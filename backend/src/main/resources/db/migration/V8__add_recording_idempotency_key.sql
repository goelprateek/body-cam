alter table if exists recording_asset
    add column if not exists idempotency_key varchar(128);

create unique index if not exists uq_recording_asset_idempotency_key
    on recording_asset(idempotency_key)
    where idempotency_key is not null;
