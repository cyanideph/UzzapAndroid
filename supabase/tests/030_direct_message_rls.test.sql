begin;

select plan(10);

create temporary table e2e_users (
  label text primary key,
  uid uuid not null,
  username text not null
) on commit drop;

insert into e2e_users values
  ('alice','00000000-0000-0000-0000-0000000000a1','e2e_alice'),
  ('bob','00000000-0000-0000-0000-0000000000b2','e2e_bob'),
  ('charlie','00000000-0000-0000-0000-0000000000c3','e2e_charlie');

insert into public.profiles(owner_id, username, nickname)
select uid, username, initcap(username)
from e2e_users
on conflict do nothing;

insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
select 'e2e_alice','e2e_bob','seed DM',
       (select uid from e2e_users where label='alice'),
       (select uid from e2e_users where label='bob');

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

select lives_ok(
  $$ insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
     values ('e2e_alice','e2e_bob','hello',auth.uid(),
             (select uid from e2e_users where label='bob')) $$,
  'Alice can send Bob a DM'
);

select throws_ok(
  $$ insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
     values ('e2e_bob','e2e_alice','spoof',auth.uid(),
             (select uid from e2e_users where label='alice')) $$,
  '42501',
  'new row violates row-level security policy for table "direct_messages"',
  'Alice cannot spoof Bob as sender'
);

select throws_ok(
  $$ insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
     values ('e2e_alice','e2e_bob','wrong recipient',
             auth.uid(),(select uid from e2e_users where label='alice')) $$,
  '42501',
  'new row violates row-level security policy for table "direct_messages"',
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

select lives_ok(
  $$ update public.direct_messages
     set is_read=true
     where sender='e2e_alice' and recipient='e2e_bob' $$,
  'Bob can mark received DMs read'
);

select is(
  (select count(*) from public.direct_messages where sender='e2e_alice' and recipient='e2e_bob' and is_read=true),
  2::bigint,
  'Bob read-state update is visible'
);

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

select throws_ok(
  $$ insert into public.direct_messages(sender,recipient,body,sender_id,recipient_id)
     values ('e2e_charlie','e2e_alice','unauthorized',auth.uid(),
             (select uid from e2e_users where label='alice')) $$,
  '42501',
  'new row violates row-level security policy for table "direct_messages"',
  'Charlie cannot send an unauthorized DM'
);

select * from finish();
rollback;
