insert into app_user (id, username, display_name, password_hash, role)
values
    ('11111111-1111-1111-1111-111111111111', 'worker1', 'Field Worker 1', '{noop}worker123', 'WORKER'),
    ('22222222-2222-2222-2222-222222222222', 'operator1', 'Operator 1', '{noop}operator123', 'OPERATOR')
on conflict (username) do nothing;
