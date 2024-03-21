package ru.titovtima.songsserver.model

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.dbConnection
import java.sql.ResultSet
import java.sql.Types
import java.util.Collections
import java.util.UUID

@Serializable
data class Song (val id: Int, val name: String, val extra: String? = null, val key: Int? = null,
                 val ownerId: Int, val public: Boolean = false, val inMainList: Boolean = false,
                 val parts: List<SongPart>, val performances: List<SongPerformance>, val audios: List<String>) {
    companion object {
        fun readFromDb(id: Int, user: User?): Song? {
            if (user == null) {
                val query = dbConnection.prepareStatement("select * from public_songs() where id = ?;")
                query.setInt(1, id)
                return songFromResultSet(query.executeQuery())
            } else {
                val query = dbConnection.prepareStatement("select * from readable_songs(?) where id = ?;")
                query.setInt(1, user.id)
                query.setInt(2, id)
                return songFromResultSet(query.executeQuery())
            }
        }

        fun readAllFromDb(user: User?): List<Song> {
            if (user == null) {
                val query = dbConnection.prepareStatement("select * from public_songs();")
                return allSongsFromResultSet(query.executeQuery())
            } else {
                val query = dbConnection.prepareStatement("select * from readable_songs(?);")
                query.setInt(1, user.id)
                return allSongsFromResultSet(query.executeQuery())
            }
        }

        fun readMainListFromDb(user: User?): List<Song> {
            if (user == null) {
                val query = dbConnection.prepareStatement("select * from public_songs() where in_main_list = true;")
                return allSongsFromResultSet(query.executeQuery())
            } else {
                val query = dbConnection.prepareStatement("select * from readable_songs(?) where in_main_list = true;")
                query.setInt(1, user.id)
                return allSongsFromResultSet(query.executeQuery())
            }
        }

        private fun songFromResultSet(resultSet: ResultSet): Song? {
            if (resultSet.next()) {
                val id = resultSet.getInt("id")
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

        private fun allSongsFromResultSet(resultSet: ResultSet): List<Song> {
            val result = mutableListOf<Song>()
            var song = songFromResultSet(resultSet)
            while (song != null) {
                result.add(song)
                song = songFromResultSet(resultSet)
            }
            return result
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

        fun checkWriteAccess(songId: Int, user: User): Boolean {
            val queryCheckRights = dbConnection.prepareStatement("select id from writable_songs(?) where id = ?;")
            queryCheckRights.setInt(1, user.id)
            queryCheckRights.setInt(2, songId)
            val resultSet = queryCheckRights.executeQuery()
            return resultSet.next()
        }
    }

    private fun checkWriteAccess(user: User): Boolean = checkWriteAccess(id, user)

    fun saveToDb(user: User, new: Boolean): Boolean {
        if (!new && !checkWriteAccess(user)) return false
        dbConnection.autoCommit = false
        try {
            if (new) {
                val queryInsert = dbConnection.prepareStatement(
                    "insert into song (id, name, extra, key, owner_id, public, in_main_list, created_at, updated_at) " +
                        "values (?, ?, ?, ?, ?, ?, ?, now(), now());")
                queryInsert.setInt(1, id)
                queryInsert.setString(2, name)
                if (extra == null) queryInsert.setNull(3, Types.VARCHAR)
                else queryInsert.setString(3, extra)
                if (key == null) queryInsert.setNull(4, Types.INTEGER)
                else queryInsert.setInt(4, key)
                queryInsert.setInt(5, ownerId)
                queryInsert.setBoolean(6, public)
                queryInsert.setBoolean(7, inMainList)
                queryInsert.executeUpdate()
            } else {
                val queryUpdate = dbConnection.prepareStatement(
                    "update song set name = ?, extra = ?, key = ?, public = ?, owner_id = ?, " +
                            "updated_at = now() where id = ?;")
                queryUpdate.setString(1, name)
                if (extra == null) queryUpdate.setNull(2, Types.VARCHAR)
                else queryUpdate.setString(2, extra)
                if (key == null) queryUpdate.setNull(3, Types.INTEGER)
                else queryUpdate.setInt(3, key)
                queryUpdate.setBoolean(4, public)
                queryUpdate.setInt(5, ownerId)
                queryUpdate.setInt(6, id)
                queryUpdate.executeUpdate()
            }

            val queryDeleteSongParts = dbConnection.prepareStatement("delete from song_part where song_id = ?;")
            queryDeleteSongParts.setInt(1, id)
            queryDeleteSongParts.executeUpdate()
            for (part in parts) part.saveToDb(id)

            val queryDeleteSongPerformances =
                dbConnection.prepareStatement("delete from song_performance where song_id = ?;")
            queryDeleteSongPerformances.setInt(1, id)
            queryDeleteSongPerformances.executeUpdate()
            for (performance in performances) performance.saveToDb(id)

            val queryDeleteAudios = dbConnection.prepareStatement(
                "update song_audio set song_id = null where song_id = ?;")
            queryDeleteAudios.setInt(1, id)
            queryDeleteAudios.executeUpdate()
            if (audios.isNotEmpty()) {
                val questions = Collections.nCopies(audios.size, "?").joinToString(",")
                val query = dbConnection.prepareStatement(
                    "update song_audio set song_id = ? where uuid in ($questions);")
                query.setInt(1, id)
                for (i in audios.indices) query.setString(i + 2, audios[i])
                if (query.executeUpdate() != audios.size) {
                    dbConnection.rollback()
                    dbConnection.autoCommit = true
                    return false
                }
            }
        } catch (e: Exception) {
            println(e)
            dbConnection.rollback()
            dbConnection.autoCommit = true
            return false
        }
        dbConnection.commit()
        dbConnection.autoCommit = true
        return true
    }
}

@Serializable
data class NewSongData (val name: String, val extra: String? = null, val key: Int? = null,
                        val public: Boolean = false, val inMainList: Boolean = false,
                        val parts: List<SongPart>, val performances: List<SongPerformance>, val audios: List<String>) {

    fun makeSong(user: User): Song? {
        val id = getId() ?: return null
        return Song(id, name, extra, key, user.id, public, inMainList, parts, performances, audios)
    }

    private fun getId(): Int? {
        dbConnection.autoCommit = false
        try {
            val query = dbConnection.prepareStatement("select min_key from keys where name = 'song';")
            val resultSet = query.executeQuery()
            if (!resultSet.next()) throw Exception("No min key in database")
            val newId = resultSet.getInt("min_key")
            val queryUpdate = dbConnection.prepareStatement(
                "update keys set min_key = min_key + 1 where name = 'song';")
            queryUpdate.executeUpdate()
            dbConnection.commit()
            dbConnection.autoCommit = true
            return newId
        } catch (e: Exception) {
            println(e)
            dbConnection.rollback()
            dbConnection.autoCommit = true
            return null
        }
    }
}

enum class SongPartType(val int: Int) { Text(1), Chords(2), ChordsText(3) }

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

    fun saveToDb(songId: Int) {
        val query = dbConnection.prepareStatement(
            "insert into song_part (song_id, type, ord, name, data, key) values (?, ?, ?, ?, ?, ?);")
        query.setInt(1, songId)
        query.setInt(2, type.int)
        query.setInt(3, ord)
        if (name == null) query.setNull(4, Types.VARCHAR)
        else query.setString(4, name)
        query.setString(5, data)
        if (key == null) query.setNull(6, Types.INTEGER)
        else query.setInt(6, key)
        query.executeUpdate()
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

    fun saveToDb(songId: Int) {
        val query = dbConnection.prepareStatement(
            "insert into song_performance (song_id, artist_id, song_name, link, is_original, is_main) " +
                    "values (?, ?, ?, ?, ?);")
        query.setInt(1, songId)
        if (artist == null) query.setNull(2, Types.INTEGER)
        else query.setInt(2, artist.id)
        if (songName == null) query.setNull(3, Types.VARCHAR)
        else query.setString(3, songName)
        if (link == null) query.setNull(4, Types.VARCHAR)
        else query.setString(4, link)
        query.setBoolean(5, isOriginal)
        query.setBoolean(6, isMain)
        query.executeUpdate()
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

class SongAudio {
    companion object {
        private const val S3_BUCKET = "songsserver"
        private val endpointUrl = Url.parse("https://hb.vkcs.cloud/")
        private const val region = "ru-msk"

        suspend fun loadAudioFromS3(uuid: String): ByteArray? {
            val getRequest = GetObjectRequest {
                bucket = S3_BUCKET
                key = uuid
            }

            var resultStream: ByteArray? = null

            try {
                S3Client.fromEnvironment {
                    endpointUrl = SongAudio.endpointUrl
                    region = SongAudio.region
                }.use { s3 ->
                    s3.getObject(getRequest) { response ->
                        resultStream = response.body?.toByteArray()
                    }
                }
            } catch (_: Exception) {
            }

            return resultStream
        }

        suspend fun uploadAudioToS3(byteArray: ByteArray): String {
            var uuid = UUID.randomUUID().toString()
            while (!checkAudioUuidFree(uuid))
                uuid = UUID.randomUUID().toString()
            val putRequest = PutObjectRequest {
                bucket = S3_BUCKET
                key = uuid
                body = ByteStream.fromBytes(byteArray)
            }

            S3Client.fromEnvironment {
                endpointUrl = SongAudio.endpointUrl
                region = SongAudio.region
            }.use { s3 ->
                println(s3.putObject(putRequest).eTag)
            }

            return uuid
        }

        private fun checkAudioUuidFree(uuid: String): Boolean {
            val query = dbConnection.prepareStatement("select * from song_audio where uuid = ?;")
            query.setString(1, uuid)
            return !query.executeQuery().next()
        }
    }
}
