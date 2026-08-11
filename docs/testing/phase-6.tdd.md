# TDD Evidence Report — Phase 6 (Wear OS Companion)

**Source plan**: [`docs/implementation-plan.md`](../implementation-plan.md) (Phase 6)
**Date**: 2026-08-09
**Scope**: A Wear OS companion app that **remote-controls the phone's playback** over the Wearable Data Layer (the chosen Phase 6 scope — not a standalone player). A shared `WearSync` protocol, the `:wear` Compose app, and the phone-side listener + now-playing publisher.

## User Journeys

1. As a driver, I want to open Easy Radio on my watch and tap a station to start it playing on the phone.
2. As a driver, I want to play/pause and skip from the watch and have it affect phone playback.
3. As a driver, I want the watch to show what the phone is currently playing (title, subtitle, playing state).
4. As a maintainer, I want the watch and phone builds to share one wire format so they can never silently disagree.
5. As any user, I want normal phone playback to keep working even with no watch paired / no Wear support on the device.

## Task Report

| Task | Summary | Validation command | Result |
|---|---|---|---|
| `WearSync` protocol | Shared, serializable contract in `:core:model`: `WearCommand` (Play/Pause/SkipForward/SkipBack/PlayStation) + `NowPlayingState`, encoded to compact JSON bytes with a `type` discriminator, plus the MessageClient paths | `./gradlew :core:model:test` | 4/4 PASS |
| `:wear` module | Compose-for-Wear app (`applicationId = com.easyradio.app` so the Data Layer routes to the phone). `WearMediaClient` sends commands to connected nodes and exposes phone-pushed state as a `StateFlow`; `MainActivity` renders now-playing + play/pause/skip + a station picker | `./gradlew :wear:assembleDebug` | BUILD SUCCESSFUL |
| Phone command receiver | `PhoneWearListenerService` (`WearableListenerService`) decodes `WearCommand` at `/easyradio/command` and applies it via a short-lived `MediaController` on the app main looper (Play/Pause, `SeekMath` skip, `PlayStation` with title/tagline metadata) | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| Phone state publisher | `WearStatePublisher`, attached to the session's ExoPlayer, pushes `NowPlayingState` (metadata title/subtitle, `isPlaying`, `canSkip` from `isCurrentMediaItemSeekable`) to connected nodes on every player event | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| Crash hardening | Wrap both Data Layer paths in `runCatching` so an unavailable Wearable API can never crash phone playback (caught on-device — see below) | on-device (phone emulator) | 0 FATAL after fix |
| On-device verification | Phone + Wear OS 5.1 emulators: both apps install/launch/render; the cross-device transport is deferred to the Studio pairing assistant | `adb` + `dumpsys` + `screencap` | see below |

## Test Specification

| # | What is guaranteed | Test file | Type | Result |
|---|---|---|---|---|
| 1 | Every command (incl. `PlayStation`) survives an encode→decode round-trip unchanged | `WearSyncTest.kt:every command round-trips through encode then decode` | unit | PASS |
| 2 | `PlayStation` preserves its `stationId` across the wire | `WearSyncTest.kt:play-station command preserves the station id` | unit | PASS |
| 3 | `NowPlayingState` round-trips every field (title/subtitle/isPlaying/canSkip) | `WearSyncTest.kt:now-playing state round-trips every field` | unit | PASS |
| 4 | The command and state Data Layer paths are distinct | `WearSyncTest.kt:command and state paths are distinct` | unit | PASS |

4 new unit test cases in `:core:model` (`WearSyncTest`), all plain JVM. The `:wear` UI, `WearMediaClient`, `PhoneWearListenerService`, and `WearStatePublisher` are Android/Data-Layer runtime glue and are verified on-device below, per this project's pattern.

## On-Device Verification (paired emulators)

Environment: the phone emulator `Medium_Phone_API_35` (`emulator-5554`, `google_apis` image) and a Wear OS 5.1 emulator `easy_radio_wear` (`emulator-5556`, `system-images;android-35-ext15;android-wear;x86_64`) created for this phase, both booted concurrently.

**Verified:**
1. **Crash caught and fixed.** Launching the phone app first crashed with `FATAL EXCEPTION`: `WearStatePublisher`'s `connectedNodes.await()` threw `ApiException(17, API_UNAVAILABLE)` (this phone image has no Wearable API) and the uncaught failure in its `Dispatchers.Main` `launch{}` killed the process. Any device with no paired watch / no Wear-enabled Play Services would hit this. After wrapping the Data Layer calls in `runCatching`, a re-launch shows **0 FATAL exceptions** and the process stays alive.
2. **Phone side wired.** `dumpsys package` confirms `PhoneWearListenerService` is registered with `Action: com.google.android.gms.wearable.MESSAGE_RECEIVED` and `Path: PREFIX /easyradio/command`.
3. **Watch app runs on real Wear OS.** The `:wear` app builds (`wear-debug.apk`), installs, and launches on the 384×384 Wear emulator with **0 FATAL exceptions**; the Compose-for-Wear UI renders the now-playing area ("Easy Radio" / "Pick a station" — the default with no phone paired), the play control, and the station chips (`screencap` confirmed). Tapping the play control and a station chip exercises the `WearMediaClient` send path and does **not** crash (it no-ops with no connected node).

**Deferred (not verifiable in this environment):** the actual cross-device round-trip (tap station on watch → phone plays; phone playback → watch updates). Two confirmed blockers:
- The available phone emulator image (`google_apis`) **lacks the Wearable API** (`API_UNAVAILABLE`), so no Data Layer transport exists on it.
- Per Google's docs, pairing a Wear emulator to a phone requires the **Android Studio pairing assistant (GUI)**, a **Play-Store phone image**, and **Google-account sign-in** — none automatable from this CLI session.

Each half is proven to run on its real device; establishing the transport between them is a Studio Device Manager → **Pair Wearable** step. When paired, the coded loop is: watch UI → `WearMediaClient` → MessageClient → `PhoneWearListenerService` → `MediaController` → session, and back via `WearStatePublisher`.

## Coverage and Known Gaps

- `:core:model`: 4/4 new unit tests passing for this phase; full project suite (`./gradlew test`) green.
- The Data Layer glue (both listener/publisher and `WearMediaClient`) is not unit-tested — it depends on Play Services Wearable and was exercised on-device instead.
- **The end-to-end command/state round-trip is unverified** here (see blockers above); it needs a Play-Store phone image + Studio pairing + Google account. This is the top follow-up before shipping Phase 6.
- Podcasts are not yet exposed to the watch — the station picker uses `CuratedRadioStations`; a podcast picker would need the phone to send its subscription list over the Data Layer.
- `NowPlayingState` for **phone-initiated** playback shows a title on the watch only when the media item carries metadata; the Wear-initiated `PlayStation` path sets it, but `MainActivity.playStation` (phone UI) currently sets `MediaItem.fromUri` without metadata, so a phone-started station would display empty on the watch until that path also sets metadata.

## Git Checkpoints (this branch)

1. `test: add RED spec for phone<->watch WearSync protocol (Phase 6)` (RED — compile-time, `WearSync` unresolved)
2. `feat: implement phone<->watch WearSync protocol (Phase 6)` (GREEN — 4/4 `WearSyncTest`)
3. `feat: add :wear Wear OS module remote-controlling the phone (Phase 6)` (`:wear:assembleDebug` GREEN; watch UI renders on-device)
4. `feat: phone-side Wear listener + now-playing publisher (Phase 6)` (build + tests GREEN)
5. `fix: never crash when the Wearable API is unavailable (Phase 6)` (crash found on-device, fixed, 0 FATAL after)
6. `docs: add TDD evidence report for Phase 6` (this report)
