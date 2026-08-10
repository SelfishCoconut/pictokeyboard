// The behaviour behind every language's deletion page.
//
// One implementation, because there is exactly one thing this page does and a
// second copy of it would be a second thing to get wrong. Each page supplies
// its own wording through `window.PK_MESSAGES` before loading this; nothing
// user-facing is written here.
//
// The publishable key is public by design -- row-level security is the
// boundary, not secrecy -- but it is substituted at deploy time from a
// repository secret rather than committed, so a fork does not inherit this
// project's backend. See .github/workflows/pages.yml.
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.58.0'

const supabase = createClient('__SUPABASE_URL__', '__SUPABASE_PUBLISHABLE_KEY__')

const text = window.PK_MESSAGES
const $ = id => document.getElementById(id)
const statusBox = $('status')

function say(message, kind) {
  statusBox.textContent = message
  if (kind) statusBox.dataset.kind = kind
  else delete statusBox.dataset.kind
  statusBox.hidden = false
}

/**
 * Our sentence for a failure, never the server's.
 *
 * Supabase's messages are written in English and cannot be translated here, so
 * showing them puts an English sentence on the Spanish page. They also describe
 * the system rather than the person's next move: "over_email_send_rate_limit"
 * is true and useless.
 *
 * The rate limit is the one worth naming specifically. The project's built-in
 * mailer sends about two messages an hour (#92), and the code route is the
 * *only* route for someone who signed up with Google, since that account has no
 * password. Meeting a dead end there with no explanation is how somebody
 * concludes their account cannot be deleted at all — so every unrecognised
 * failure still ends by pointing at the email address, which needs no working
 * mailer of ours to reach.
 */
function describe(error) {
  switch (error?.code) {
    case 'over_email_send_rate_limit':
    case 'over_request_rate_limit':
      return text.tooManyRequests
    case 'otp_expired':
      return text.codeExpired
    case 'invalid_credentials':
      return text.wrongDetails
    default:
      return text.somethingWentWrong
  }
}

/**
 * Sign in with Google, without sending a single email.
 *
 * This is the route for an account created with "Continue with Google", which
 * has no password to type. It used to have only one way in -- a code emailed by
 * the project's built-in mailer, which delivers to project team addresses only
 * (#92). For every real caregiver that was a dead end on the one page Play
 * requires to work.
 *
 * OAuth needs no mailer at all, so this page now works for every account type
 * with the default email settings left exactly as they are.
 *
 * `redirectTo` is this page, minus any fragment: the caregiver comes back where
 * they were, in the language they were reading. Both language URLs have to be
 * allow-listed in the project -- see docs/owner-setup.md.
 */
$('google-btn').addEventListener('click', async () => {
  say(text.openingGoogle)
  const { error } = await supabase.auth.signInWithOAuth({
    provider: 'google',
    options: { redirectTo: location.href.split('#')[0].split('?')[0] },
  })
  // Only reached if the redirect itself could not be started; on success the
  // browser has already left this page.
  if (error) say(describe(error), 'error')
})

function showConfirm(user) {
  $('signin').hidden = true
  $('verify').hidden = true
  $('who').textContent = user?.email ?? text.yourAccount
  $('confirm').hidden = false
}

$('signin').addEventListener('submit', async event => {
  event.preventDefault()
  const email = $('email').value.trim()
  const password = $('password').value
  // An account created with "Continue with Google" has no password to type.
  // Saying so beats letting Supabase answer "invalid login credentials", which
  // reads as "you have the wrong password" and sends people hunting for one
  // that never existed.
  if (!password) {
    say(text.noPassword, 'error')
    return
  }
  say(text.signingIn)
  const { data, error } = await supabase.auth.signInWithPassword({ email, password })
  if (error) { say(describe(error), 'error'); return }
  statusBox.hidden = true
  showConfirm(data.user)
})

// A one-time code rather than a magic link: a code is typed into this page, so
// it needs no redirect URL allow-listed in the project, and it cannot strand
// someone on a page other than the one they were using.
$('code-btn').addEventListener('click', async () => {
  const email = $('email').value.trim()
  if (!email) { say(text.emailFirst, 'error'); return }
  say(text.sendingCode)
  const { error } = await supabase.auth.signInWithOtp({
    // Never create an account here. Without this, a typo would silently make a
    // new account on a page whose entire purpose is deleting one.
    email, options: { shouldCreateUser: false },
  })
  if (error) { say(describe(error), 'error'); return }
  // Deliberately does not confirm whether the address has an account: this page
  // is public, and answering that question turns it into a way to test which
  // addresses are registered.
  say(text.codeSent, 'ok')
  $('verify').hidden = false
  $('code').focus()
})

$('verify').addEventListener('submit', async event => {
  event.preventDefault()
  const email = $('email').value.trim()
  const token = $('code').value.trim()
  say(text.checkingCode)
  const { data, error } = await supabase.auth.verifyOtp({ email, token, type: 'email' })
  if (error) { say(describe(error), 'error'); return }
  statusBox.hidden = true
  showConfirm(data.user)
})

/**
 * Pick up the session Google just handed back.
 *
 * supabase-js parses the tokens out of the URL fragment as it initialises, so by
 * the time `getSession` resolves the session exists. The fragment is cleared
 * afterwards regardless: access tokens in an address bar get copied into
 * messages, restored from history and read over someone's shoulder, and this
 * particular session can destroy an account.
 *
 * Google can also come back refusing -- a closed consent screen arrives as
 * `error` in the query string rather than as a thrown error -- and saying
 * nothing would leave the caregiver looking at a form that appears to have
 * ignored them.
 */
async function resumeAfterRedirect() {
  const params = new URLSearchParams(location.search)
  const hash = new URLSearchParams(location.hash.slice(1))
  const refused = params.get('error') ?? hash.get('error')

  const { data } = await supabase.auth.getSession()
  if (location.hash || location.search) {
    history.replaceState(null, '', location.pathname)
  }

  if (data.session) {
    showConfirm(data.session.user)
  } else if (refused) {
    say(text.googleRefused, 'error')
  }
}

$('delete-btn').addEventListener('click', async () => {
  const button = $('delete-btn')
  button.disabled = true
  say(text.deleting)
  // No user id is sent, because there is none to send. The function takes the
  // caller from the verified JWT on this request and never reads the body, so
  // it cannot be asked to delete anybody else.
  const { error } = await supabase.functions.invoke('delete-account')
  if (error) {
    button.disabled = false
    say(text.deleteFailed, 'error')
    return
  }
  // Local on purpose. A server-side sign-out would try to revoke a session for
  // a user that no longer exists; failing there would report a failed deletion
  // after the account had already gone.
  await supabase.auth.signOut({ scope: 'local' })
  $('confirm').hidden = true
  say(text.deleted, 'ok')
})

resumeAfterRedirect()
