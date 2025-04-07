package ru.titovtima.songsserver.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import ru.titovtima.songsserver.model.*
import java.io.File
import java.util.*

fun Application.configureRouting() {
    install(IgnoreTrailingSlash)
    install(PartialContent)
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Head)
    }
    this.configureAuthRoutes()
    this.configureSongsRoutes()
    this.configureSongsListsRoutes()
    routing {
        get("/api/v1/audio/{uuid}") {
            val uuid = call.parameters["uuid"]
            if (uuid == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            MutexByString.withLock(uuid) {
                runBlocking {
                    val file = File(System.getenv("CACHE_PATH") + uuid)
                    if (!file.exists() || (Date().time - file.lastModified() > 1000 * 60 * 60)) {
                        val byteArray = SongAudio.loadAudioFromS3(uuid)
                        if (byteArray == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@runBlocking
                        }
                        file.writeBytes(byteArray)
                    }
                    call.response.header("Content-Type", "audio/mpeg")
                    call.respondFile(file)
                }
            }
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
        get("/api/v1/artists") {
            val list = Artist.readAllFromDb()
            call.respond(list)
        }
        get("/api/v1/artist/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Artist id must be integer")
                return@get
            }
            val artist = Artist.readFromDb(id)
            if (artist == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respond(artist)
        }
        authenticate("auth-bearer") {
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
            post("/api/v1/song/{id}/audio") {
                val songId = call.parameters["id"]
                val contentType = call.request.contentType()
                if (!contentType.match(ContentType.Audio.MPEG)) {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                    return@post
                }
                val bytes = call.receive<ByteArray>()
                val uuid = SongAudio.uploadAudioToS3(bytes, songId ?: "")
                call.respond(mapOf("uuid" to uuid))
            }
            post("/api/v1/artist/{id}") {
                val id = call.parameters["id"]
                if (id == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                val artist = call.receive<Artist>()
                val idInt = id.toIntOrNull()
                if (id == "new" || (idInt != null && idInt < 0)) {
                    val newId = Artist.saveNew(artist.name)
                    call.respond(HttpStatusCode.Created, Artist(newId, artist.name))
                    return@post
                }
                if (idInt == null || artist.id != idInt) {
                    call.respond(HttpStatusCode.BadRequest, "Error parsing id")
                    return@post
                }
                artist.saveToDb()
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

fun Application.configureAuthRoutes() {
    routing {
        post("/api/v1/register") {
            val registerData = call.receive<UserRegisterData>()
            if (!registerData.checkRegex()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(1,
                    "Username should match ${Authorization.usernameRegex.pattern} and " +
                            "password should match ${Authorization.passwordRegex.pattern}"))
                return@post
            }
            val success = Authorization.register(registerData)
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
            val user = User.readFromDb(userLogin.username)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            val token = AuthToken.createToken(user.id)
            if (token == null)
                call.respond(HttpStatusCode.BadGateway, ErrorResponse(1, "Error generating token"))
            else
                call.respond(mapOf("token" to token))
        }
        post("/api/v1/reset_password/{userId}/{token}") {
            val userId = call.parameters["userId"]?.toIntOrNull()
            val token = call.parameters["token"]
            if (userId == null || token == null || !ActionToken.checkToken(userId, token, 1)) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val newPassword = call.receive<OptNewPassword>().password
            if (newPassword == null) {
                call.respond(HttpStatusCode(211, "Token checked"))
                return@post
            }
            if (!Authorization.passwordRegex.matches(newPassword)) {
                call.respond(HttpStatusCode.BadRequest,
                    "Password should match ${Authorization.passwordRegex.pattern}")
                return@post
            }
            Authorization.changePassword(userId, newPassword)
            if (call.request.queryParameters["revokeTokens"] == "true") {
                Authorization.revokeAllTokens(userId)
            }
            ActionToken.deleteToken(userId, token, 1)
            call.respond(HttpStatusCode.OK)
        }
        post("/api/v1/password_recovery") {
            val data = call.receive<PasswordRecoveryRequest>()
            val user1 = data.username?.let { User.readFromDb(it) }
            val usersList = arrayListOf(user1)
            if (data.email != null) usersList.addAll(User.getByEmail(data.email))
            var count = 0
            for (user in usersList.filterNotNull()) {
                val email = Email.passwordRecoveryEmail(user) ?: continue
                email.send()
                count++
            }
            if (count > 0)
                call.respond(HttpStatusCode.OK)
            else
                call.respond(HttpStatusCode.BadRequest)
        }
        authenticate("auth-bearer") {
            post("/api/v1/change_password") {
                val user = getUser(call)
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
                Authorization.changePassword(user.id, newPassword)
                if (call.request.queryParameters["revokeTokens"] == "true") {
                    Authorization.revokeAllTokens(user.id)
                }
                call.respond(HttpStatusCode.OK)
            }
            get("/api/v1/users/me") {
                val user = getUser(call)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                call.respond(user)
            }
            post("/api/v1/change_email") {
                val user = getUser(call)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val email = call.receive<ChangeEmailRequest>().email
                user.changeEmail(email)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

fun Application.configureSongsRoutes() {
    routing {
        authenticate("auth-bearer", strategy = AuthenticationStrategy.Optional) {
            get("/api/v1/song/{id}") {
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val user = getUser(call)
                val song = Song.readFromDb(songId, user)
                if (song == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, song)
                }
            }
            get("/api/v1/song/{id}/info") {
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val user = getUser(call)
                val songInfo = SongInfo.readFromDb(songId, user)
                if (songInfo == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, songInfo)
                }
            }
            get("/api/v1/song/{id}/rights") {
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val user = getUser(call)
                val songRights = SongRights.readFromDb(songId, user)
                if (songRights == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, songRights)
                }
            }
            get("/api/v1/songs") {
                val user = getUser(call)
                call.respond(ListOfSongsResponse(Song.readAllFromDb(user)))
            }
            get("/api/v1/songs/info") {
                val user = getUser(call)
                call.respond(ListOfSongsInfoResponse(SongInfo.readAllFromDb(user)))
            }
            get("/api/v1/songs/main_list") {
                val user = getUser(call)
                call.respond(ListOfSongsResponse(Song.readMainListFromDb(user)))
            }
            get("/api/v1/songs/main_list/info") {
                val user = getUser(call)
                call.respond(ListOfSongsInfoResponse(SongInfo.readMainListFromDb(user)))
            }
        }
        authenticate("auth-bearer") {
            post("/api/v1/song/{id}") {
                val user = getUser(call)
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
                if (!SongRights.checkWriteAccess(songId, user)) {
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
            post("/api/v1/song/{id}/rights") {
                val user = getUser(call)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                val rights = call.receive<SongRights>()
                if (rights.songId != songId) {
                    call.respond(HttpStatusCode.BadRequest, "Song id should match id in url")
                    return@post
                }
                if (!rights.checkWriteAccess(user)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                if (!rights.writeToDb(user)) {
                    call.respond(HttpStatusCode.BadGateway, "Error writing to db")
                    return@post
                }
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

fun Application.configureSongsListsRoutes() {
    routing {
        authenticate("auth-bearer", strategy = AuthenticationStrategy.Optional) {
            get("/api/v1/songs_list/{id}") {
                val user = getUser(call)
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
                val user = getUser(call)
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
                val user = getUser(call)
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
                val principal = call.principal<UserIdIntPrincipal>()
                val userId = principal?.id
                val user = userId?.let { User.readFromDb(it) }
                call.respond(ListOfSongsListsInfoResponse(SongsListInfo.readAllFromDb(user)))
            }
            get("/api/v1/songs_list/{id}/rights") {
                val user = getUser(call)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                val listId = call.parameters["id"]?.toIntOrNull()
                if (listId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val rights = SongsListRights.readFromDb(listId, user)
                if (rights == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, rights)
                }
            }
        }
        authenticate("auth-bearer") {
            post("/api/v1/songs_list/{id}") {
                val user = getUser(call)
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
                val list = call.receive<SongsList>()
                if (list.id != listId) {
                    call.respond(HttpStatusCode.BadRequest, "List id should match id in url")
                    return@post
                }
                val oldList = SongsListInfo.readFromDb(listId, user)
                if (oldList == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                if (!SongsList.checkWriteAccess(listId, user)) {
                    call.respond(HttpStatusCode.Forbidden)
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
            post("/api/v1/songs_list/{id}/rights") {
                val user = getUser(call)
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val listId = call.parameters["id"]?.toIntOrNull()
                if (listId == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                val rights = call.receive<SongsListRights>()
                if (rights.listId != listId) {
                    call.respond(HttpStatusCode.BadRequest, "List id should match id in url")
                    return@post
                }
                if (!SongsListRights.checkWriteAccess(listId, user)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                if (!rights.writeToDb(user)) {
                    call.respond(HttpStatusCode.BadGateway, "Error while writing to database")
                } else {
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}

fun getUser(call: ApplicationCall): User? {
    val principal = call.principal<UserIdIntPrincipal>()
    val userId = principal?.id
    return userId?.let { User.readFromDb(it) }
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

@Serializable
data class PasswordRecoveryRequest(val email: String? = null, val username: String? = null)

@Serializable
data class ChangeEmailRequest(val email: String)

@Serializable
data class OptNewPassword(val password: String? = null)
