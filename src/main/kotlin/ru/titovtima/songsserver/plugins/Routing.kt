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
import ru.titovtima.songsserver.User
import java.util.*

fun Application.configureRouting() {
    install(IgnoreTrailingSlash)
    routing {
        post("/register") {
            val user = call.receive<User>()
            val success = Database.register(user)
            if (success)
                call.respond(HttpStatusCode.Created)
            else
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(1, "Username is already taken"))
        }
        post("/login") {
            val user = call.receive<User>()
            if (!Database.checkCredentials(user)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            val token = JWT.create()
                .withClaim("username", user.username)
                .withExpiresAt(Date(System.currentTimeMillis() + 5*60*1000))
                .sign(Algorithm.HMAC256(jwtSecret))
            call.respond(mapOf("token" to token))
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
