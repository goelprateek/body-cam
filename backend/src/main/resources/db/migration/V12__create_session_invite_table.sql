create table if not exists session_invite (
    id uuid primary key,
    session_id uuid not null references live_session(id),
    invite_token varchar(120) not null unique,
    participant_role varchar(32) not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp
);

create index if not exists idx_session_invite_session_created_at
    on session_invite(session_id, created_at desc);

create index if not exists idx_session_invite_token
    on session_invite(invite_token);
