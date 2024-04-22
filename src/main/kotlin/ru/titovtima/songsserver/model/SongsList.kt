package ru.titovtima.songsserver.model

import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.dbConnection
import java.sql.ResultSet
import java.util.*

@Serializable
data class SongsList(val id: Int, val name: String, val owner: String, val public: Boolean, val list: List<Song>) {

    constructor(listInfo: SongsListInfo, list: List<Song>) :
            this(listInfo.id, listInfo.name, listInfo.owner, listInfo.public, list)

    companion object {
        fun readFromDb(listId: Int, user: User? = null): SongsList? {
            val listInfo = SongsListInfo.readFromDb(listId, user) ?: return null
            val list = getSongsFromDb(listId, user)
            return SongsList(listInfo, list)
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

@Serializable
data class SongsListInfo(val id: Int, val name: String, val owner: String, val public: Boolean) {
    companion object {
        fun readFromDb(listId: Int, user: User? = null): SongsListInfo? {
            if (user == null) {
                val query = dbConnection.prepareStatement(
                    "select * from songs_list_with_username where public and id = ?;"
                )
                query.setInt(1, listId)
                return songsListInfoFromResultSet(query.executeQuery())
            } else {
                val query = dbConnection.prepareStatement("select * from readable_lists(?) where id = ?;")
                query.setInt(1, user.id)
                query.setInt(2, listId)
                return songsListInfoFromResultSet(query.executeQuery())
            }
        }

        fun readAllFromDb(user: User?): List<SongsListInfo> {
            if (user == null) {
                val query = dbConnection.prepareStatement(
                    "select * from songs_list_with_username where public;"
                )
                return allSongsListsInfoFromResultSet(query.executeQuery())
            } else {
                val query = dbConnection.prepareStatement("select * from readable_lists(?);")
                query.setInt(1, user.id)
                return allSongsListsInfoFromResultSet(query.executeQuery())
            }
        }

        private fun songsListInfoFromResultSet(resultSet: ResultSet): SongsListInfo? {
            if (!resultSet.next()) return null
            val id = resultSet.getInt("id")
            val name = resultSet.getString("name")
            val owner = resultSet.getString("owner")
            val public = resultSet.getBoolean("public")
            return SongsListInfo(id, name, owner, public)
        }

        private fun allSongsListsInfoFromResultSet(resultSet: ResultSet): List<SongsListInfo> {
            val result = mutableListOf<SongsListInfo>()
            var listInfo = songsListInfoFromResultSet(resultSet)
            while (listInfo != null) {
                result.add(listInfo)
                listInfo = songsListInfoFromResultSet(resultSet)
            }
            return result
        }
    }
}

@Serializable
data class SongsListSongsInfo(val id: Int, val name: String, val owner: String, val public: Boolean,
                              val list: List<SongInfo>) {
    constructor(listInfo: SongsListInfo, list: List<SongInfo>) :
            this(listInfo.id, listInfo.name, listInfo.owner, listInfo.public, list)

    companion object {
        fun readFromDb(listId: Int, user: User? = null): SongsListSongsInfo? {
            val listInfo = SongsListInfo.readFromDb(listId, user) ?: return null
            val list = getSongsFromDb(listId, user)
            return SongsListSongsInfo(listInfo, list)
        }

        private fun getSongsFromDb(listId: Int, user: User?): List<SongInfo> {
            if (user == null) {
                val query = dbConnection.prepareStatement(
                    "select s.id, s.name, s.extra, s.key, s.owner, s.public, s.in_main_list, s.created_at, s.updated_at " +
                            "from public_songs() s left join song_in_list sl on sl.song_id = s.id where sl.list_id = ?;")
                query.setInt(1, listId)
                return SongInfo.allSongsInfoFromResultSet(query.executeQuery())
            } else {
                val query = dbConnection.prepareStatement(
                    "select s.id, s.name, s.extra, s.key, s.owner, s.public, s.in_main_list, s.created_at, s.updated_at " +
                            "from readable_songs(?) s left join song_in_list sl on sl.song_id = s.id where sl.list_id = ?;")
                query.setInt(1, user.id)
                query.setInt(2, listId)
                return SongInfo.allSongsInfoFromResultSet(query.executeQuery())
            }
        }
    }
}

@Serializable
data class PostSongsList(val id: Int, val name: String, val owner: String, val public: Boolean, val list: List<Int>) {
    companion object {
        fun checkWriteAccess(listId: Int, user: User): Boolean {
            val query = dbConnection.prepareStatement("select id from writable_lists(?) where id = ?;")
            query.setInt(1, user.id)
            query.setInt(2, listId)
            val resultSet = query.executeQuery()
            return resultSet.next()
        }
    }

    fun checkWriteAccess(user: User) = checkWriteAccess(id, user)

    fun saveToDb(user: User, new: Boolean): Boolean {
        if (!new && !checkWriteAccess(user)) return false
        val userOwner = User.readFromDb(owner) ?: return false
        dbConnection.autoCommit = false
        try {
            if (new) {
                val query = dbConnection.prepareStatement("insert into songs_list (id, name, public, owner_id) " +
                        "values (?, ?, ?, ?);")
                query.setInt(1, id)
                query.setString(2, name)
                query.setBoolean(3, public)
                query.setInt(4, userOwner.id)
                query.executeUpdate()
            } else {
                val query = dbConnection.prepareStatement("update songs_list set name = ?, public = ?, owner_id = ? " +
                        "where id = ?;")
                query.setString(1, name)
                query.setBoolean(2, public)
                query.setInt(3, userOwner.id)
                query.setInt(4, id)
                query.executeUpdate()
            }

            if (list.isEmpty()) {
                val queryDelete = dbConnection.prepareStatement(
                    "delete from song_in_list where list_id = ?;")
                queryDelete.setInt(1, id)
                queryDelete.executeUpdate()
            } else {

                val questions = Collections.nCopies(list.size, "?").joinToString(",")
                val queryDelete = dbConnection.prepareStatement(
                    "delete from song_in_list where list_id = ? and song_id not in ($questions);")
                queryDelete.setInt(1, id)
                for (i in list.indices)
                    queryDelete.setInt(i + 2, list[i])
                queryDelete.executeUpdate()

                val questions2 = Collections.nCopies(list.size, "(?,?)").joinToString(",")
                val queryInsert = dbConnection.prepareStatement(
                    "insert into song_in_list (song_id, list_id) values $questions2 on conflict do nothing;")
                for (i in list.indices) {
                    queryInsert.setInt(i*2 + 1, list[i])
                    queryInsert.setInt(i*2 + 2, id)
                }
                queryInsert.executeUpdate()
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
data class NewSongsList(val name: String, val public: Boolean, val list: List<Int>) {
    fun makeList(user: User): PostSongsList? {
        val id = getId() ?: return null
        return PostSongsList(id, name, user.username, public, list)
    }

    private fun getId(): Int? {
        dbConnection.autoCommit = false
        try {
            val query = dbConnection.prepareStatement("select min_key from keys where name = 'songs_list';")
            val resultSet = query.executeQuery()
            if (!resultSet.next()) throw Exception("No min key in database")
            val newId = resultSet.getInt("min_key")
            val queryUpdate = dbConnection.prepareStatement(
                "update keys set min_key = min_key + 1 where name = 'songs_list';")
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
