-- The published-board catalogue.
--
-- Three tables and the policies that decide who may read them. There are no
-- personal boards here and there never will be: a caregiver's own vocabulary
-- stays on their device and travels only in a .pkb file they export themselves
-- (#88). What the server holds is accounts and the marketplace.
--
-- The one rule everything below is built on: **row-level security is the only
-- boundary there is.** The publishable key ships inside the APK, so anyone can
-- extract it and query PostgREST directly. Every rule here has to hold against
-- a hostile client holding a valid key, which means it has to be a policy or a
-- privilege and can never be a hidden button.
--
-- Spec: docs/superpowers/specs/2026-08-03-marketplace-and-local-backup-design.md

-- ---------------------------------------------------------------------------
-- Controlled vocabularies
-- ---------------------------------------------------------------------------

-- Tags are a fixed list, not free text — free text fragments on contact with
-- users and stops filtering anything within a week. The facet is part of the
-- id so the client can group them and AND across facets without a second
-- table. Adding a tag is a migration, which is what "controlled" means.
create type public.board_tag as enum (
  -- Place
  'place_home', 'place_school', 'place_hospital', 'place_shop',
  'place_restaurant', 'place_outdoors', 'place_transport',
  -- People
  'people_family', 'people_friends', 'people_teacher', 'people_carer',
  'people_doctor',
  -- Situation
  'situation_mealtime', 'situation_bedtime', 'situation_play',
  'situation_appointment', 'situation_shopping', 'situation_travel',
  'situation_emergency',
  -- Topic
  'topic_food', 'topic_feelings', 'topic_body', 'topic_animals',
  'topic_clothes', 'topic_colours', 'topic_numbers', 'topic_time'
);

-- Licences are derived from a board's contents rather than chosen (#38). A
-- board carrying ARASAAC symbols is CC BY-NC-SA and cannot be published as
-- anything looser.
create type public.board_licence as enum (
  'CC-BY-NC-SA-4.0',
  'CC-BY-SA-4.0',
  'CC-BY-4.0',
  'CC0-1.0'
);

-- ---------------------------------------------------------------------------
-- profiles
-- ---------------------------------------------------------------------------

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,

  -- Deliberately nullable, and deliberately never derived from the email
  -- address or from the name Google hands us.
  --
  -- This column is public — it is what a published board shows as its author.
  -- Defaulting it to the local part of an email would turn
  -- maria.garcia.lopez@example.com into a public byline the caregiver never
  -- chose, and signing in with Google would silently publish a real name. So
  -- it starts null, a null author reads as "Anonymous", and the publish flow
  -- (#41) is what asks for a name and says out loud that it will be public.
  display_name text
    constraint display_name_is_reasonable
    check (char_length(btrim(display_name)) between 1 and 60),

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.profiles is
  'Public authorship for published boards. Holds no personal board data.';

-- ---------------------------------------------------------------------------
-- published_boards — catalogue metadata
-- ---------------------------------------------------------------------------

create table public.published_boards (
  id uuid primary key default gen_random_uuid(),

  -- Nullable *only* so account deletion can null it later (#83): a published
  -- board is anonymised rather than removed, because deleting it would take a
  -- board out from under caregivers relying on it, and under GDPR the personal
  -- data in a published board is the author's name and not the vocabulary.
  --
  -- The insert policy is what stops it being null at creation. A nullable
  -- column with no insert policy would let anyone publish as nobody.
  author_id uuid references public.profiles (id) on delete set null,

  name text not null
    constraint name_is_reasonable
    check (char_length(btrim(name)) between 1 and 80),
  description text
    constraint description_is_reasonable
    check (char_length(description) <= 500),

  -- The language of the board's vocabulary, which is not the caregiver's
  -- interface language: a caregiver may well run the app in Spanish while
  -- building an English board for school.
  language text not null
    constraint language_is_bcp47_ish
    check (language ~ '^[a-z]{2}(-[A-Z]{2})?$'),

  -- Capped rather than deduplicated: a repeated tag is cosmetic and the
  -- containment operators the Discover filter uses do not care, whereas
  -- enforcing distinctness in a check constraint needs a subquery, which
  -- Postgres will not allow.
  tags public.board_tag[] not null default '{}'
    constraint tags_are_few
    check (coalesce(array_length(tags, 1), 0) <= 8),

  -- The board's own picto. An ARASAAC id, never an image: published boards
  -- carry no photographs (see published_board_payloads).
  icon_arasaac_id integer
    constraint icon_arasaac_id_is_positive
    check (icon_arasaac_id > 0),

  -- ARGB, matching BoardEntity.colorArgb. Postgres integer and Kotlin Int are
  -- both signed 32-bit, so 0xFF24303F round-trips as the same negative number
  -- on both sides without a conversion anyone has to remember.
  color_argb integer not null,

  licence public.board_licence not null,

  -- Derived from the payload by a trigger, never written by a client — see
  -- set_published_board_counts below.
  category_count integer not null default 0,
  picto_count integer not null default 0,

  -- Official boards are the ones we publish. They are the signed-out on-ramp:
  -- someone who has just installed the app must be able to get a working board
  -- immediately, with no account and no typing. No client can set this column —
  -- there is no grant for it — so official boards can only be seeded with the
  -- secret key, which never leaves the server.
  is_official boolean not null default false,

  withdrawn_at timestamptz,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.published_boards is
  'Catalogue metadata. Readable while signed out — browsing is open, downloading is not.';

-- The Discover list is the only read path that matters: filter by tag and
-- language among the boards that are still live.
create index published_boards_live_idx
  on public.published_boards (language, is_official, created_at desc)
  where withdrawn_at is null;

create index published_boards_tags_idx
  on public.published_boards using gin (tags)
  where withdrawn_at is null;

create index published_boards_author_idx
  on public.published_boards (author_id)
  where withdrawn_at is null;

-- ---------------------------------------------------------------------------
-- published_board_payloads — what a download actually fetches
-- ---------------------------------------------------------------------------

create table public.published_board_payloads (
  board_id uuid primary key
    references public.published_boards (id) on delete cascade,

  payload_version integer not null default 1
    constraint payload_version_is_known
    check (payload_version = 1),

  payload jsonb not null,

  -- A published board contains ARASAAC ids and text. Never a photo, never a
  -- recording. This is a firm rule rather than a default, because the photos in
  -- a caregiver's board are of a real child, their real teacher, their real
  -- classroom door — and the caregiver who took the photo is very often not the
  -- person who could consent to it being published. Publishing is irreversible.
  --
  -- The publish flow strips them and says it has (#41). This constraint is what
  -- makes that true even when the client is not ours: the keys must be absent
  -- from the payload entirely, at any depth, not merely set to null.
  constraint payload_carries_no_media check (
    not jsonb_path_exists(payload, '$.**.imagePath')
    and not jsonb_path_exists(payload, '$.**.iconImagePath')
    and not jsonb_path_exists(payload, '$.**.audioPath')
    and not jsonb_path_exists(payload, '$.**.media')
  ),

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.published_board_payloads is
  'The board graph itself. Anon may read this only for official boards — this is "seeing is not downloading".';

-- ---------------------------------------------------------------------------
-- Triggers
-- ---------------------------------------------------------------------------

create function public.touch_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger profiles_touch_updated_at
  before update on public.profiles
  for each row execute function public.touch_updated_at();

create trigger published_boards_touch_updated_at
  before update on public.published_boards
  for each row execute function public.touch_updated_at();

create trigger published_board_payloads_touch_updated_at
  before update on public.published_board_payloads
  for each row execute function public.touch_updated_at();

-- Every account gets a profile row the moment it exists, so a published board
-- always has somewhere to point. The name starts null on purpose — see the
-- column comment.
create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id)
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Counts are shown in the Discover list, so they have to be readable while
-- signed out — but they describe the payload, which is not. Deriving them here
-- keeps the two from drifting and means a client cannot advertise a board as
-- larger than it is. There is no grant on either column, so this trigger is the
-- only thing that writes them.
--
-- The payload size cap lives here rather than in a check constraint because a
-- stated limit belongs with a message, and because 512 KB of text is already a
-- board far larger than anyone builds by hand.
create function public.set_published_board_counts()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  categories integer;
  pictos integer;
begin
  if octet_length(new.payload::text) > 524288 then
    raise exception 'Published board payload is larger than 512 KB'
      using errcode = 'check_violation';
  end if;

  select
    coalesce(jsonb_array_length(new.payload -> 'categories'), 0),
    coalesce(
      (
        select sum(coalesce(jsonb_array_length(c -> 'pictos'), 0))
        from jsonb_array_elements(
          coalesce(new.payload -> 'categories', '[]'::jsonb)
        ) as c
      ),
      0
    )
  into categories, pictos;

  update public.published_boards
     set category_count = categories,
         picto_count = pictos
   where id = new.board_id;

  return new;
end;
$$;

create trigger published_board_payloads_set_counts
  after insert or update of payload on public.published_board_payloads
  for each row execute function public.set_published_board_counts();

-- ---------------------------------------------------------------------------
-- Privileges
--
-- New tables are not exposed to the Data API roles without an explicit grant.
-- These are written column by column on purpose: the columns a client must not
-- write — is_official above all — are simply absent, which is a harder boundary
-- than a policy that has to be read carefully to be believed.
-- ---------------------------------------------------------------------------

grant select on public.profiles to anon, authenticated;
grant update (display_name) on public.profiles to authenticated;

grant select on public.published_boards to anon, authenticated;
grant insert (id, author_id, name, description, language, tags,
              icon_arasaac_id, color_argb, licence)
  on public.published_boards to authenticated;
grant update (name, description, tags, icon_arasaac_id, color_argb,
              withdrawn_at)
  on public.published_boards to authenticated;
grant delete on public.published_boards to authenticated;

grant select on public.published_board_payloads to anon, authenticated;
grant insert (board_id, payload_version, payload)
  on public.published_board_payloads to authenticated;
grant update (payload_version, payload)
  on public.published_board_payloads to authenticated;
grant delete on public.published_board_payloads to authenticated;

-- ---------------------------------------------------------------------------
-- Row-level security
--
-- auth.uid() is wrapped in a scalar subquery throughout so the planner
-- evaluates it once per statement rather than once per row.
--
-- force row level security is deliberately *not* used: the two triggers above
-- run as the table owner precisely because a client must not be able to do what
-- they do, and forcing RLS on the owner would break them.
-- ---------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.published_boards enable row level security;
alter table public.published_board_payloads enable row level security;

-- profiles ------------------------------------------------------------------

-- Only authors of live boards are visible, so the table is not a register of
-- everyone who ever signed up. A display name is public because it is a byline,
-- not because the account exists.
create policy "Authors of live boards are public"
  on public.profiles for select
  to anon, authenticated
  using (
    exists (
      select 1 from public.published_boards b
      where b.author_id = profiles.id
        and b.withdrawn_at is null
    )
  );

create policy "A caregiver can always read their own profile"
  on public.profiles for select
  to authenticated
  using ((select auth.uid()) = id);

create policy "A caregiver changes only their own display name"
  on public.profiles for update
  to authenticated
  using ((select auth.uid()) = id)
  with check ((select auth.uid()) = id);

-- No insert policy: profiles are created by the signup trigger, so a client
-- cannot manufacture one. No delete policy: they go when the account does.

-- published_boards ----------------------------------------------------------

-- Browsing is open. This is the whole point of the split: a signed-out
-- caregiver sees the entire community catalogue, which is what makes signing up
-- worth doing.
create policy "The live catalogue is public"
  on public.published_boards for select
  to anon, authenticated
  using (withdrawn_at is null);

create policy "An author can see their own withdrawn boards"
  on public.published_boards for select
  to authenticated
  using ((select auth.uid()) = author_id);

-- Signed in, and only as yourself. is_official is unreachable because there is
-- no grant on the column, so a caregiver cannot publish a board and hand it to
-- every signed-out user.
create policy "Publishing requires an account, and only as yourself"
  on public.published_boards for insert
  to authenticated
  with check ((select auth.uid()) = author_id);

create policy "An author edits only their own published boards"
  on public.published_boards for update
  to authenticated
  using ((select auth.uid()) = author_id)
  with check ((select auth.uid()) = author_id);

create policy "An author withdraws only their own published boards"
  on public.published_boards for delete
  to authenticated
  using ((select auth.uid()) = author_id);

-- published_board_payloads --------------------------------------------------

-- This is the policy that implements "seeing is not downloading", and the one
-- to read twice in review.
--
-- Signed out, a caregiver may fetch the payload of an official board and no
-- other. The community catalogue is fully visible next to it and completely
-- unreadable, which is the intended shape rather than an oversight.
create policy "Signed out, only official boards can be downloaded"
  on public.published_board_payloads for select
  to anon
  using (
    exists (
      select 1 from public.published_boards b
      where b.id = published_board_payloads.board_id
        and b.is_official
        and b.withdrawn_at is null
    )
  );

create policy "Signed in, any live board can be downloaded"
  on public.published_board_payloads for select
  to authenticated
  using (
    exists (
      select 1 from public.published_boards b
      where b.id = published_board_payloads.board_id
        and b.withdrawn_at is null
    )
  );

create policy "A payload can only be attached to your own board"
  on public.published_board_payloads for insert
  to authenticated
  with check (
    exists (
      select 1 from public.published_boards b
      where b.id = published_board_payloads.board_id
        and b.author_id = (select auth.uid())
    )
  );

create policy "An author replaces only their own payloads"
  on public.published_board_payloads for update
  to authenticated
  using (
    exists (
      select 1 from public.published_boards b
      where b.id = published_board_payloads.board_id
        and b.author_id = (select auth.uid())
    )
  )
  with check (
    exists (
      select 1 from public.published_boards b
      where b.id = published_board_payloads.board_id
        and b.author_id = (select auth.uid())
    )
  );

create policy "An author deletes only their own payloads"
  on public.published_board_payloads for delete
  to authenticated
  using (
    exists (
      select 1 from public.published_boards b
      where b.id = published_board_payloads.board_id
        and b.author_id = (select auth.uid())
    )
  );
