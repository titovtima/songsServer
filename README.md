# SongsServer

A backend server for songs site. Frontend repository: [github.com/titovtima/songsSite](https://github.com/titovtima/songsSite).

Two independent instances are running on [songs.istokspb.org](https://songs.istokspb.org) and [test.songs.titovtima.ru](https://test.songs.titovtima.ru).

File [sqlScripts/songs.sql](https://github.com/titovtima/songsServer/blob/main/sqlScripts/songs.sql) contains database structure used by server.  
PostgreSQL is being used as Database Management System.

The server is running on `localhost:2403` and use proxy server (caddy) to establish TLS and separate from other services.

### Environment

Env variables that are needed for the project works properly:

* `AWS_ACCESS_KEY_ID` & `AWS_SECRET_ACCESS_KEY` - key for S3 storage access
* `CACHE_PATH` - path to cache files got from S3 storage
* `HOST` - external host where the app instance is running. Used in emails
* `JWT_SECRET` - secret string for jwt authorization (though main auth strategy is by bearer token)
* `POSTGRES_PASSWORD` - database password for user `songsserver`
