package ru.titovtima.songsserver.model

import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.Database

@Serializable
data class UserLogin(val username: String, val password: String)

class User(val id: Int, val username: String, val isAdmin: Boolean, val approved: Boolean) {
    companion object {
        fun readFromDb(id: Int): User? {
            val query = Database.connection.prepareStatement(
                "select username, is_admin, approved from users where id = ?;")
            query.setInt(1, id)
            val result = query.executeQuery()
            if (!result.next())
                return null
            val username = result.getString("username")
            val isAdmin = result.getBoolean("is_admin")
            val approved = result.getBoolean("approved")
            return User(id, username, isAdmin, approved)
        }

        fun readFromDb(username: String): User? {
            val query = Database.connection.prepareStatement(
                "select id, is_admin, approved from users where username = ?;")
            query.setString(1, username)
            val result = query.executeQuery()
            if (!result.next())
                return null
            val id = result.getInt("id")
            val isAdmin = result.getBoolean("is_admin")
            val approved = result.getBoolean("approved")
            return User(id, username, isAdmin, approved)
        }
    }
}
