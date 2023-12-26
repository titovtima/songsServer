package ru.titovtima.songsserver

import java.sql.Connection
import java.sql.DriverManager

class Database {
    companion object {
        val connection: Connection = DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/songsserver", "songsserver", "my_password")
    }
}