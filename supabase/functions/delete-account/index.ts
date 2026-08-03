// Deleting a caregiver's account, which is the one destructive thing the
// server can do (#83).
//
// It lives in an Edge Function for one reason: removing an `auth.users` row
// needs the secret key, and the secret key must never be inside the APK. The
// publishable key ships to every phone and can be extracted from any install,
// so anything it could reach is effectively public. This endpoint is the seam
// between "what a phone may ask for" and "what only the server may do".
//
// **The caller is taken from the verified JWT and from nowhere else.** The
// request body is never read -- not parsed, not consulted, not logged. That is
// what stops this becoming an endpoint for deleting other people's accounts,
// and it is deliberately a property of the code's shape rather than of a
// validation step somebody could later relax.
//
// What deletion means is decided by the schema, not here: `profiles.id`
// cascades from `auth.users`, and `published_boards.author_id` is
// `on delete set null`, so removing the user anonymises what they published
// instead of taking a board out from under the caregivers relying on it.
// Those consequences are asserted in `supabase/tests/database/account_deletion.test.sql`.
import { withSupabase } from 'npm:@supabase/server'

export default {
  fetch: withSupabase({ auth: 'user' }, async (_req, ctx) => {
    // Belt and braces: `auth: 'user'` has already rejected anything without a
    // valid JWT, so this is unreachable. It is here because the alternative to
    // an explicit check is a silent `undefined` reaching deleteUser.
    const id = ctx.userClaims?.id
    if (!id) {
      return Response.json({ error: 'unauthenticated' }, { status: 401 })
    }

    const { error } = await ctx.supabaseAdmin.auth.admin.deleteUser(id)
    if (error) {
      // Reported rather than swallowed. A caregiver who is told their account
      // is gone when it is not has been lied to about the one thing this
      // endpoint exists to guarantee.
      console.error('delete-account failed', { id, message: error.message })
      return Response.json({ error: 'delete_failed' }, { status: 500 })
    }

    return Response.json({ deleted: true })
  }),
}
