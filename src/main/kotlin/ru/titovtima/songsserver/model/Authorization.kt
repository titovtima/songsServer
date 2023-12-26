package ru.titovtima.songsserver.model

import ru.titovtima.songsserver.Encoder
import ru.titovtima.songsserver.OldEncoder
import ru.titovtima.songsserver.dbConnection

class Authorization {
    companion object {
        fun register(userLogin: UserLogin): Boolean {
            if (!checkUsernameFree(userLogin.username))
                return false
            val encodedPassword = Encoder.encodeString(userLogin.password).toString()
            val query = dbConnection.prepareStatement("insert into users(username, password) values (?, ?);")
            query.setString(1, userLogin.username)
            query.setString(2, encodedPassword)
            query.execute()
            return true
        }

        private fun checkUsernameFree(username: String): Boolean {
            val query = dbConnection.prepareStatement("select id from users where username = ?;")
            query.setString(1, username)
            val result = query.executeQuery()
            return !result.next()
        }

        fun checkCredentials(userLogin: UserLogin): Boolean {
            val encodedPassword = Encoder.encodeString(userLogin.password).toString()
            val query = dbConnection.prepareStatement("select password from users where username = ?;")
            query.setString(1, userLogin.username)
            val result = query.executeQuery()
            if (!result.next()) return false
            if (result.getString("password") == encodedPassword) {
                val queryUpdate = dbConnection.prepareStatement(
                    "update users set last_login = now() where username = ?;")
                queryUpdate.setString(1, userLogin.username)
                queryUpdate.execute()
                return true
            } else {
                return checkPasswordInOldEncoding(userLogin)
            }
        }

        private fun checkPasswordInOldEncoding(userLogin: UserLogin): Boolean {
            val oldEncodedPassword = OldEncoder.encodeString(userLogin.password).toString()
            val query = dbConnection.prepareStatement(
                "select password from old_encoded_password where user_id = (select id from users where username = ?);")
            query.setString(1, userLogin.username)
            val result = query.executeQuery()
            if (!result.next()) return false
            if (result.getString("password") == oldEncodedPassword) {
                val queryUpdate = dbConnection.prepareStatement(
                    "update users set last_login = now() where username = ?;")
                queryUpdate.setString(1, userLogin.username)
                queryUpdate.execute()
                val encodedPassword = Encoder.encodeString(userLogin.password).toString()
                val queryWritePassword = dbConnection.prepareStatement(
                    "update users set password = ? where username = ?;")
                queryWritePassword.setString(1, encodedPassword)
                queryWritePassword.setString(2, userLogin.username)
                queryWritePassword.execute()
                return true
            } else {
                return false
            }
        }

        fun changePassword(user: User, newPassword: String) {
            val encodedPassword = Encoder.encodeString(newPassword).toString()
            val query = dbConnection.prepareStatement(
                "update users set password = ?, last_change_password = now() where id = ?;")
            query.setString(1, encodedPassword)
            query.setInt(2, user.id)
            query.execute()
        }
    }
}