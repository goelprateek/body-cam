create table if not exists session_recording_export (
    id uuid primary key,
    session_id uuid not null references live_session(id),
    status varchar(32) not null,
    object_key varchar(255),
    package_size_bytes bigint,
    artifact_count integer,
    error_message text,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create index if not exists idx_session_recording_export_session_created_at
    on session_recording_export(session_id, created_at desc);

create index if not exists idx_session_recording_export_status_created_at
    on session_recording_export(status, created_at asc);
