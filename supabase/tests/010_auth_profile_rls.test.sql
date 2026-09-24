begin;

create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

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

insert into auth.users(id, aud, role, email, raw_user_meta_data, created_at, updated_at)
select uid, 'authenticated', 'authenticated', username || '@e2e.invalid',
       jsonb_build_object('username',username,'nickname',initcap(username)),
       now(), now()
from e2e_users;

grant select on e2e_users to authenticated;
set local role authenticated;
select set_config(
  'request.jwt.claims',
  json_build_object('role','authenticated','sub',(select uid::text from e2e_users where label='alice'))::text,
  true
);

select is(auth.uid(), (select uid from e2e_users where label='alice'), 'auth.uid resolves to Alice');

select lives_ok(
  $$ update public.profiles set status_message = 'E2E updated' where owner_id = auth.uid() $$,
  'Alice can update her own profile'
);

select is(
  (select status_message from public.profiles where username='e2e_alice'),
  'E2E updated',
  'Alice profile update persisted'
);

select results_eq(
  $$ update public.profiles
     set status_message = 'SPOOFED'
     where owner_id = (select uid from e2e_users where label='bob')
     returning username $$,
  $ select username from e2e_users where false $,
  'Alice cannot update Bob profile'
);

select throws_ok(
  $$ insert into public.profiles(owner_id, username, nickname, email)
     values ((select uid from e2e_users where label='bob'),
             'e2e_spoof_profile','Spoof','spoof@e2e.invalid') $$,
  '42501',
  'new row violates row-level security policy for table "profiles"',
  'Alice cannot insert a profile owned by Bob'
);

select lives_ok(
  $$ insert into public.profiles(owner_id, username, nickname, email)
     values (auth.uid(),'e2e_alice_extra','Alice Extra','alice-extra@e2e.invalid') $$,
  'Alice can insert a profile owned by herself'
);

select set_config(
  'request.jwt.claims',
  json_build_object('role','authenticated','sub',(select uid::text from e2e_users where label='charlie'))::text,
  true
);

select is(
  (select count(*) from public.profiles where username='e2e_bob'),
  1::bigint,
  'Profiles are publicly readable as configured'
);

select results_eq(
  $$ update public.profiles
     set status_message = 'CHARLIE SPOOF'
     where owner_id = (select uid from e2e_users where label='alice')
     returning username $$,
  $ select username from e2e_users where false $,
  'Charlie cannot update Alice profile'
);

select * from finish();
rollback;
