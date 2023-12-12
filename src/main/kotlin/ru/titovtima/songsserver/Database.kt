package ru.titovtima.songsserver

import ru.titovtima.songsserver.model.User
import java.sql.Connection
import java.sql.DriverManager

class Database {
    companion object {
        val connection: Connection = DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/songsserver", "songsserver", "my_password")

        fun register(user: User): Boolean {
            if (!checkUsernameFree(user.username))
                return false
            val encodedPassword = RSAEncoder.encodeString(user.password).toString()
            val query = connection.prepareStatement("insert into users(username, password) values (?, ?);")
            query.setString(1, user.username)
            query.setString(2, encodedPassword)
            query.execute()
            return true
        }

        private fun checkUsernameFree(username: String): Boolean {
            val query = connection.prepareStatement("select id from users where username = ?;")
            query.setString(1, username)
            val result = query.executeQuery()
            return !result.next()
        }

        fun checkCredentials(user: User): Boolean {
            val encodedPassword = RSAEncoder.encodeString(user.password).toString()
            val query = connection.prepareStatement("select password from users where username = ?;")
            query.setString(1, user.username)
            val result = query.executeQuery()
            if (!result.next()) return false
            if (result.getString("password") == encodedPassword) {
                val queryUpdate = connection.prepareStatement("update users set last_login = now() where username = ?;")
                queryUpdate.setString(1, user.username)
                queryUpdate.execute()
                return true
            } else {
                return false
            }
        }
    }
}