package ru.titovtima.songsserver.model

import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.dbConnection
import java.sql.ResultSet

@Serializable
data class SongsList(val id: Int, val name: String, val owner: String, val public: Boolean, val list: List<Song>) {
    companion object {
        fun readFromDb(listId: Int, user: User? = null): SongsList? {
            if (user == null) {
                val query = dbConnection.prepareStatement(
                    "select * from songs_list_with_username where public and id = ?;")
                query.setInt(1, listId)
                return songsListFromResultSet(query.executeQuery(), getSongsFromDb(listId, null))
            } else {
                val query = dbConnection.prepareStatement("select * from readable_lists(?) where id = ?;")
                query.setInt(1, user.id)
                query.setInt(2, listId)
                return songsListFromResultSet(query.executeQuery(), getSongsFromDb(listId, user))
            }
        }

        private fun songsListFromResultSet(resultSet: ResultSet, list: List<Song>): SongsList? {
            if (!resultSet.next()) return null
            val id = resultSet.getInt("id")
            val name = resultSet.getString("name")
            val owner = resultSet.getString("owner")
            val public = resultSet.getBoolean("public")
            return SongsList(id, name, owner, public, list)
        }

        private fun getSongsFromDb(listId: Int, user: User?): List<Song> {
            if (user == null) {
                val query = dbConnection.prepareStatement(
                    "select s.id, s.name, s.extra, s.key, s.owner, s.public, s.in_main_list, s.created_at, s.updated_at " +
                            "from public_songs() s left join song_in_list sl on sl.song_id = s.id where sl.list_id = ?;")
                query.setInt(1, listId)
                return Song.allSongsFromResultSet(query.executeQuery())
            } else {
                val query = dbConnection.prepareStatement(
                    "select s.id, s.name, s.extra, s.key, s.owner, s.public, s.in_main_list, s.created_at, s.updated_at " +
                            "from readable_songs(?) s left join song_in_list sl on sl.song_id = s.id where sl.list_id = ?;")
                query.setInt(1, user.id)
                query.setInt(2, listId)
                return Song.allSongsFromResultSet(query.executeQuery())
            }
        }
    }
}
