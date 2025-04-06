package ru.titovtima.songsserver

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import ru.titovtima.songsserver.model.User
import kotlin.test.*
import ru.titovtima.songsserver.plugins.configureRouting
import ru.titovtima.songsserver.plugins.configureSecurity
import ru.titovtima.songsserver.plugins.configureSerialization

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application {
            configureSecurity()
            configureSerialization()
            configureRouting()
        }
        client.get("/api/v1/user/titovtima").apply {
            assertEquals(HttpStatusCode.OK, status)
            val user = Json.decodeFromString<User>(bodyAsText())
            assertEquals("titovtima", user.username)
            assertEquals(1, user.id)
        }
    }
}
