# The server

Everything PictoKeyboard keeps outside the phone. It is a short list on purpose.

| Table | Holds |
|---|---|
| `auth.users` | Supabase's own — email, password hash, Google identity |
| `profiles` | a public display name, so a published board can name its author |
| `published_boards` | catalogue metadata: name, tags, language, picto, licence, counts |
| `published_board_payloads` | the published board itself, as one `jsonb` |

**No personal boards. No categories. No pictos. No settings. No usage
statistics. No photographs.** A caregiver's own vocabulary stays on their device
and travels only in a `.pkb` file they export themselves (#88). If it is not in
that table list, it is not on the server.

## The rule everything is built on

The publishable key ships inside the APK. Anyone can extract it and query
PostgREST directly, so **row-level security is the only boundary there is**.
Every access rule has to be a policy or a privilege. A hidden button is an
explanation, never a boundary.

| Action | Signed out | Signed in |
|---|---|---|
| Browse the catalogue | **Yes** | Yes |
| Download an **official** board (one we publish) | **Yes** | Yes |
| Download a **community** board | **No** | Yes |
| Publish a board | **No** | Yes |
| Withdraw or edit your own | **No** | Yes (own only) |

*Seeing is not downloading* is why the metadata and the payload are two tables
with two policies rather than one table and a column the client is trusted not
to read.

## Working on it

The CLI is not installed; `npx` fetches it.

```sh
# Start Postgres and apply every migration. Nothing but the database starts —
# pgTAP connects to it directly, so PostgREST, Auth and the rest are dead weight.
npx supabase start -x gotrue,realtime,storage-api,imgproxy,kong,mailpit,\
postgrest,postgres-meta,studio,edge-runtime,logflare,vector,supavisor

npx supabase test db      # the policies, proved
npx supabase db reset     # re-apply migrations from scratch after an edit
npx supabase stop
```

`npx supabase start` with no `-x` brings up the whole stack, including Studio on
<http://127.0.0.1:54323>, which is worth it when you want to click around.

### Changing the schema

Migrations are the only way. Add a file to `migrations/`, `db reset`, and extend
`tests/database/` — a policy nobody exercised is a policy nobody has read
carefully. CI runs `supabase test db` on **every** pull request and it is a
required check, because this is where the security lives.

When you add a test, prove it can fail: break the policy it covers, watch the
test go red, then put it back. Both of the policies in the first migration were
checked that way.

### Deploying

```sh
npx supabase link --project-ref <ref>   # asks for the database password
npx supabase db push
```

## Keys

Two keys, and the difference matters.

- **`sb_publishable_…`** is public by design. It goes in `local.properties` and
  is compiled into the APK. Leaking it costs nothing, which is the entire reason
  the policies above have to be real.
- **`sb_secret_…`** bypasses every policy in this directory. It must never enter
  this repository, `local.properties`, an APK, or a log line. It belongs in
  Supabase's own secrets and in nothing else. If one is ever pasted somewhere it
  should not be, rotate it — do not reason about who might have seen it.

Official boards are seeded with the secret key, which is precisely why no client
can create one: there is no grant on `is_official` for `anon` or `authenticated`
at all.

Spec: `docs/superpowers/specs/2026-08-03-marketplace-and-local-backup-design.md`
