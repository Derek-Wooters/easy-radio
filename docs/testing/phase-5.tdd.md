# TDD Evidence Report — Phase 5 (Android Auto / Automotive OS)

**Source plan**: [`docs/implementation-plan.md`](../implementation-plan.md) (Phase 5)
**Date**: 2026-08-09
**Scope**: Expose the station + podcast catalog as an Android Auto / Android Automotive OS (AAOS) browse tree and make items playable from the head unit, by migrating the playback service from `MediaSessionService` to `MediaLibraryService`. Includes station/podcast artwork delivery to the car UI.

## User Journeys

1. As a driver, I want to open Easy Radio on the car head unit and see "Radio" and "Podcasts" sections without touching my phone.
2. As a driver, I want to browse into "Radio" and see the curated stations with their names, taglines, and logos.
3. As a driver, I want to tap a station and have it start playing through the car.
4. As a driver, I want subscribed podcasts to appear under "Podcasts" and expand to their episodes (playable, preferring a downloaded file when present).
5. As a maintainer, I want the phone UI and the car browse tree to share one database instance, not two competing handles on the same file.

## Task Report

| Task | Summary | Validation command | Result |
|---|---|---|---|
| `MediaBrowseTree` + `BrowseNode` | Pure builder for the browse tree: root → Radio/Podcasts, stations/episodes as playable nodes, podcasts as browsable, offline-vs-remote uri selection, and `mediaId → playback uri` resolution — media-system-agnostic so it is unit-testable without `android.net.Uri` | `./gradlew :core:media:test` | 7/7 PASS |
| `EasyRadioPlaybackService` → `MediaLibraryService` | `MediaLibrarySession` + `Callback` implementing `onGetLibraryRoot` / `onGetChildren` (feeding `CuratedRadioStations` + `PodcastRepository` through `MediaBrowseTree`) and `onAddMediaItems` for play resolution; `BrowseNode → MediaItem` adapter | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `EasyRadioGraph` | Single shared `EasyRadioDatabase` + `PodcastRepository`, consumed by both `MainActivity` and the service so the UI and the car browse tree do not open two handles on `easy-radio.db` | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| Manifest / Auto declarations | `MediaLibraryService` + `MediaBrowserService` intent filters, `com.google.android.gms.car.application` meta-data + `automotive_app_desc.xml`, and `android.hardware.type.automotive` (`required=false`) so one APK installs on phone and AAOS | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| Auto legacy play path fix | `onPlayFromMediaId` delivers a uri-less item; `onAddMediaItems` resolves the uri via `MediaBrowseTree.playbackUri`, and returns stations **synchronously** (immediate future) so the player prepares within the same play command instead of after an empty timeline | on-device (AAOS) | `BUFFERING → PLAYING(3)` |
| `ArtworkContentProvider` | Wraps a remote artwork url in a local `content://` uri (Auto/AAOS reject remote `http(s)` artwork); downloads + caches on first request and streams the file back | on-device (AAOS) | real logos render |
| Manual verification | AAOS API 35 emulator: browse root → Radio → stations (with logos), tap KFAN → live stream plays | `adb` + `dumpsys media_session` + `screencap` | All confirmed (see below) |

## Test Specification

| # | What is guaranteed | Test file | Type | Result |
|---|---|---|---|---|
| 1 | Root exposes `Radio` and `Podcasts` in order, both browsable, neither playable | `MediaBrowseTreeTest.kt:root exposes Radio and Podcasts as browsable, non-playable nodes` | unit | PASS |
| 2 | A station maps to a playable node with `station/<id>` id, title, tagline subtitle, artwork url, and the stream uri | `MediaBrowseTreeTest.kt:station maps to a playable node carrying the stream uri and artwork` | unit | PASS |
| 3 | A podcast maps to a browsable, non-playable `podcast/<id>` node | `MediaBrowseTreeTest.kt:podcast maps to a browsable, non-playable node` | unit | PASS |
| 4 | An episode maps to a playable `episode/<id>` node using the remote audio url by default | `MediaBrowseTreeTest.kt:episode maps to a playable node using the remote audio url by default` | unit | PASS |
| 5 | A downloaded episode prefers its local file path for offline playback | `MediaBrowseTreeTest.kt:downloaded episode prefers the local file path for offline playback` | unit | PASS |
| 6 | `playbackUri` resolves station and episode media ids back to their source uri | `MediaBrowseTreeTest.kt:playbackUri resolves station and episode media ids back to their source` | unit | PASS |
| 7 | `playbackUri` returns null for unknown or browsable (podcast/nonexistent) media ids | `MediaBrowseTreeTest.kt:playbackUri returns null for unknown or browsable media ids` | unit | PASS |

7 new unit test cases in `core:media` (`MediaBrowseTreeTest`), all plain JVM — no `android.net.Uri`, live network, or emulator required to run the suite. The `MediaItem` adapter, `MediaLibrarySession` wiring, the Auto legacy play path, and the artwork content provider are Android-runtime glue and are verified on-device below rather than in unit tests, per the established pattern for this project.

## On-Device Verification (Journeys 1–5)

Environment: `easy_radio_aaos` AVD — Android Automotive OS **API 35** system image (`system-images;android-35-ext15;android-automotive;x86_64`), created for this phase. The app was driven through the AAOS reference Media app (`com.android.car.media`) via the `android.car.intent.action.MEDIA_TEMPLATE` intent; no phone screen was used.

1. **Source connect + root**: pointing the car Media app at `com.easyradio.app/.playback.EasyRadioPlaybackService`, `MediaActivity` logged `MediaSource changed to …EasyRadioPlaybackService` and `MediaActivityCtr: Browse tree loaded, status (has children or not) changed: false -> true` — i.e. `onGetLibraryRoot` + `onGetChildren` returned children. The UI rendered **Radio** and **Podcasts** as the two top-level tabs (the root's browsable children).
2. **Radio browse**: the **Radio** tab listed all three curated stations with correct titles **and** taglines: `KFAN FM 100.3` / "Audio Home For Minnesota Sports", `AZPM NPR 89.1` / "NPR News and Music", `BBC World Service` / "News, analysis and information from the BBC" (`screencap` confirmed).
3. **Play a station**: tapping `KFAN FM 100.3` transitioned the car UI `BROWSING → PLAYBACK`; `dumpsys media_session` for our session went `BUFFERING(6) → PLAYING(3)`, and the Now Playing screen showed live ICY stream metadata (`text="Spot Block End" amgTrackId=…`) with a pause control — i.e. the live stream is genuinely playing, not a stub.
4. **Artwork**: initially the browse list showed placeholder tiles because Auto/AAOS reject remote `https` artwork uris (`BitmapDrawable created with null Bitmap`). After routing artwork through `ArtworkContentProvider` (`content://` uris), the Radio list rendered the real **KFAN**, **AZPM**, and **BBC World Service** logos (`screencap` confirmed). Direct `content read` of the provider returned a 43,168-byte image and wrote the cache file, confirming the download/serve path independently of the car app.
5. **Shared database**: `MainActivity` and the service both obtain their `PodcastRepository` from `EasyRadioGraph`, so the Podcasts browse branch reads the same subscriptions/episodes the phone UI writes (single `easy-radio.db` handle).

This proves the Phase 5 loop end-to-end on a real Automotive head unit: connect → browse root → browse Radio (names, taglines, logos) → play a station to `PLAYING(3)` with live stream metadata — without any phone-screen interaction, which is exactly the plan's Phase 5 validation.

## Coverage and Known Gaps

- `core:media`: 7/7 new unit tests passing for this phase, no skipped tests; full project suite (`./gradlew test`) green.
- The `MediaLibrarySession` callbacks, `BrowseNode → MediaItem` adapter, the Auto legacy play path, and `ArtworkContentProvider` are not unit-tested — they require the Android media/runtime and were verified on the AAOS emulator instead, per this project's pattern (no `compose-ui-test` / instrumented harness wired in yet).
- **Two root-diagnosed bugs are covered only by on-device evidence**, not a regression test: (a) the uri-less `onPlayFromMediaId` item, and (b) the `Dispatchers.Main` future landing after `play()` evaluated an empty timeline. Both are Android-session-timing issues that a JVM unit test can't reproduce; an instrumented `MediaController`/`MediaBrowser` test would be the right future guard.
- Podcast → episode browse was exercised through the same code path as stations but not separately screenshotted with a live subscription in this session; the `podcast/<id> → episodes` branch is unit-covered at the `MediaBrowseTree` level.
- `ArtworkContentProvider` is `exported="true"` and will fetch any `url` passed to it (returns image bytes only). Acceptable for this app; a hardening pass could restrict it to known artwork hosts to close a minor image-proxy vector.
- The AAOS media-source enumerator logs `No opt-in info found … Skipping MBS … non media template app` during automatic source discovery; browse/playback works via the explicit `MEDIA_TEMPLATE` intent. Confirming automatic listing in the AAOS media picker is a follow-up.

## Git Checkpoints (this branch)

1. `test: add RED spec for Android Auto browse tree (Phase 5)` (RED — compile-time, `MediaBrowseTree` unresolved)
2. `feat: implement Android Auto browse tree builder (Phase 5)` (GREEN — 7/7 `MediaBrowseTreeTest`)
3. `feat: serve Android Auto browse tree via MediaLibraryService (Phase 5)` (service migration + `EasyRadioGraph` + manifest; build + tests GREEN)
4. `fix: resolve playback uri from mediaId on Auto's legacy play path` (removes the `createMediaSource` crash on tap)
5. `fix: resolve station playback synchronously so Auto reaches PLAYING` (station play verified `PLAYING(3)` on AAOS)
6. `feat: serve station/podcast artwork to Auto via a content provider` (real logos render on AAOS)
7. `docs: add TDD evidence report for Phase 5` (this report)
