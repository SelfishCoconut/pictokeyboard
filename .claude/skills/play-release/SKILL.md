---
name: play-release
description: Prepare and verify a Google Play release — build config, signing, Data Safety and privacy policy, store listing, target-API compliance, and the track rollout. Use for "release to Play", "prepare a Play upload", "am I Play compliant", "publish the app".
---

# Google Play release

Play rejections are almost never about code quality. They are about declarations that do not match the binary, a missing policy document, or a target-API floor. Work through this in order; the automated part (`playstore-check.yml`) covers roughly the first section and nothing after it.

## 1. Build configuration (automatable)

- **`targetSdk` meets Play's current floor.** Play enforces a rolling minimum for new apps and for updates, and raises it every year. Check the current requirement before assuming — an out-of-date `targetSdk` blocks upload outright, and the behavior changes that come with a bump need real testing, so do it early and in its own change.
- **`versionCode` strictly increases** on every upload. Play rejects a re-used code permanently — that number is burned even if the upload failed. Derive it from the release tag or a monotonic counter, never hand-edit it.
- **Release signing configured** and pointing at the upload keystore, not debug. A debug-signed artifact is rejected.
- **`isMinifyEnabled = true`** with `shrinkResources`. Then verify the minified build actually runs: reflection-based libraries (Room, Moshi/Gson, Retrofit, anything with `@Keep`-worthy models) break *silently* under R8 without keep rules. **Test the release variant on a device before uploading** — this is the single most common "worked in debug, crashed in production" failure.
- **`android:debuggable`** absent from the release manifest, and no `usesCleartextTraffic="true"`.
- Build an **AAB** (`bundleRelease`) — Play requires App Bundles for new apps.

## 2. Permissions and Data Safety (the section that gets apps rejected)

Enumerate what the app actually does, from the code, not from memory:

```sh
grep -rn "uses-permission" app/src/main/AndroidManifest.xml
```

For each permission: is it still used? Every unused permission is a declaration you have to justify and a reason for reviewers to look harder.

Then fill the **Data Safety** form so it matches the binary:

- Every network call is a data flow. Trace them (Retrofit interfaces, OkHttp clients, any analytics or crash SDK) and declare what leaves the device, why, whether it is linked to identity, and whether the user can request deletion.
- Third-party SDKs collect data **on your behalf** and you are responsible for declaring it. An analytics or ads SDK you added without reading its data practices is still your declaration.
- If the form and the binary disagree, that is a policy violation, not a paperwork error — and it is discovered by automated scanning, reliably.

### Some app categories get extra scrutiny

If the app can observe or act on data outside itself, expect the strictest review tier and a slower turnaround. This covers input methods, accessibility services, VPN and device-admin apps, screen readers, call/SMS handlers, and anything requesting `QUERY_ALL_PACKAGES` or all-files access.

For these:

- State plainly what data the component can observe and whether any of it leaves the device. If the answer is "none", say so in the listing and policy, and make sure it is *true* — including any crash reporter that might capture a buffer of user content.
- Use the platform's privacy signals rather than working around them (for an IME, honor `IME_FLAG_NO_PERSONALIZED_LEARNING` and never persist password-field input; for an accessibility service, request only the event types you use).
- Accessibility APIs must be used for actual accessibility purposes. Using an accessibility service for automation or convenience features is a policy violation and a common removal reason.
- Be ready to answer "why does this app need this?" for **every** permission, in writing.

## 3. Documents you must have

- **Privacy policy** at a public, stable URL — required for every app, no exceptions. It must describe actual behavior, name any third-party recipients, and give a contact.
- **Account deletion policy** if the app has accounts.
- **Attribution / third-party licenses** for bundled assets. Check the asset licence terms permit distribution through an app store, and that required attribution appears somewhere the user can reach.

## 4. Store listing

App name, short and full description, icon (512x512), feature graphic (1024x500), and screenshots for every form factor you declare support for. Content rating questionnaire. Target audience and content settings — if the app plausibly appeals to children, the Families policy adds requirements, so answer this deliberately.

Declare the app's category honestly; accessibility and assistive apps often belong in a specific one and it affects discovery.

## 5. Rollout

Never straight to production:

1. **Internal testing** — up to 100 testers, available in minutes. Install from Play (not `adb`) and confirm the *signed, minified, Play-delivered* artifact works. This catches R8 and signing problems that no local build will.
2. **Closed testing** — a wider group. New personal-developer accounts have a mandatory closed-testing period with a tester minimum before production access; check the current requirement early, because it is measured in weeks and will set your timeline.
3. **Production**, staged rollout starting small. Watch crash rate and ANR rate in the Play console vitals before advancing.

## 6. Before you press upload

Confirm, out loud, each of: targetSdk floor met; versionCode fresh; release-signed AAB; minified build tested on a device; permissions minimal and justified; Data Safety matches the binary; privacy policy live at its URL; attributions present; listing assets complete.

Anything you have not verified, say you have not verified. An unchecked box here costs days of review turnaround, and a policy strike is far more expensive than a delay.
