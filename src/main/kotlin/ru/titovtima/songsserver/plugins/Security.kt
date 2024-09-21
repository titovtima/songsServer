package ru.titovtima.songsserver.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import ru.titovtima.songsserver.Encoder
import ru.titovtima.songsserver.dbConnection

val jwtSecret: String = System.getenv("JWT_SECRET")

fun Application.configureSecurity() {
    val authRealm = "songs site"
    authentication {
        jwt("auth-jwt") {
            realm = authRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .build()
            )
            validate { credential ->
                val username = credential.payload.getClaim("username").asString()
                if (username == "") return@validate null
                val dbQuery = dbConnection.prepareStatement(
                    "select last_change_password from users where username = ?;")
                dbQuery.setString(1, username)
                val resultSet = dbQuery.executeQuery()
                if (!resultSet.next()) return@validate null
                val lastChangePassword = resultSet.getTimestamp("last_change_password").time
                if (credential.payload.getClaim("created_at").asLong() > lastChangePassword) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
        bearer("auth-bearer") {
            realm = authRealm
            authenticate { tokenCredential ->
                Token.getUserIdByToken(tokenCredential.token)?.let { UserIdIntPrincipal(it) }
            }
        }
    }
}

data class UserIdIntPrincipal(val id: Int): Principal

class Token {
    companion object {
        fun createToken(userId: Int): String? {
            for (i in 1..100) {
                val token = generateTokenString()
                if (writeTokenToDb(userId, token)) {
                    return token
                }
            }
            return null
        }

        private fun generateTokenString(): String {
            val symbols = "abcdefghijklmnopqrstuvwxyz1234567890"
            val length = 64
            var token = ""
            for (i in 1..length) {
                token += symbols.random()
            }
            return token
        }

        private fun writeTokenToDb(userId: Int, token: String): Boolean {
            val encodedToken = Encoder.encodeString(token).toString()
            val query = dbConnection.prepareStatement("insert into auth_tokens(user_id, token) values (?, ?);")
            query.setInt(1, userId)
            query.setString(2, encodedToken)
            return query.executeUpdate() == 1
        }

        fun getUserIdByToken(token: String): Int? {
            val encodedToken = Encoder.encodeString(token).toString()
            val query = dbConnection.prepareStatement("select user_id from auth_tokens where token = ?;")
            query.setString(1, encodedToken)
            val resultSet = query.executeQuery()
            if (resultSet.next()) {
                val id = resultSet.getInt("user_id")
                val updateQuery = dbConnection.prepareStatement("update auth_tokens set used_at = now() where token = ?;")
                updateQuery.setString(1, encodedToken)
                updateQuery.executeUpdate()
                return id
            } else {
                return null
            }
        }
    }
}
