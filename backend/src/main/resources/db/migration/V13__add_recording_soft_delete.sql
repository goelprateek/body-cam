alter table if exists recording_asset
    add column if not exists is_active boolean not null default true,
    add column if not exists deactivated_at timestamp with time zone;

create index if not exists idx_recording_asset_active_created_at
    on recording_asset(is_active, created_at desc, id desc);
