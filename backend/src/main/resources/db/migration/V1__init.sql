create table if not exists app_user (
    id uuid primary key,
    username varchar(120) not null unique,
    display_name varchar(160) not null,
    password_hash varchar(255) not null,
    role varchar(40) not null,
    created_at timestamp with time zone not null default current_timestamp
);

create table if not exists live_session (
    id uuid primary key,
    worker_id uuid not null,
    worker_name varchar(160) not null,
    room_name varchar(120) not null unique,
    status varchar(40) not null,
    started_at timestamp with time zone not null,
    ended_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp
);

create table if not exists recording_asset (
    id uuid primary key,
    session_id uuid not null references live_session(id),
    object_key varchar(255) not null,
    playback_url varchar(500) not null,
    duration_seconds integer,
    created_at timestamp with time zone not null default current_timestamp
);
