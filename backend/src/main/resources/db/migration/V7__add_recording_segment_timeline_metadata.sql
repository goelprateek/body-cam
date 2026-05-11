alter table if exists recording_metadata
    add column if not exists segment_sequence integer,
    add column if not exists segment_started_at timestamp with time zone,
    add column if not exists segment_ended_at timestamp with time zone,
    add column if not exists session_elapsed_start_ms bigint,
    add column if not exists session_elapsed_end_ms bigint;

create index if not exists idx_recording_metadata_segment_sequence
    on recording_metadata(segment_sequence);
