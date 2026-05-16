\set ON_ERROR_STOP on

begin;

create temp table temp_cleanup_session_ids (
    session_id uuid primary key
);

insert into temp_cleanup_session_ids (session_id)
select distinct nullif(trim(value), '')::uuid
from unnest(string_to_array(:'session_ids_csv', ',')) as value;

do $$
declare
    requested_count integer;
    eligible_count integer;
begin
    select count(*) into requested_count
    from temp_cleanup_session_ids;

    select count(*) into eligible_count
    from live_session session_row
    join temp_cleanup_session_ids requested on requested.session_id = session_row.id
    where session_row.status = 'ENDED';

    if requested_count = 0 then
        raise exception 'No session ids were provided for cleanup.';
    end if;

    if requested_count <> eligible_count then
        raise exception
            'Refusing cleanup because % requested sessions are no longer eligible ended sessions.',
            requested_count - eligible_count;
    end if;
end $$;

create temp table cleanup_sessions as
select session_row.id
from live_session session_row
join temp_cleanup_session_ids requested on requested.session_id = session_row.id;

delete from session_invite
where session_id in (select id from cleanup_sessions);

delete from session_recording_export
where session_id in (select id from cleanup_sessions);

delete from recording_asset
where session_id in (select id from cleanup_sessions);

delete from live_session
where id in (select id from cleanup_sessions);

commit;
