package ru.titovtima.songsserver

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import ru.titovtima.songsserver.plugins.configureRouting
import ru.titovtima.songsserver.plugins.configureSecurity
import ru.titovtima.songsserver.plugins.configureSerialization
import java.sql.Connection
import java.sql.DriverManager

fun main() {
    embeddedServer(Netty, port = 2403, host = "127.0.0.1", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSecurity()
    configureSerialization()
    configureRouting()
}

val dbConnection: Connection = DriverManager.getConnection(
    "jdbc:postgresql://localhost:5432/songsserver", "songsserver", "my_password")
