create table if not exists recording_metadata (
    recording_id uuid primary key references recording_asset(id) on delete cascade,
    captured_at timestamp with time zone,
    latitude numeric(9,6),
    longitude numeric(9,6),
    altitude_meters numeric(8,2),
    location_accuracy_meters numeric(8,2),
    camera_facing varchar(16),
    thermal_enabled boolean,
    thermal_min_c numeric(6,2),
    thermal_max_c numeric(6,2),
    thermal_avg_c numeric(6,2),
    sensor_payload jsonb,
    created_at timestamp with time zone not null default current_timestamp
);
