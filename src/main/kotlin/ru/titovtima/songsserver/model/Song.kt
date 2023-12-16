package ru.titovtima.songsserver.model

import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.Database
import java.sql.ResultSet

@Serializable
data class Song (val id: Int, val name: String, val text: String? = null, val chords: String? = null,
            val chordsText: String? = null, val extra: String? = null, val key: Int? = null,
            val artistId: Int? = null, val originalName: String? = null, val link: String? = null,
            val ownerId: Int? = null, val public: Boolean = false, val inMainList: Boolean = false) {
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
                    "with sub as (select id as id from users where username = ?) " +
                            "select * from song s " +
                            "left join song_reader r on s.id = r.song_id " +
                            "left join song_writer w on s.id = w.song_id " +
                            "where s.id = ? and (s.public or s.owner_id = (select id from sub) " +
                            "             or r.user_id = (select id from sub) or w.user_id = (select id from sub));")
                query.setString(1, user.username)
                query.setInt(2, id)
                return songFromResultSet(id, query.executeQuery())
            }
        }

        private fun songFromResultSet(id: Int, resultSet: ResultSet): Song? {
            if (resultSet.next()) {
                val name = resultSet.getString("name")
                val text = resultSet.getString("text")
                val chords = resultSet.getString("chords")
                val chordsText = resultSet.getString("chords_text")
                val extra = resultSet.getString("extra")
                val key = resultSet.getInt("key")
                val artistId = resultSet.getInt("artist_id")
                val originalName = resultSet.getString("original_name")
                val link = resultSet.getString("link")
                val ownerId = resultSet.getInt("owner_id")
                val public = resultSet.getBoolean("public")
                val inMainList = resultSet.getBoolean("in_main_list")
                return Song(id, name, text, chords, chordsText, extra, key, artistId, originalName, link,
                    ownerId, public, inMainList)
            } else {
                return null
            }
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