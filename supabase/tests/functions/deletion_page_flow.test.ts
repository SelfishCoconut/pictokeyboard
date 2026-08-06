// What the web deletion page does, run against a real stack (#83).
//
// site/delete-account/index.html is the deletion route Play requires to work
// without the app installed, and it has no other coverage: there is no browser
// in CI, and a static page has nothing to unit test. So this runs the page's
// exact call sequence, through the same `esm.sh` import specifier the page
// uses, and asserts the same outcomes.
//
// It catches the failures a static page fails at *runtime* for: a renamed
// method, a wrong option shape, a wrong OTP `type`. Every one of those is
// invisible until a caregiver meets it, at the moment they are trying to
// delete their account.
//
// Kept in step with the page by hand -- if the page's calls change, change
// these. Run:  supabase start && deno run --allow-net --allow-env \
//   supabase/tests/functions/deletion_page_flow.test.ts
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.58.0'

const API = Deno.env.get('API')!
const PUBLISHABLE = Deno.env.get('PUBLISHABLE')!
const SECRET = Deno.env.get('SECRET')!

let pass = 0, fail = 0
const check = (desc: string, ok: boolean, detail = '') => {
  if (ok) { console.log(`ok   - ${desc}`); pass++ }
  else { console.log(`FAIL - ${desc} ${detail}`); fail++ }
}

const admin = (path: string, init: RequestInit = {}) =>
  fetch(`${API}/auth/v1${path}`, {
    ...init,
    headers: {
      apikey: SECRET,
      Authorization: `Bearer ${SECRET}`,
      'Content-Type': 'application/json',
      ...(init.headers ?? {}),
    },
  })

const mkuser = async (email: string, password?: string) => {
  const body: Record<string, unknown> = { email, email_confirm: true }
  if (password) body.password = password
  const r = await admin('/admin/users', { method: 'POST', body: JSON.stringify(body) })
  return (await r.json()).id as string
}

const exists = async (id: string) =>
  (await admin(`/admin/users/${id}`)).status === 200

const stamp = Date.now()

// ---------------------------------------------------------------- password
{
  const email = `page-pw-${stamp}@example.test`
  const id = await mkuser(email, 'correct-horse-battery')
  const supabase = createClient(API, PUBLISHABLE)

  const { data, error } = await supabase.auth.signInWithPassword({
    email, password: 'correct-horse-battery',
  })
  check('the page\'s password sign-in works', !error && !!data.user, error?.message ?? '')
  check('it shows the address it signed in as', data.user?.email === email)

  const { error: fnError } = await supabase.functions.invoke('delete-account')
  check('the page\'s delete call succeeds', !fnError, fnError?.message ?? '')

  // The exact option shape the page uses. A wrong one throws at runtime, after
  // the account is already gone -- reporting a failure for something that
  // succeeded.
  await supabase.auth.signOut({ scope: 'local' })
  check('the page\'s local sign-out does not throw', true)

  check('the account is really gone', !(await exists(id)))
}

// -------------------------------------------------------------------- code
// The route a caregiver who signed up with Google has to use, since that
// account has no password. The admin API hands back the same 6-digit code the
// email would carry, so this needs no mail server.
{
  const email = `page-otp-${stamp}@example.test`
  const id = await mkuser(email)
  const supabase = createClient(API, PUBLISHABLE)

  const { error: otpError } = await supabase.auth.signInWithOtp({
    email, options: { shouldCreateUser: false },
  })
  check('the page can request a code for a passwordless account', !otpError, otpError?.message ?? '')

  const linkResponse = await admin('/admin/generate_link', {
    method: 'POST',
    body: JSON.stringify({ type: 'magiclink', email }),
  })
  const { email_otp: token } = await linkResponse.json()
  check('a 6-digit code is what arrives', /^\d{6}$/.test(token ?? ''), `got '${token}'`)

  const { data, error } = await supabase.auth.verifyOtp({ email, token, type: 'email' })
  check('the page\'s verifyOtp accepts that code', !error && !!data.user, error?.message ?? '')

  const { error: fnError } = await supabase.functions.invoke('delete-account')
  check('deleting works over the code route too', !fnError, fnError?.message ?? '')
  check('that account is gone as well', !(await exists(id)))
}

// ------------------------------------------------- it cannot create accounts
{
  const email = `page-never-${stamp}@example.test`
  const supabase = createClient(API, PUBLISHABLE)
  await supabase.auth.signInWithOtp({ email, options: { shouldCreateUser: false } })

  const list = await admin(`/admin/users?filter=${encodeURIComponent(email)}`)
  const users = (await list.json()).users ?? []
  check('asking for a code never creates an account', users.length === 0,
    `found ${users.length}`)
}

console.log('---')
console.log(`${pass} passed, ${fail} failed`)
Deno.exit(fail === 0 ? 0 : 1)
