begin;

create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(8);

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

insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
select 'e2e_alice','e2e_bob','seed DM',
       (select uid from e2e_users where label='alice'),
       (select uid from e2e_users where label='bob');

create or replace function pg_temp.capture_sqlstate(statement text) returns text language plpgsql as $fn$
begin
  execute statement;
  return '00000';
exception when others then
  return sqlstate;
end
$fn$;

grant select on e2e_users to authenticated;
set local role authenticated;
select set_config(
  'request.jwt.claims',
  json_build_object('role','authenticated','sub',(select uid::text from e2e_users where label='alice'))::text,
  true
);

select is(
  (select count(*) from public.direct_messages where sender='e2e_alice' and recipient='e2e_bob'),
  1::bigint,
  'Alice can read her DM'
);

with ins as (
  insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
  values ('e2e_alice','e2e_bob','hello',auth.uid(),
          (select uid from e2e_users where label='bob'))
  returning id
)
select is((select count(*) from ins),1::bigint,'Alice can send Bob a DM');

select is(
  pg_temp.capture_sqlstate($probe$insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
     values ('e2e_bob','e2e_alice','spoof',auth.uid(),
             (select uid from e2e_users where label='alice'))$),
  '42501',
  'Alice cannot spoof Bob as sender'
);

select is(
  pg_temp.capture_sqlstate($probe$insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
     values ('e2e_alice','e2e_bob','wrong recipient',
             auth.uid(),(select uid from e2e_users where label='alice'))$),
  '42501',
  'Alice cannot make the recipient herself'
);

select set_config(
  'request.jwt.claims',
  json_build_object('role','authenticated','sub',(select uid::text from e2e_users where label='bob'))::text,
  true
);

select is(
  (select count(*) from public.direct_messages where sender='e2e_alice' and recipient='e2e_bob'),
  2::bigint,
  'Bob can read the Alice-to-Bob conversation'
);

with upd as (
  update public.direct_messages
  set is_read=true
  where sender='e2e_alice' and recipient='e2e_bob'
  returning id
)
select is((select count(*) from upd),2::bigint,'Bob can mark received DMs read');

select set_config(
  'request.jwt.claims',
  json_build_object('role','authenticated','sub',(select uid::text from e2e_users where label='charlie'))::text,
  true
);

select is(
  (select count(*) from public.direct_messages where sender='e2e_alice' and recipient='e2e_bob'),
  0::bigint,
  'Unrelated user cannot read the DM conversation'
);

select is(
  pg_temp.capture_sqlstate($probe$insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
     values ('e2e_charlie','e2e_alice','unauthorized',auth.uid(),
             (select uid from e2e_users where label='alice'))$),
  '42501',
  'Charlie cannot send an unauthorized DM'
);

select * from finish();
rollback;
