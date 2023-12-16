package ru.titovtima.songsserver.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.Database
import ru.titovtima.songsserver.model.Song
import ru.titovtima.songsserver.model.SongRights
import ru.titovtima.songsserver.model.User
import ru.titovtima.songsserver.model.UserLogin
import java.util.*

fun Application.configureRouting() {
    install(IgnoreTrailingSlash)
    routing {
        post("/register") {
            val userLogin = call.receive<UserLogin>()
            val success = Database.register(userLogin)
            if (success)
                call.respond(HttpStatusCode.Created)
            else
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(1, "Username is already taken"))
        }
        post("/login") {
            val userLogin = call.receive<UserLogin>()
            if (!Database.checkCredentials(userLogin)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            val token = JWT.create()
                .withClaim("username", userLogin.username)
                .withExpiresAt(Date(System.currentTimeMillis() + 5*60*1000))
                .sign(Algorithm.HMAC256(jwtSecret))
            call.respond(mapOf("token" to token))
        }
        authenticate("auth-jwt", strategy = AuthenticationStrategy.Optional) {
            get("/song/{id}") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val user = username?.let { User.readFromDb(it) }
                val song = Song.readFromDb(songId, user)
                if (song == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, song)
                }
            }
            get("/song/{id}/rights") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val user = username?.let { User.readFromDb(it) }
                val songRights = SongRights.readFromDb(songId, user)
                if (songRights == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, songRights)
                }
            }
        }
        authenticate("auth-jwt") {
            get("/secret") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                call.respondText("Hello, $username. This token expires at ${principal.expiresAt}")
            }
        }
    }
}

@Serializable
data class ErrorResponse(val errorCode: Int, val details: String)
