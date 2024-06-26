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
import ru.titovtima.songsserver.model.*
import java.util.*

fun Application.configureRouting() {
    install(IgnoreTrailingSlash)
    this.configureSongsRoutes()
    this.configureSongsListsRoutes()
    routing {
        post("/api/v1/register") {
            val userLogin = call.receive<UserLogin>()
            if (!userLogin.checkRegex()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(1,
                    "Username should match ${Authorization.usernameRegex.pattern} and " +
                            "password should match ${Authorization.passwordRegex.pattern}"))
                return@post
            }
            val success = Authorization.register(userLogin)
            when (success) {
                0 -> call.respond(HttpStatusCode.Created)
                1 -> call.respond(HttpStatusCode.BadRequest,
                    ErrorResponse(2, "Username is already taken"))
                2 -> call.respond(HttpStatusCode.InternalServerError,
                    ErrorResponse(3, "Error getting new id"))
                else -> call.respond(HttpStatusCode.InternalServerError,
                    ErrorResponse(4, "Server error"))
            }
        }
        post("/api/v1/login") {
            val userLogin = call.receive<UserLogin>()
            if (!Authorization.checkCredentials(userLogin)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            val token = JWT.create()
                .withClaim("username", userLogin.username)
                .withClaim("created_at", System.currentTimeMillis())
                .withExpiresAt(Date(System.currentTimeMillis() + 30*60*1000))
                .sign(Algorithm.HMAC256(jwtSecret))
            call.respond(mapOf("token" to token))
        }
        get("/api/v1/audio/{uuid}") {
            val uuid = call.parameters["uuid"]
            if (uuid == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val byteArray = SongAudio.loadAudioFromS3(uuid)
            if (byteArray == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.response.header("Content-Type", "audio/mpeg")
            call.respond(byteArray)
        }
        get("/api/v1/user/{username}") {
            val username = call.parameters["username"]
            if (username == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val user = User.readFromDb(username)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(user)
        }
        authenticate("auth-jwt") {
            post("/api/v1/change_password") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                val user = User.readFromDb(username)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val newPassword = call.receive<NewPassword>().password
                if (!Authorization.passwordRegex.matches(newPassword)) {
                    call.respond(HttpStatusCode.BadRequest,
                        "Password should match ${Authorization.passwordRegex.pattern}")
                    return@post
                }
                Authorization.changePassword(user, newPassword)
                call.respond(HttpStatusCode.OK)
            }
            post("/api/v1/audio") {
                val contentType = call.request.contentType()
                if (!contentType.match(ContentType.Audio.MPEG)) {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                    return@post
                }
                val bytes = call.receive<ByteArray>()
                val uuid = SongAudio.uploadAudioToS3(bytes)
                call.respond(mapOf("uuid" to uuid))
            }
        }
    }
}

fun Application.configureSongsRoutes() {
    routing {
        authenticate("auth-jwt", strategy = AuthenticationStrategy.Optional) {
            get("/api/v1/song/{id}") {
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
            get("/api/v1/song/{id}/info") {
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
                val songInfo = SongInfo.readFromDb(songId, user)
                if (songInfo == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, songInfo)
                }
            }
            get("/api/v1/song/{id}/rights") {
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
            get("/api/v1/songs") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val user = username?.let { User.readFromDb(it) }
                call.respond(ListOfSongsResponse(Song.readAllFromDb(user)))
            }
            get("/api/v1/songs/info") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val user = username?.let { User.readFromDb(it) }
                call.respond(ListOfSongsInfoResponse(SongInfo.readAllFromDb(user)))
            }
            get("/api/v1/songs/main_list") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val user = username?.let { User.readFromDb(it) }
                call.respond(ListOfSongsResponse(Song.readMainListFromDb(user)))
            }
            get("/api/v1/songs/main_list/info") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val user = username?.let { User.readFromDb(it) }
                call.respond(ListOfSongsInfoResponse(SongInfo.readMainListFromDb(user)))
            }
        }
        authenticate("auth-jwt") {
            post("/api/v1/song/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                val user = username?.let { it1 -> User.readFromDb(it1) }
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                if (call.parameters["id"] == "new") {
                    val songData = call.receive<NewSongData>()
                    if (songData.public && !user.approved && !user.isAdmin) {
                        call.respond(HttpStatusCode.Forbidden, "You cannot create public songs")
                        return@post
                    }
                    val song = songData.makeSong(user)
                    if (song == null) {
                        call.respond(HttpStatusCode.InternalServerError, "Error getting new song id")
                        return@post
                    }
                    if (!song.saveToDb(user, true)) {
                        call.respond(HttpStatusCode.InternalServerError, "Error while writing to database")
                    } else {
                        call.respond(HttpStatusCode.Created, song)
                    }
                    return@post
                }
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                val oldSong = Song.readFromDb(songId, user)
                if (oldSong == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                if (!Song.checkWriteAccess(songId, user)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                val song = call.receive<Song>()
                if (song.id != songId) {
                    call.respond(HttpStatusCode.BadRequest, "Song id should match id in url")
                    return@post
                }
                if (!oldSong.public && song.public && !user.approved && !user.isAdmin) {
                    call.respond(HttpStatusCode.Forbidden, "You cannot create public songs")
                    return@post
                }
                if (!song.saveToDb(user, false)) {
                    call.respond(HttpStatusCode.InternalServerError, "Error while writing to database")
                } else {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}

fun Application.configureSongsListsRoutes() {
    routing {
        authenticate("auth-jwt", strategy = AuthenticationStrategy.Optional) {
            get("/api/v1/songs_list/{id}") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val user = username?.let { User.readFromDb(it) }
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val list = SongsList.readFromDb(id, user)
                if (list == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(list)
            }
            get("/api/v1/songs_list/{id}/info") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val user = username?.let { User.readFromDb(it) }
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val list = SongsListInfo.readFromDb(id, user)
                if (list == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(list)
            }
            get("/api/v1/songs_list/{id}/songs_info") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val user = username?.let { User.readFromDb(it) }
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val list = SongsListSongsInfo.readFromDb(id, user)
                if (list == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(list)
            }
            get("/api/v1/songs_lists/info") {
                val principal = call.principal<JWTPrincipal>()
                var username: String? = null
                if (principal != null)
                    username = principal.payload.getClaim("username").asString()
                val user = username?.let { User.readFromDb(it) }
                call.respond(ListOfSongsListsInfoResponse(SongsListInfo.readAllFromDb(user)))
            }
        }
        authenticate("auth-jwt") {
            post("/api/v1/songs_list/{id}") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                val user = username?.let { it1 -> User.readFromDb(it1) }
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                if (call.parameters["id"] == "new") {
                    val listData = call.receive<NewSongsList>()
                    if (listData.public && !user.approved && !user.isAdmin) {
                        call.respond(HttpStatusCode.Forbidden, "You cannot create public lists")
                        return@post
                    }
                    val list = listData.makeList(user)
                    if (list == null) {
                        call.respond(HttpStatusCode.InternalServerError, "Error getting new list id")
                        return@post
                    }
                    if (!list.saveToDb(user, true)) {
                        call.respond(HttpStatusCode.InternalServerError, "Error while writing to database")
                    } else {
                        call.respond(HttpStatusCode.Created, SongsListInfo(list.id, list.name, list.owner, list.public))
                    }
                    return@post
                }
                val listId = call.parameters["id"]?.toIntOrNull()
                if (listId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                val oldList = SongsListInfo.readFromDb(listId, user)
                if (oldList == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                if (!PostSongsList.checkWriteAccess(listId, user)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                val list = call.receive<PostSongsList>()
                if (list.id != listId) {
                    call.respond(HttpStatusCode.BadRequest, "List id should match id in url")
                    return@post
                }
                if (!oldList.public && list.public && !user.approved && !user.isAdmin) {
                    call.respond(HttpStatusCode.Forbidden, "You cannot create public lists")
                    return@post
                }
                if (!list.saveToDb(user, false)) {
                    call.respond(HttpStatusCode.InternalServerError, "Error while writing to database")
                } else {
                    call.respond(HttpStatusCode.OK, SongsListInfo(list.id, list.name, list.owner, list.public))
                }
            }
        }
    }
}

@Serializable
data class ErrorResponse(val errorCode: Int, val details: String)

@Serializable
data class NewPassword(val password: String)

@Serializable
data class ListOfSongsResponse(val list: List<Song>, val count: Int = list.size)

@Serializable
data class ListOfSongsInfoResponse(val list: List<SongInfo>, val count: Int = list.size)

@Serializable
data class ListOfSongsListsInfoResponse(val list: List<SongsListInfo>, val count: Int = list.size)
