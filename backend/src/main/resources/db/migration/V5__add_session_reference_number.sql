alter table live_session
    add column if not exists reference_number varchar(120);

update live_session
set reference_number = coalesce(nullif(reference_number, ''), 'LEGACY-' || substring(id::text, 1, 8))
where reference_number is null
   or btrim(reference_number) = '';

alter table live_session
    alter column reference_number set not null;
