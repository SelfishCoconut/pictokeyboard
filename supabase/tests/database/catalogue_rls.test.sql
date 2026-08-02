-- What a hostile client holding the publishable key can and cannot do.
--
-- The publishable key ships inside the APK, so every assertion here is a claim
-- about the database rather than about the app. If one of them fails, the app
-- has no boundary at all — hiding a button proves nothing.
--
-- Run with: npx supabase test db

begin;
create extension if not exists pgtap with schema extensions;

select plan(30);

-- ---------------------------------------------------------------------------
-- Fixtures: two caregivers, one official board, one community board, one
-- withdrawn board. Written as the owner, because seeding an official board is
-- something only the secret key can do.
-- ---------------------------------------------------------------------------

\set ana             '11111111-1111-1111-1111-111111111111'
\set bruno           '22222222-2222-2222-2222-222222222222'
\set official_board  'aaaaaaaa-0000-0000-0000-000000000001'
\set community_board 'bbbbbbbb-0000-0000-0000-000000000002'
\set withdrawn_board 'cccccccc-0000-0000-0000-000000000003'
\set ana_new_board   'eeeeeeee-0000-0000-0000-000000000004'

insert into auth.users (id, instance_id, aud, role, email, created_at, updated_at)
values
  (:'ana',   '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ana@example.test',   now(), now()),
  (:'bruno', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'bruno@example.test', now(), now());

select is(
  (select count(*)::int from public.profiles where id in (:'ana', :'bruno')),
  2,
  'signing up creates a profile row, so a published board always has an author to point at'
);

select is(
  (select display_name from public.profiles where id = :'ana'),
  null::text,
  'a new profile has no display name — a public byline is chosen, never derived from an email address'
);

insert into public.published_boards
  (id, author_id, name, language, tags, color_argb, licence, is_official)
values
  (:'official_board',  :'ana',   'Primeras palabras', 'es',
   '{topic_food,place_home}', -14405057, 'CC-BY-NC-SA-4.0', true),
  (:'community_board', :'bruno', 'Día de colegio',    'es',
   '{place_school}',          -14405057, 'CC-BY-NC-SA-4.0', false),
  (:'withdrawn_board', :'bruno', 'Retirado',          'es',
   '{place_home}',            -14405057, 'CC-BY-NC-SA-4.0', false);

update public.published_boards set withdrawn_at = now() where id = :'withdrawn_board';

insert into public.published_board_payloads (board_id, payload)
values
  (:'official_board',
   '{"categories":[{"name":"Comida","pictos":[{"label":"agua","arasaacId":2248},{"label":"pan","arasaacId":2465}]}]}'),
  (:'community_board',
   '{"categories":[{"name":"Clase","pictos":[{"label":"profesor","arasaacId":6009}]},{"name":"Patio","pictos":[]}]}'),
  (:'withdrawn_board',
   '{"categories":[]}');

-- ---------------------------------------------------------------------------
-- Counts are derived, never asserted by the client
-- ---------------------------------------------------------------------------

select is(
  (select picto_count from public.published_boards where id = :'official_board'),
  2,
  'picto count comes from the payload, so a board cannot advertise itself as larger than it is'
);

select is(
  (select category_count from public.published_boards where id = :'community_board'),
  2,
  'category count comes from the payload'
);

-- ---------------------------------------------------------------------------
-- A published board carries no photographs (§4.2)
-- ---------------------------------------------------------------------------

select throws_ok(
  $$ insert into public.published_board_payloads (board_id, payload)
     values ('cccccccc-0000-0000-0000-000000000003',
             '{"categories":[{"pictos":[{"label":"mamá","imagePath":"/data/photos/mum.jpg"}]}]}') $$,
  '23514',
  null,
  'a payload naming a photo path is refused by the database, not merely by our own client'
);

select throws_ok(
  $$ insert into public.published_board_payloads (board_id, payload)
     values ('cccccccc-0000-0000-0000-000000000003',
             '{"categories":[{"pictos":[{"label":"mamá","imagePath":null}]}]}') $$,
  '23514',
  null,
  'the media keys must be absent entirely — nulling them is not stripping them'
);

-- ---------------------------------------------------------------------------
-- Signed out
-- ---------------------------------------------------------------------------

set local role anon;

select is(
  (select count(*)::int from public.published_boards),
  2,
  'signed out, the whole live catalogue is visible — browsing is what makes signing up worth doing'
);

select is(
  (select count(*)::int from public.published_boards where id = :'community_board'),
  1,
  'signed out, a community board is listed with its name, tags and counts'
);

select is(
  (select count(*)::int from public.published_board_payloads where board_id = :'official_board'),
  1,
  'signed out, an official board downloads — the on-ramp for someone who has just installed the app'
);

select is(
  (select count(*)::int from public.published_board_payloads where board_id = :'community_board'),
  0,
  'SEEING IS NOT DOWNLOADING: signed out, the community payload is unreadable though the board is listed'
);

select is(
  (select count(*)::int from public.published_boards where id = :'withdrawn_board'),
  0,
  'signed out, a withdrawn board is gone from the catalogue'
);

select is(
  (select count(*)::int from public.profiles),
  2,
  'signed out, only authors of live boards are visible — this is not a register of everyone who signed up'
);

select throws_ok(
  $$ insert into public.published_boards (author_id, name, language, color_argb, licence)
     values (null, 'Anónimo', 'es', -1, 'CC-BY-NC-SA-4.0') $$,
  '42501',
  null,
  'signed out, publishing is refused'
);

reset role;

-- ---------------------------------------------------------------------------
-- Signed in as Ana
-- ---------------------------------------------------------------------------

set local role authenticated;
set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}';

select is(
  (select count(*)::int from public.published_board_payloads where board_id = :'community_board'),
  1,
  'signed in, a community board downloads'
);

select is(
  (select count(*)::int from public.published_board_payloads where board_id = :'withdrawn_board'),
  0,
  'a withdrawn board does not download, signed in or not'
);

select throws_ok(
  $$ insert into public.published_boards (author_id, name, language, color_argb, licence)
     values ('22222222-2222-2222-2222-222222222222', 'Suplantación', 'es', -1, 'CC-BY-NC-SA-4.0') $$,
  '42501',
  null,
  'publishing under someone else''s author_id is refused'
);

select throws_ok(
  $$ insert into public.published_boards (author_id, name, language, color_argb, licence, is_official)
     values ('11111111-1111-1111-1111-111111111111', 'Autoascendido', 'es', -1, 'CC-BY-NC-SA-4.0', true) $$,
  '42501',
  null,
  'a client cannot publish an official board — it holds no privilege on that column at all'
);

select throws_ok(
  $$ update public.published_boards set is_official = true
     where id = 'aaaaaaaa-0000-0000-0000-000000000001' $$,
  '42501',
  null,
  'nor promote an existing board to official'
);

with hijacked as (
  update public.published_boards set name = 'Secuestrado'
  where id = :'community_board' returning 1
)
select is(
  (select count(*)::int from hijacked),
  0,
  'editing another caregiver''s published board changes nothing'
);

with withdrawn as (
  delete from public.published_boards where id = :'community_board' returning 1
)
select is(
  (select count(*)::int from withdrawn),
  0,
  'a caregiver withdraws their own published board and nobody else''s'
);

with renamed as (
  update public.profiles set display_name = 'Robado'
  where id = :'bruno' returning 1
)
select is(
  (select count(*)::int from renamed),
  0,
  'a caregiver cannot rename anybody else'
);

select lives_ok(
  $$ update public.profiles set display_name = 'Ana'
     where id = '11111111-1111-1111-1111-111111111111' $$,
  'a caregiver sets their own display name'
);

select lives_ok(
  $$ insert into public.published_boards (id, author_id, name, language, color_argb, licence)
     values ('eeeeeeee-0000-0000-0000-000000000004',
             '11111111-1111-1111-1111-111111111111',
             'Cita médica', 'es', -1, 'CC-BY-NC-SA-4.0') $$,
  'publishing as yourself works'
);

select is(
  (select is_official from public.published_boards where id = :'ana_new_board'),
  false,
  'and what a caregiver publishes is never official'
);

select throws_ok(
  $$ insert into public.published_board_payloads (board_id, payload)
     values ('bbbbbbbb-0000-0000-0000-000000000002', '{"categories":[]}') $$,
  '42501',
  null,
  'a payload cannot be attached to a board you do not own'
);

with renamed as (
  update public.published_boards set name = 'Cita con el médico'
  where id = :'ana_new_board' returning 1
)
select is(
  (select count(*)::int from renamed),
  1,
  'an author edits their own published board'
);

with withdrawn as (
  update public.published_boards set withdrawn_at = now()
  where id = :'ana_new_board' returning 1
)
select is(
  (select count(*)::int from withdrawn),
  1,
  'and withdraws it — which is why an author keeps a select on their own withdrawn boards'
);

select is(
  (select count(*)::int from public.published_boards where id = :'ana_new_board'),
  1,
  'a withdrawn board stays visible to its author, so withdrawing is not a one-way door'
);

reset role;

-- ---------------------------------------------------------------------------
-- Account deletion anonymises rather than removes (#83)
-- ---------------------------------------------------------------------------

delete from auth.users where id = :'bruno';

select is(
  (select author_id from public.published_boards where id = :'community_board'),
  null::uuid,
  'deleting an account anonymises what it published instead of taking a board out from under the caregivers relying on it'
);

select is(
  (select count(*)::int from public.published_boards where id = :'community_board'),
  1,
  'the board itself survives the author'
);

select * from finish();
rollback;
