package ru.titovtima.songsserver.model

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.HOST
import ru.titovtima.songsserver.httpClient
import ru.titovtima.songsserver.plugins.ActionToken

@Serializable
class Email(val recipients: List<String>, val subject: String, val contentType: String, val data: String,
            val sender: String = EmailsSender) {
    companion object {
        val SendEmailRequestAddress = "http://127.0.0.1:2389"
        val EmailsSender = "songs.istokspb@yandex.ru"

        fun passwordRecoveryEmail(user: User): Email? {
            if (user.email == null) return null
            val token = ActionToken.createToken(user.id, 1) ?: return null
            return Email(listOf(user.email), "Восстановление пароля", "text/html",
                "Здравствуйте, ${user.username}. <br/>" +
                "Для изменения пароля на сайте <a href=\"$HOST\">$HOST</a> перейдите по " +
                "<a href=\"https://$HOST/reset_password/${user.id}/$token\">ссылке</a>")
        }
    }

    suspend fun send() {
        httpClient.post(SendEmailRequestAddress) {
            contentType(ContentType.Application.Json)
            setBody(this@Email)
        }
    }
}
