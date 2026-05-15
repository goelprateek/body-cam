alter table recording_transcript
    add column if not exists last_error_stage varchar(32),
    add column if not exists retry_count integer not null default 0;
