begin;

create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(11);

create temporary table e2e_users (
  label text primary key,
  uid uuid not null,
  username text not null
) on commit drop;

insert into e2e_users values
  ('alice','00000000-0000-0000-0000-0000000000a1','e2e_alice'),
  ('bob','00000000-0000-0000-0000-0000000000b2','e2e_bob'),
  ('charlie','00000000-0000-0000-0000-0000000000c3','e2e_charlie');

insert into auth.users(id, aud, role, email, raw_user_meta_data, created_at, updated_at)
select uid, 'authenticated', 'authenticated', username || '@e2e.invalid',
       jsonb_build_object('username',username,'nickname',initcap(username)),
       now(), now()
from e2e_users;

create temporary table e2e_room(name text primary key) on commit drop;
insert into e2e_room values ('e2e-room-' || substr(gen_random_uuid()::text,1,8));

insert into public.chat_rooms(id, category_id, name, topic, capacity, is_moderator_only)
select 'e2e-' || substr(gen_random_uuid()::text,1,8), null, name, 'pgTAP E2E', 50, false
from e2e_room;

create or replace function pg_temp.capture_sqlstate(statement text) returns text language plpgsql as $fn$
begin
  execute statement;
  return '00000';
exception when others then
  return sqlstate;
end
$fn$;

grant select on e2e_room to authenticated;
grant select on e2e_users to authenticated;

set local role authenticated;
select set_config(
  'request.jwt.claims',
  json_build_object('role','authenticated','sub',(select uid::text from e2e_users where label='alice'))::text,
  true
);

insert into public.room_participants(room_name, username, owner_id)
values ((select name from e2e_room),'e2e_alice',auth.uid());

select set_config(
  'request.jwt.claims',
  json_build_object('role','authenticated','sub',(select uid::text from e2e_users where label='bob'))::text,
  true
);

insert into public.room_participants(room_name, username, owner_id)
values ((select name from e2e_room),'e2e_bob',auth.uid());

select set_config(
  'request.jwt.claims',
  json_build_object('role','authenticated','sub',(select uid::text from e2e_users where label='alice'))::text,
  true
);

select ok((select is_active_room_member((select name from e2e_room))), 'Alice is an active room member');

with ins as (
  insert into public.room_messages(room_name,sender,body,is_system,sender_id)
  values ((select name from e2e_room),'e2e_alice','hello from Alice',false,auth.uid())
  returning id
)
select is((select count(*) from ins),1::bigint,'Alice can send a room message');

select is(
  (select count(*) from public.room_messages
   where room_name=(select name from e2e_room) and sender='E2e_alice' and body='hello from Alice'),
  1::bigint,
  'Alice message exists'
);

with ins as (
  insert into public.room_messages(room_name,sender,body,is_system,sender_id)
  values ((select name from e2e_room),'e2e_bob','spoof',false,
          (select uid from e2e_users where label='bob'))
  returning id,sender,sender_id,is_system
)
select is(
  (select sender_id from ins),
  (select uid from e2e_users where label='alice'),
  'Room trigger normalizes spoofed sender_id to Alice'
);

select isnt(
  pg_temp.capture_sqlstate($probe$insert into public.room_messages(room_name,sender,body,is_system,sender_id)
     values ((select name from e2e_room),'UzzapBot','fake system',true,auth.uid())$probe$),
  '00000',
  'Authenticated user cannot forge a system message'
);

select is(
  (select count(*) from public.room_messages where room_name=(select name from e2e_room) and sender='E2e_alice' and body='hello from Alice'),
  1::bigint,
  'Alice can read her room message as an active member'
);

select set_config(
  'request.jwt.claims',
  json_build_object('role','authenticated','sub',(select uid::text from e2e_users where label='charlie'))::text,
  true
);

select is(
  (select count(*) from public.room_messages where room_name=(select name from e2e_room)),
  0::bigint,
  'Non-member cannot read room messages'
);

select isnt(
  pg_temp.capture_sqlstate($probe$insert into public.room_messages(room_name,sender,body,is_system,sender_id)
     values ((select name from e2e_room),'e2e_charlie','non-member',false,auth.uid())$probe$),
  '00000',
  'Non-member cannot send room messages'
);

select is(
  (select count(*) from public.room_participants where room_name=(select name from e2e_room)),
  0::bigint,
  'Non-member cannot see seeded room participants'
);

select is(
  (select count(*) from public.room_participants where owner_id=auth.uid()),
  0::bigint,
  'Charlie cannot see Alice/Bob participant rows'
);

select is(
  (select count(*) from public.chat_rooms where name=(select name from e2e_room)),
  1::bigint,
  'Room metadata is publicly readable'
);

select * from finish();
rollback;
