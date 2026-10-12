begin;

create extension if not exists pgtap with schema extensions;
set local search_path = extensions, public;

select plan(12);

select has_table('public','profiles','profiles table exists');
select has_table('public','chat_rooms','chat_rooms table exists');
select has_table('public','room_participants','room_participants table exists');
select has_table('public','room_messages','room_messages table exists');
select has_table('public','buddies','buddies table exists');
select has_table('public','buddy_requests','buddy_requests table exists');
select has_table('public','direct_messages','direct_messages table exists');
select has_table('public','room_invitations','room_invitations table exists');
select has_table('public','blocked_users','blocked_users table exists');
select has_table('public','user_reports','user_reports table exists');
select has_table('public','moderator_roles','moderator_roles table exists');
select has_table('public','moderation_actions','moderation_actions table exists');

select * from finish();
rollback;
