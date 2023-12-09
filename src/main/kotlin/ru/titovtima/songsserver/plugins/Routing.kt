package ru.titovtima.songsserver.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.titovtima.songsserver.User
import java.util.*

fun Application.configureRouting() {
    install(IgnoreTrailingSlash)
    routing {
        post("/login") {
            val user = call.receive<User>()
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
