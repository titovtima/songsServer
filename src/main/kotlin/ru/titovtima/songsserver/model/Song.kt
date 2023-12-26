package ru.titovtima.songsserver.model

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.dbConnection
import java.sql.ResultSet

@Serializable
data class Song (val id: Int, val name: String, val extra: String? = null, val key: Int? = null,
                 val ownerId: Int? = null, val public: Boolean = false, val inMainList: Boolean = false,
                 val parts: List<SongPart>, val performances: List<SongPerformance>, val audios: List<String>) {
    companion object {
        fun readFromDb(id: Int, user: User?): Song? {
            if (user == null) {
                val query = dbConnection.prepareStatement(
                    "select * from song s " +
                            "left join song_in_list sl on s.id = sl.song_id " +
                            "left join songs_list l on sl.list_id = l.id " +
                            "where s.id = ? and (s.public or l.public);")
                query.setInt(1, id)
                return songFromResultSet(id, query.executeQuery())
            } else if (user.isAdmin) {
                val query = dbConnection.prepareStatement("select * from song where id = ?;")
                query.setInt(1, id)
                return songFromResultSet(id, query.executeQuery())
            } else {
                val query = dbConnection.prepareStatement(
                    "select * from song s " +
                            "left join song_reader sr on s.id = sr.song_id " +
                            "left join song_writer sw on s.id = sw.song_id " +
                            "left join song_in_list sl on s.id = sl.song_id " +
                            "left join songs_list l on sl.list_id = l.id " +
                            "left join list_reader lr on sl.list_id = lr.list_id " +
                            "left join list_writer lw on sl.list_id = lw.list_id " +
                            "left join group_song_reader gsr on s.id = gsr.song_id " +
                            "left join group_song_writer gsw on s.id = gsw.song_id " +
                            "left join group_list_reader glr on sl.list_id = glr.list_id " +
                            "left join group_list_writer glw on sl.list_id = glw.list_id " +
                            "left join users_group g " +
                            "    on gsr.group_id = g.id or gsw.group_id = g.id " +
                            "           or glr.group_id = g.id or glw.group_id = g.id " +
                            "left join user_in_group ug " +
                            "    on gsr.group_id = ug.group_id or gsw.group_id = ug.group_id " +
                            "           or glr.group_id = ug.group_id or glw.group_id = ug.group_id " +
                            "left join group_admin ga " +
                            "    on gsr.group_id = ga.group_id or gsw.group_id = ga.group_id " +
                            "           or glr.group_id = ga.group_id or glw.group_id = ga.group_id " +
                            "where s.id = ? and (s.owner_id = ? or l.owner_id = ? or s.public or l.public " +
                            "   or sr.user_id = ? or sw.user_id = ? or lr.user_id = ? or lw.user_id = ? " +
                            "   or g.owner_id = ? or ug.user_id = ? or ga.user_id = ?);")
                query.setInt(1, id)
                query.setInt(2, user.id)
                query.setInt(3, user.id)
                query.setInt(4, user.id)
                query.setInt(5, user.id)
                query.setInt(6, user.id)
                query.setInt(7, user.id)
                query.setInt(8, user.id)
                query.setInt(9, user.id)
                query.setInt(10, user.id)
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
                val songAudios = getAllSongAudio(id)
                return Song(id, name, extra, key, ownerId, public, inMainList, songParts, songPerformances, songAudios)
            } else {
                return null
            }
        }

        private fun getAllSongAudio(songId: Int): List<String> {
            val query = dbConnection.prepareStatement("select uuid from song_audio where song_id = ?;")
            query.setInt(1, songId)
            val resultSet = query.executeQuery()
            val result = arrayListOf<String>()
            while (resultSet.next()) {
                val uuid = resultSet.getString("uuid")
                result.add(uuid)
            }
            return result
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
            val query = dbConnection.prepareStatement("select * from song_part where song_id = ?;")
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
            val query = dbConnection.prepareStatement("select name from artist where id = ?;")
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
            val query = dbConnection.prepareStatement("select * from song_performance where song_id = ?;")
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
            val queryReaders = dbConnection.prepareStatement(
                "select username from users u right join song_reader r on u.id = r.user_id where r.song_id = ?;")
            queryReaders.setInt(1, songId)
            val resultReaders = queryReaders.executeQuery()
            while (resultReaders.next()) {
                val reader = resultReaders.getString("username")
                readers.add(reader)
            }
            val writers = arrayListOf<String>()
            val queryWriters = dbConnection.prepareStatement(
                "select username from users u right join song_writer w on u.id = w.user_id where w.song_id = ?;")
            queryWriters.setInt(1, songId)
            val resultWriters = queryWriters.executeQuery()
            while (resultWriters.next()) {
                val writer = resultWriters.getString("username")
                writers.add(writer)
            }
            val querySong = dbConnection.prepareStatement(
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

suspend fun loadAudioFromS3(uuid: String): ByteArray? {
    val getRequest = GetObjectRequest {
        bucket = "songssite"
        key = uuid
    }

    var resultStream: ByteArray? = null

    S3Client.fromEnvironment {
        endpointUrl = Url.parse("https://storage.yandexcloud.net/")
        region = "ru-central1"
    }.use { s3 ->
        s3.getObject(getRequest) { response ->
            resultStream = response.body?.toByteArray()
        }
    }

    return resultStream
}