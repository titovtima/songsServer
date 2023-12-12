package ru.titovtima.songsserver.model

import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.Database
import java.sql.ResultSet

@Serializable
data class Song (val id: Int, val name: String, val text: String? = null, val chords: String? = null,
            val chordsText: String? = null, val extra: String? = null, val key: Int? = null,
            val artistId: Int? = null, val originalName: String? = null, val link: String? = null,
            val ownerId: Int? = null, val public: Boolean = false, val inMainListL: Boolean = false) {
    companion object {
        fun readFromDb(id: Int, username: String? = null): Song? {
            if (username == null) {
                val query = Database.connection.prepareStatement("select * from song where id = ? and public = true;")
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
                query.setString(1, username)
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