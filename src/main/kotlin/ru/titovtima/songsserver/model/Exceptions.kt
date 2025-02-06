package ru.titovtima.songsserver.model

class SavingToDbException(message: String) : Exception("Error saving to database: $message") {
    constructor() : this("")
}
