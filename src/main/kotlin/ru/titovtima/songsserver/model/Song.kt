package ru.titovtima.songsserver.model

import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.Database
import java.sql.ResultSet

@Serializable
data class Song (val id: Int, val name: String, val extra: String? = null, val key: Int? = null,
                 val ownerId: Int? = null, val public: Boolean = false, val inMainList: Boolean = false,
                 val parts: List<SongPart>, val performances: List<SongPerformance>) {
    companion object {
        fun readFromDb(id: Int, user: User?): Song? {
            if (user == null) {
                val query = Database.connection.prepareStatement("select * from song where id = ? and public = true;")
                query.setInt(1, id)
                return songFromResultSet(id, query.executeQuery())
            } else if (user.isAdmin) {
                val query = Database.connection.prepareStatement("select * from song where id = ?;")
                query.setInt(1, id)
                return songFromResultSet(id, query.executeQuery())
            } else {
                val query = Database.connection.prepareStatement(
                    "select * from song s " +
                            "left join song_reader r on s.id = r.song_id " +
                            "left join song_writer w on s.id = w.song_id " +
                            "where s.id = ? and (s.public or s.owner_id = ? or r.user_id = ? or w.user_id = ?);")
                query.setInt(1, id)
                query.setInt(2, user.id)
                query.setInt(3, user.id)
                query.setInt(4, user.id)
                return songFromResultSet(id, query.executeQuery())
            }
        }

        private fun songFromResultSet(id: Int, resultSet: ResultSet): Song? {
            if (resultSet.next()) {
                val name = resultSet.getString("name")
                val extra = resultSet.getString("extra")
                var key: Int? = resultSet.getInt("key")
                if (resultSet.wasNull()) key = null
                val ownerId = resultSet.getInt("owner_id")
                val public = resultSet.getBoolean("public")
                val inMainList = resultSet.getBoolean("in_main_list")
                val songParts = SongPart.getAllSongParts(id)
                val songPerformances = SongPerformance.getAllSongPerformances(id)
                return Song(id, name, extra, key, ownerId, public, inMainList, songParts, songPerformances)
            } else {
                return null
            }
        }
    }
}

enum class SongPartType(int: Int) { Text(1), Chords(2), ChordsText(3) }

fun songPartTypeFromInt(int: Int) = when (int) {
    1 -> SongPartType.Text
    2 -> SongPartType.Chords
    3 -> SongPartType.ChordsText
    else -> SongPartType.Text
}

@Serializable
data class SongPart(val type: SongPartType, val ord: Int, val name: String?, val data: String, val key: Int?) {
    companion object {
        fun getAllSongParts(songId: Int): List<SongPart> {
            val query = Database.connection.prepareStatement("select * from song_part where song_id = ?;")
            query.setInt(1, songId)
            val resultSet = query.executeQuery()
            val result = arrayListOf<SongPart>()
            while (resultSet.next()) {
                val type = resultSet.getInt("type")
                val ord = resultSet.getInt("ord")
                val name = resultSet.getString("name")
                val data = resultSet.getString("data")
                var key: Int? = resultSet.getInt("key")
                if (resultSet.wasNull()) key = null
                result.add(SongPart(songPartTypeFromInt(type), ord, name, data, key))
            }
            return result
        }
    }
}

@Serializable
data class Artist(val id: Int, val name: String) {
    companion object {
        fun readFromDb(id: Int): Artist? {
            val query = Database.connection.prepareStatement("select name from artist where id = ?;")
            query.setInt(1, id)
            val resultSet = query.executeQuery()
            if (resultSet.next()) {
                val name = resultSet.getString("name")
                return Artist(id, name)
            } else
                return null
        }
    }
}

@Serializable
data class SongPerformance(val artist: Artist?, val songName: String?, val link: String?,
                           val isOriginal: Boolean = false, val isMain: Boolean) {
    companion object {
        fun getAllSongPerformances(songId: Int): List<SongPerformance> {
            val query = Database.connection.prepareStatement("select * from song_performance where song_id = ?;")
            query.setInt(1, songId)
            val resultSet = query.executeQuery()
            val result = arrayListOf<SongPerformance>()
            while (resultSet.next()) {
                var artistId: Int? = resultSet.getInt("artist_id")
                if (resultSet.wasNull()) artistId = null
                val songName = resultSet.getString("song_name")
                val link = resultSet.getString("link")
                val isOriginal = resultSet.getBoolean("is_original")
                val isMain = resultSet.getBoolean("is_main")
                result.add(SongPerformance(artistId?.let { Artist.readFromDb(it) }, songName, link, isOriginal, isMain))
            }
            return result
        }
    }
}

@Serializable
data class SongRights(val songId: Int, val readers: List<String>, val writers: List<String>, val owner: String) {
    companion object {
        fun readFromDb(songId: Int, user: User?): SongRights? {
            if (user == null) return null
            val readers = arrayListOf<String>()
            val queryReaders = Database.connection.prepareStatement(
                "select username from users u right join song_reader r on u.id = r.user_id where r.song_id = ?;")
            queryReaders.setInt(1, songId)
            val resultReaders = queryReaders.executeQuery()
            while (resultReaders.next()) {
                val reader = resultReaders.getString("username")
                readers.add(reader)
            }
            val writers = arrayListOf<String>()
            val queryWriters = Database.connection.prepareStatement(
                "select username from users u right join song_writer w on u.id = w.user_id where w.song_id = ?;")
            queryWriters.setInt(1, songId)
            val resultWriters = queryWriters.executeQuery()
            while (resultWriters.next()) {
                val writer = resultWriters.getString("username")
                writers.add(writer)
            }
            val querySong = Database.connection.prepareStatement(
                "select username from song s left join users u on s.owner_id = u.id where s.id = ?;")
            querySong.setInt(1, songId)
            val resultSong = querySong.executeQuery()
            if (!resultSong.next()) {
                return null
            }
            val owner = resultSong.getString("username")
            return if (owner == user.username || readers.contains(user.username) || user.isAdmin)
                SongRights(songId, readers, writers, owner)
            else
                null
        }
    }
}