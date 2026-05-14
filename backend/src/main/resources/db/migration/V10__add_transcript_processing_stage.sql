alter table recording_transcript
    add column if not exists processing_stage varchar(32),
    add column if not exists last_stage_at timestamp with time zone;
