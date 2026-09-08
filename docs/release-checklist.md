# Pre-Field-Test Release Checklist

Scope: what's needed to get Easy Radio onto Play Console's **internal testing track**
for a small group of trusted testers. Not a production/public-release checklist.

## 1. Crash visibility — Play Console vitals

Play Console vitals (crash rate, ANR rate, stack traces) require **no app code** —
they're automatic once a build lands on any Play Console track. The only prerequisite
is getting a signed release build onto that track at all, which meant fixing signing:

- [x] `app/build.gradle.kts` now defines a `release` `signingConfig` that reads from
      `keystore.properties` (gitignored) when present, and falls back to unsigned
      (today's behavior) when it's absent — no regression for anyone without the keystore.
- [ ] Generate the upload keystore locally (not done by Claude — key generation was
      blocked by the auto-mode safety classifier as a sensitive action):
  ```
  keytool -genkeypair -v -keystore release-upload-key.jks -alias easy-radio-upload \
    -keyalg RSA -keysize 2048 -validity 10000
  ```
  Follow the prompts for store password, key password, and identity fields. **Back up
  the `.jks` file and both passwords somewhere durable (password manager)** — losing
  them means going through Google's upload-key-reset process later.
- [ ] Create `keystore.properties` at the repo root (gitignored) with:
  ```properties
  storeFile=release-upload-key.jks
  storePassword=<store password>
  keyAlias=easy-radio-upload
  keyPassword=<key password>
  ```
- [ ] Confirm a signed bundle builds: `./gradlew bundleRelease` — the AAB lands in
      `app/build/outputs/bundle/release/`.
- [ ] Enroll in **Play App Signing** when creating the app in Play Console (Google
      manages the real signing key; the keystore above is only the *upload* key).
- [ ] `versionCode` must strictly increase on every future upload — currently `1`.

### Play Console account/app setup (outside the repo)
- [ ] One-time $25 developer registration, if not already done.
- [ ] Create the app entry, fill the (short, since this is internal testing only)
      Data Safety form — currently no accounts, no analytics, no ad SDKs, so this
      should be quick and honest as "no data collected."
- [ ] Content rating questionnaire.
- [ ] Add testers by email or Google Group; they get an opt-in link and install
      through the real Play Store — no APK sideloading needed.
- [ ] Once testers are on the track, vitals data (crash/ANR rate + stack traces)
      appears in Play Console automatically, with some delay (not real-time).

## 2. Nice-to-have for production (not needed for internal testing): Crashlytics

Play Console vitals are aggregated and delayed, and depend on testers actually
reporting what they hit. Firebase Crashlytics gives per-crash stack traces the
moment they happen, without waiting on a tester to notice or describe the symptom —
which matters more once the app has a wider, less hands-on audience than a small
group of trusted testers.

**Defer to production.** Rough scope when it's time: add `google-services.json`,
the Crashlytics Gradle plugin + Firebase BOM, one `FirebaseCrashlytics.getInstance()`
init call in `MainActivity`/`Application`. Estimated 30–60 minutes.

## 3. Compose reactivity audit

Done as part of this same pass (see the pagination stall fix in
`PodcastsScreen.kt` for context on the specific bug class): audited every
`derivedStateOf`, every bare `remember {}` block, and every `LaunchedEffect` in
the app and wear modules for the "closure captures a stale, non-live value" and
"effect key gets flipped by its own body mid-flight" patterns. No other instances
found — the pagination bug was a one-off, not a pattern repeated elsewhere.
