package ru.titovtima.songsserver.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import ru.titovtima.songsserver.Database
import java.io.File

val jwtSecret = File("jwt_secret").inputStream().readBytes().toString(Charsets.UTF_8)

fun Application.configureSecurity() {
    val jwtRealm = "songs site"
    authentication {
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .build()
            )
            validate { credential ->
                val username = credential.payload.getClaim("username").asString()
                if (username == "") return@validate null
                val dbQuery = Database.connection.prepareStatement(
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
    }
}
