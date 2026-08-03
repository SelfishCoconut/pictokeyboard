-- What deleting an account does, and what it deliberately does not do (#83).
--
-- The claims here are about the database rather than about the Edge Function,
-- because the function only asks the database to remove one `auth.users` row --
-- everything after that is foreign keys and policies. If these fail, the
-- function being correct would not save us.
--
-- Run with: npx supabase test db

begin;
create extension if not exists pgtap with schema extensions;

select plan(10);

\set ana    '11111111-1111-1111-1111-111111111111'
\set bruno  '22222222-2222-2222-2222-222222222222'
\set ana_board   'aaaaaaaa-0000-0000-0000-000000000011'
\set bruno_board 'bbbbbbbb-0000-0000-0000-000000000012'

insert into auth.users (id, instance_id, aud, role, email, created_at, updated_at)
values
  (:'ana',   '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'ana@example.test',   now(), now()),
  (:'bruno', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'bruno@example.test', now(), now());

insert into public.published_boards
  (id, author_id, name, language, tags, color_argb, licence, is_official)
values
  (:'ana_board',   :'ana',   'Primeras palabras', 'es', '{topic_food}',   -14405057, 'CC-BY-NC-SA-4.0', false),
  (:'bruno_board', :'bruno', 'Día de colegio',    'es', '{place_school}', -14405057, 'CC-BY-NC-SA-4.0', false);

insert into public.published_board_payloads (board_id, payload)
values
  (:'ana_board',   '{"categories":[{"name":"Comida","pictos":[{"label":"agua","arasaacId":2248}]}]}'),
  (:'bruno_board', '{"categories":[{"name":"Clase","pictos":[{"label":"profesor","arasaacId":6009}]}]}');

-- ---------------------------------------------------------------------------
-- Withdrawal, as a transition rather than a seeded state
--
-- The catalogue tests already assert that a board withdrawn at seed time is
-- unreadable. What matters to a caregiver pressing the button is that the
-- payload stops being readable *at the moment they press it*, so this withdraws
-- a live board mid-test and re-reads it.
-- ---------------------------------------------------------------------------

set local role authenticated;
set local request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}';

select is(
  (select count(*)::int from public.published_board_payloads where board_id = :'ana_board'),
  1,
  'before anything is withdrawn, a signed-in caregiver can download a live community board'
);

-- Note the shape of this one. RLS *filters* rows; it does not raise. Trying to
-- withdraw somebody else's board is not an error, it is an update that matches
-- nothing -- so the client must count rows to know it worked. Asserting a
-- thrown 42501 here fails, and the app treating "no exception" as "done" would
-- tell a caregiver their boards were withdrawn when nothing had happened.
with attempted as (
  update public.published_boards set withdrawn_at = now()
  where id = 'aaaaaaaa-0000-0000-0000-000000000011'
  returning 1
)
select is(
  (select count(*)::int from attempted),
  0,
  'withdrawing somebody else''s board changes nothing -- the same boundary that stops one caregiver deleting another''s account'
);

select is(
  (select withdrawn_at from public.published_boards where id = :'ana_board'),
  null::timestamptz,
  'and the board it failed to touch is still live'
);

reset role;
set local role authenticated;
set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}';

update public.published_boards set withdrawn_at = now() where id = :'ana_board';

select is(
  (select count(*)::int from public.published_board_payloads where board_id = :'ana_board'),
  0,
  'the moment a board is withdrawn its payload stops downloading, for its own author too'
);

reset role;
set local role anon;

select is(
  (select count(*)::int from public.published_board_payloads where board_id = :'ana_board'),
  0,
  'and it stops downloading signed out, which is the half a hostile client would try'
);

reset role;

-- ---------------------------------------------------------------------------
-- Deleting the account
--
-- Bruno's board stays live throughout: the point of #83 is that deleting an
-- account does not take a board out from under the caregivers relying on it.
-- ---------------------------------------------------------------------------

delete from auth.users where id = :'bruno';

select is(
  (select count(*)::int from public.profiles where id = :'bruno'),
  0,
  'the profile row goes with the user, so the display name that was the personal data is gone'
);

select is(
  (select author_id from public.published_boards where id = :'bruno_board'),
  null::uuid,
  'the published board is anonymised rather than removed -- under GDPR the personal data here is the author name, not the vocabulary'
);

set local role authenticated;
set local request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}';

-- The assertion nothing else makes. Anonymising nulls author_id, so any future
-- author_id clause added to the payload policy would silently make every
-- anonymised board undownloadable -- a board that is listed, looks installable,
-- and fails at the last step.
select is(
  (select count(*)::int from public.published_board_payloads where board_id = :'bruno_board'),
  1,
  'an anonymised board is still installable -- losing its author must not quietly cost it its payload'
);

select is(
  (select count(*)::int from public.published_boards where id = :'bruno_board'),
  1,
  'and it is still listed in the catalogue'
);

reset role;

select is(
  (select count(*)::int from public.published_boards where id = :'ana_board'),
  1,
  'deleting one account leaves every other author untouched'
);

select * from finish();
rollback;
