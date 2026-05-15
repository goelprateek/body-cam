create table if not exists recording_transcript (
    id uuid primary key,
    recording_id uuid not null unique references recording_asset(id) on delete cascade,
    status varchar(32) not null,
    engine varchar(64),
    model varchar(128),
    language_code varchar(16),
    full_text text,
    error_message text,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp
);

create index if not exists idx_recording_transcript_status
    on recording_transcript(status);

create table if not exists recording_transcript_segment (
    id uuid primary key,
    transcript_id uuid not null references recording_transcript(id) on delete cascade,
    segment_index integer not null,
    start_seconds numeric(8,2) not null,
    end_seconds numeric(8,2) not null,
    text text not null,
    confidence numeric(5,4),
    created_at timestamp with time zone not null default current_timestamp,
    unique (transcript_id, segment_index)
);

create index if not exists idx_recording_transcript_segment_transcript
    on recording_transcript_segment(transcript_id, segment_index);
