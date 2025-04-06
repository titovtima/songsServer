package ru.titovtima.songsserver.model

import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.dbConnection

@Serializable
class User(val id: Int, val username: String, val email: String?, val isAdmin: Boolean, val approved: Boolean) {
    companion object {
        fun readFromDb(id: Int): User? {
            val query = dbConnection.prepareStatement(
                "select username, email, is_admin, approved from users where id = ?;")
            query.setInt(1, id)
            val result = query.executeQuery()
            if (!result.next())
                return null
            val username = result.getString("username")
            val email = result.getString("email")
            val isAdmin = result.getBoolean("is_admin")
            val approved = result.getBoolean("approved")
            return User(id, username, email, isAdmin, approved)
        }

        fun readFromDb(username: String): User? {
            val query = dbConnection.prepareStatement(
                "select id, email, is_admin, approved from users where username = ?;")
            query.setString(1, username)
            val result = query.executeQuery()
            if (!result.next())
                return null
            val id = result.getInt("id")
            val email = result.getString("email")
            val isAdmin = result.getBoolean("is_admin")
            val approved = result.getBoolean("approved")
            return User(id, username, email, isAdmin, approved)
        }

        fun getByEmail(email: String): List<User> {
            val query = dbConnection.prepareStatement(
                "select id, username, is_admin, approved from users where email = ?;")
            query.setString(1, email)
            val result = query.executeQuery()
            val list = arrayListOf<User>()
            while (result.next()) {
                val id = result.getInt("id")
                val username = result.getString("username")
                val isAdmin = result.getBoolean("is_admin")
                val approved = result.getBoolean("approved")
                list.add(User(id, username, email, isAdmin, approved))
            }
            return list
        }
    }

    fun changeEmail(email: String) {
        val query = dbConnection.prepareStatement("update users set email = ? where id = ?;")
        query.setString(1, email)
        query.setInt(2, id)
        query.executeUpdate()
    }
}
