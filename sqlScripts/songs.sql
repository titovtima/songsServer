-- drop table if exists
--     artist, group_admin, group_list_reader, group_list_writer, group_song_data, auth_tokens, song_audio,
--     group_song_reader, group_song_writer, song, song_reader, song_writer, songs_list, song_in_list,
--     list_reader, list_writer, user_in_group, users, users_group, user_song_data, song_part, song_performance,
--     old_encoded_password, song_draft, new_song_draft, keys
--     cascade;

create table users (
    id int primary key,
    username varchar(64) not null unique,
    password varchar(128) not null,
    last_login date default now(),
    last_change_password timestamptz default now(),
    approved bool not null default false,
    is_admin bool not null default false
);

create table auth_tokens (
    user_id int not null references users(id),
    token varchar(128) not null unique,
    created_at timestamptz not null default now(),
    used_at timestamptz not null default now()
);

create table artist (
    id int primary key,
    name varchar(64)
);

create table song (
    id int primary key,
    name varchar(256) not null,
    extra text,
    key int,
    owner_id int not null references users(id),
    public boolean,
    in_main_list boolean,
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

create table song_part (
    song_id int not null references song(id),
    type int not null,
    ord int not null,
    name varchar(256) default null,
    data text not null,
    key int default null,
    primary key (song_id, type, ord)
);

create table song_audio (
    song_id int references song(id) on delete set null default null,
    uuid char(36) primary key,
    created_at timestamptz not null default now()
);

create table song_performance (
    song_id int not null references song(id),
    artist_id int references artist(id),
    song_name varchar(256),
    link varchar(1024),
    is_original bool default false,
    is_main bool,
    audio_uuid char(36) references song_audio(uuid) default null
);

create table song_reader (
    user_id int not null references users(id),
    song_id int not null references song(id),
    primary key (user_id, song_id)
);

create table song_writer (
    user_id int not null references users(id),
    song_id int not null references song(id),
    primary key (user_id, song_id)
);

create table songs_list (
    id int primary key,
    name varchar(256) not null,
    public boolean not null default false,
    owner_id int not null references users(id)
);

create table song_in_list (
    song_id int references song(id),
    list_id int references songs_list(id),
    primary key (song_id, list_id)
);

create table list_reader (
    user_id int not null references users(id),
    list_id int not null references songs_list(id),
    primary key (user_id, list_id)
);

create table list_writer (
    user_id int not null references users(id),
    list_id int not null references songs_list(id),
    primary key (user_id, list_id)
);

create table users_group (
    id int primary key,
    name varchar(256),
    owner_id int not null references users(id)
);

create table user_in_group (
    user_id int not null references users(id),
    group_id int not null references users_group(id),
    primary key (user_id, group_id)
);

create table group_admin (
    user_id int not null references users(id),
    group_id int not null references users_group(id),
    primary key (user_id, group_id)
);

create table group_song_reader (
    group_id int not null references users_group(id),
    song_id int not null references song(id),
    primary key (group_id, song_id)
);

create table group_song_writer (
    group_id int not null references users_group(id),
    song_id int not null references song(id),
    primary key (group_id, song_id)
);

create table group_list_reader (
    group_id int not null references users_group(id),
    list_id int not null references songs_list(id),
    primary key (group_id, list_id)
);

create table group_list_writer (
    group_id int not null references users_group(id),
    list_id int not null references songs_list(id),
    primary key (group_id, list_id)
);

create table user_song_data (
    user_id int not null references users(id),
    song_id int not null references song(id),
    text_notes text,
    key int,
    primary key (user_id, song_id)
);

create table group_song_data (
    group_id int not null references users_group(id),
    song_id int not null references song(id),
    text_notes text,
    key int,
    primary key (group_id, song_id)
);

create table old_encoded_password (
    user_id int references users(id) primary key,
    password varchar(128) not null
);

create table song_draft (
    song_id int not null references song(id),
    user_id int not null references users(id),
    song_data text,
    updated_at timestamptz default now()
);

create table new_song_draft (
    user_id int not null references users(id),
    song_data text,
    updated_at timestamptz default now()
);

create table keys (
    name varchar(30) primary key,
    min_key int not null
);

insert into keys (name, min_key) values ('users', 1);
insert into keys (name, min_key) values ('song', 1);
insert into keys (name, min_key) values ('songs_list', 1);
insert into keys (name, min_key) values ('users_group', 1);
insert into keys (name, min_key) values ('artist', 1);

-- drop view if exists song_with_username;
-- drop view if exists songs_list_with_username;

create view song_with_username(
    id, name, extra, key, owner, public, in_main_list, created_at, updated_at
) as
select
s.id, s.name, s.extra, s.key, u.username as owner, s.public, s.in_main_list, s.created_at, s.updated_at
from song s left join users u on s.owner_id = u.id;

create view songs_list_with_username(
    id, name, public, owner
) as
select
    l.id, l.name, l.public, u.username as owner
from songs_list l left join users u on l.owner_id = u.id;

-- drop function if exists public_songs;
-- drop function if exists readable_songs;
-- drop function if exists writable_songs;
-- drop function if exists readable_lists;
-- drop function if exists writable_lists;

create or replace function public_songs() returns table (
    id int,
    name varchar(256),
    extra text,
    key int,
    owner varchar(64),
    public boolean,
    in_main_list boolean,
    created_at timestamptz,
    updated_at timestamptz
) as $$
select
distinct s.id, s.name, s.extra, s.key, s.owner, s.public, s.in_main_list, s.created_at, s.updated_at
from song_with_username s
left join song_in_list sl on s.id = sl.song_id
left join songs_list l on sl.list_id = l.id
where s.public or l.public
order by s.id;
$$ language sql;

create or replace function readable_songs (reader_id int) returns table (
    id int,
    name varchar(256),
    extra text,
    key int,
    owner varchar(64),
    public boolean,
    in_main_list boolean,
    created_at timestamptz,
    updated_at timestamptz
) as
$$
begin
if (select u.is_admin from users u where u.id = reader_id) then
    return query (select * from song_with_username);
else
    return query (select
    distinct s.id, s.name, s.extra, s.key, u.username as owner, s.public, s.in_main_list, s.created_at, s.updated_at
    from song s
    left join song_reader sr on s.id = sr.song_id
    left join song_writer sw on s.id = sw.song_id
    left join song_in_list sl on s.id = sl.song_id
    left join songs_list l on sl.list_id = l.id
    left join list_reader lr on sl.list_id = lr.list_id
    left join list_writer lw on sl.list_id = lw.list_id
    left join group_song_reader gsr on s.id = gsr.song_id
    left join group_song_writer gsw on s.id = gsw.song_id
    left join group_list_reader glr on sl.list_id = glr.list_id
    left join group_list_writer glw on sl.list_id = glw.list_id
    left join users_group g
        on gsr.group_id = g.id or gsw.group_id = g.id
        or glr.group_id = g.id or glw.group_id = g.id
    left join user_in_group ug on g.id = ug.group_id
    left join group_admin ga on g.id = ga.group_id
    left join users u on s.owner_id = u.id
    where s.owner_id = reader_id or l.owner_id = reader_id or s.public or l.public
        or sr.user_id = reader_id or sw.user_id = reader_id or lr.user_id = reader_id or lw.user_id = reader_id
        or g.owner_id = reader_id or ug.user_id = reader_id or ga.user_id = reader_id
    order by s.id);
end if;
end;
$$ language plpgsql;

create or replace function writable_songs (writer_id int) returns table (
    id int,
    name varchar(256),
    extra text,
    key int,
    owner varchar(64),
    public boolean,
    in_main_list boolean,
    created_at timestamptz,
    updated_at timestamptz
) as $$
begin
if (select is_admin from users u where u.id = writer_id) then
    return query (select * from song_with_username);
else
    return query (select
    distinct s.id, s.name, s.extra, s.key, u.username as owner, s.public, s.in_main_list, s.created_at, s.updated_at
    from song s
    left join song_writer sw on s.id = sw.song_id
    left join song_in_list sl on s.id = sl.song_id
    left join songs_list l on sl.list_id = l.id
    left join list_writer lw on sl.list_id = lw.list_id
    left join group_song_writer gsw on s.id = gsw.song_id
    left join group_list_writer glw on sl.list_id = glw.list_id
    left join users_group g on gsw.group_id = g.id or glw.group_id = g.id
    left join user_in_group ug on g.id = ug.group_id
    left join group_admin ga on g.id = ga.group_id
    left join users u on s.owner_id = u.id
    where s.owner_id = writer_id or l.owner_id = writer_id
        or sw.user_id = writer_id or lw.user_id = writer_id
        or g.owner_id = writer_id or ug.user_id = writer_id or ga.user_id = writer_id)
    union
    (select s.id, s.name, s.extra, s.key, s.owner, s.public, s.in_main_list, s.created_at, s.updated_at
    from users u left join public_songs() s on true
    where u.id = writer_id and (u.approved or u.is_admin))
    order by id;
end if;
end;
$$ language plpgsql;

create or replace function readable_lists(reader_id int) returns table (
    id int,
    name varchar(256),
    public boolean,
    owner varchar(64)
) as $$
begin
if (select u.is_admin from users u where u.id = reader_id) then
    return query (select * from songs_list_with_username);
else
    return query (select
    distinct l.id, l.name, l.public, u.username as owner from songs_list l
    left join list_reader lr on l.id = lr.list_id
    left join list_writer lw on l.id = lw.list_id
    left join group_list_reader glr on l.id = glr.list_id
    left join group_list_writer glw on l.id = glw.list_id
    left join users_group g on glr.group_id = g.id or glw.group_id = g.id
    left join user_in_group ug on g.id = ug.group_id
    left join group_admin ga on g.id = ga.group_id
    left join users u on l.owner_id = u.id
    where l.public or l.owner_id = reader_id or lr.user_id = reader_id or lw.user_id = reader_id
       or ug.user_id = reader_id or ga.user_id = reader_id or g.owner_id = reader_id
    order by l.id);
end if;
end;
$$ language plpgsql;

create or replace function writable_lists(writer_id int) returns table (
    id int,
    name varchar(256),
    public boolean,
    owner varchar(64)
) as $$
begin
if (select u.is_admin from users u where u.id = writer_id) then
    return query (select * from songs_list_with_username);
else
    return query (select
    distinct l.id, l.name, l.public, u.username as owner from songs_list l
    left join list_writer lw on l.id = lw.list_id
    left join group_list_writer glw on l.id = glw.list_id
    left join users_group g on glw.group_id = g.id
    left join user_in_group ug on g.id = ug.group_id
    left join group_admin ga on g.id = ga.group_id
    left join users u on l.owner_id = u.id
    where l.owner_id = writer_id or lw.user_id = writer_id
       or ug.user_id = writer_id or ga.user_id = writer_id or g.owner_id = writer_id
    order by l.id)
    union
    (select l.id, l.name, l.public, l.owner
    from users u left join songs_list_with_username l on l.public
    where u.approved and u.id = writer_id)
    order by id;
end if;
end;
$$ language plpgsql;
