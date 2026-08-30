# TDD Evidence Report — Phase 8 (Testing, Polish, Designer Handoff)

**Source plan**: [`docs/implementation-plan.md`](../implementation-plan.md) (Phase 8), cross-checked against [`docs/designer-brief.md`](../designer-brief.md)'s Priority 1 screen list.
**Date**: 2026-08-29
**Scope**: The plan's three Phase 8 items — unit tests for repositories/view models, a manual QA pass across phone/Auto/Wear, and confirming the designer screen list is delivered — plus the two real gaps that audit surfaced (a missing Downloads screen and missing stream-failure/offline error states) and the optional onboarding flow.

## User Journeys

1. As a user, I want my downloaded episodes listed in one place with how much space they're using, so I can manage storage.
2. As a user, I want to know when a stream fails to load instead of the app silently doing nothing.
3. As a developer, I want the Wear/Auto integration's decision logic covered by real unit tests, not just manual device verification.
4. As a first-time user, I want a quick "pick your favorites" step so the app feels tailored from the start, with the option to skip it.

## Task Report

| Task | Summary | Validation command | Result |
|---|---|---|---|
| Downloads screen | New standalone list of downloaded episodes (`EpisodeDao.observeDownloaded`), real storage-used total computed from actual file sizes, remove action; reachable from the Podcasts overflow menu | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL; on-device verified |
| Stream-failure / empty states | `PlaybackUiState.ERROR` (already computed via `PlaybackStateMapper`, never surfaced) now shows a `Snackbar`; added a missing "no podcasts yet" empty state | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `SettingsRepository` testability fix | Found and fixed a real bug: `by preferencesDataStore(...)` is a process-wide singleton that leaked state between test methods. Refactored to accept an injectable `DataStore<Preferences>` (`Context`-based constructor unchanged for production) | `./gradlew :app:testDebugUnitTest --tests SettingsRepositoryTest` | 6/6 PASS |
| `WearCommandMapper` extraction | Pulled `PhoneWearListenerService`'s `WearCommand`→action decision logic into a pure, unit-tested function in `core:media` | `./gradlew :core:media:test` | 6/6 PASS |
| `NowPlayingStateMapper` extraction | Pulled `WearStatePublisher`'s metadata→`NowPlayingState` mapping (including the subtitle→artist→empty fallback chain) into a pure function | `./gradlew :core:media:test` | 3/3 PASS |
| `WearMessageSender` seam | `WearStatePublisher` no longer calls `Wearable.getMessageClient/getNodeClient` directly — confirmed via `javap` against the real AAR that both are abstract classes tied to `GoogleApi` construction, not fakeable. Introduced an interface seam instead | `./gradlew :app:testDebugUnitTest --tests WearStatePublisherTest` | 3/3 PASS |
| QA pass: Android Auto | Installed on a real AAOS API 35 emulator; app icon renders correctly in the facet bar, Home screen renders with real curated-station data, KFAN reaches `state=PLAYING` (confirmed via `dumpsys media_session`) | on-device | verified, zero `FATAL EXCEPTION` |
| QA pass: Wear OS | Installed on a real Wear OS emulator; app launches, renders "Easy Radio / Pick a station" UI correctly | on-device | verified, zero `FATAL EXCEPTION` |
| Onboarding flow | First-run "Pick your favorites" genre picker (`docs/designs/7f-onboarding.png`), gated by a persisted `hasCompletedOnboarding` flag, Skip/Continue both complete onboarding (Skip persists no genres) | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL; on-device verified |

## Test Specification

| # | What is guaranteed | Test file | Type | Result |
|---|---|---|---|---|
| 1 | `settings` starts at `AppSettings` defaults before any writes | `SettingsRepositoryTest.kt:settings starts at AppSettings defaults` | unit | PASS |
| 2 | Theme/sleep-timer/download-quality/wifi-only/auto-download setters persist and round-trip | `SettingsRepositoryTest.kt` (4 cases) | unit | PASS |
| 3 | `completeOnboarding` persists the completed flag and the chosen genres | `SettingsRepositoryTest.kt:completeOnboarding persists...` | unit | PASS |
| 4 | `AppSettings()` defaults include `hasCompletedOnboarding=false`, `favoriteGenres=emptySet()` | `AppSettingsTest.kt:defaults are system theme...` | unit | PASS |
| 5 | `WearCommand.Play/Pause` map to the matching player action | `WearCommandMapperTest.kt` (2 cases) | unit | PASS |
| 6 | `SkipForward`/`SkipBack` seek 30s/15s clamped to duration/zero | `WearCommandMapperTest.kt` (2 cases) | unit | PASS |
| 7 | `PlayStation` maps to a `PlayStream` action for a known station id, `Ignore` for an unknown one | `WearCommandMapperTest.kt` (2 cases) | unit | PASS |
| 8 | Title/subtitle map straight through when both are present | `NowPlayingStateMapperTest.kt:uses title and subtitle...` | unit | PASS |
| 9 | Subtitle falls back to artist, then to an empty string, when metadata fields are missing | `NowPlayingStateMapperTest.kt` (2 cases) | unit | PASS |
| 10 | `attach()` publishes the current state to every connected node | `WearStatePublisherTest.kt:attach publishes...` | unit | PASS |
| 11 | A failure fetching connected nodes doesn't crash `publish()` (the `runCatching` guard actually works) | `WearStatePublisherTest.kt:a failure fetching...` | unit | PASS |
| 12 | No connected nodes means no messages are sent | `WearStatePublisherTest.kt:no connected nodes...` | unit | PASS |

18 new/updated unit cases across `core:model`, `core:media`, and `app`, all plain JVM or Robolectric, all passing. `DownloadsScreen`, the error snackbar, and `OnboardingScreen` are Compose UI verified on-device below, per this project's established pattern.

## On-Device Verification

1. **Downloads screen.** Reached via the Podcasts overflow menu; lists downloaded episodes with a real "N MB used" total and a working remove action.
2. **Auto QA pass (`easy_radio_aaos`, API 35).** Fresh install; the "Easy Radio" card appears on the car launcher home screen; the app icon (from the earlier icon/splash work) renders correctly as "ER" in the facet bar; opening it renders the real Home screen (Live Radio Dial with KFAN/AZPM/BBC); tapping KFAN's play button reaches `state=PLAYING(3)` per `dumpsys media_session`. Zero `FATAL EXCEPTION` across the session.
3. **Wear OS QA pass (`easy_radio_wear`).** Fresh install; launches directly into "Easy Radio / Pick a station" with a station chip visible. Zero `FATAL EXCEPTION`. Full Wear↔phone remote-control round-trip still isn't verifiable headlessly (needs the Android Studio pairing GUI — same boundary documented in the Phase 6 evidence report); this was a per-surface smoke check confirming this session's phone-UI nav rebuild didn't regress either surface.
4. **Onboarding flow (fresh install, `Medium_Phone_API_35`).** First launch shows "Pick your favorites" with 8 genre chips wrapping into two rows, matching `docs/designs/7f-onboarding.png`. Tapping "Sports" and "Comedy" toggles them to the selected (filled) state; tapping another chip again deselects it. "Continue" persists the selection and navigates straight into Home. Force-killing and relaunching the app goes straight to Home with **no onboarding flash** — confirms the one-time-real-DataStore-read gating (`LaunchedEffect(Unit) { settingsRepository.settings.first() }`) works, not just the naive `collectAsState` default. Zero `FATAL EXCEPTION`.

## Coverage and Known Gaps

- `core:model` + `core:media` + `app`: 18/18 new unit tests passing; full suite (`./gradlew test`) green.
- **A real regression was caught and fixed during this phase**: adding `PodcastDao.setPreset` earlier in the session had silently broken `PodcastRepositoryTest` (its fake didn't implement the new method) because only `compileDebugKotlin`/`assembleDebug` had been run, never the test suite. This is exactly the kind of gap Phase 8's "unit tests for repositories" item exists to catch.
- **`WearStatePublisher`'s underlying `MessageClient`/`NodeClient` calls (via `PlayServicesWearMessageSender`) and `PhoneWearListenerService`'s `MediaController` glue remain unit-test-free by design** — they're thin wrappers over Play Services/Media3 APIs that can't be meaningfully faked (confirmed via `javap`, not assumed); their correctness is covered by the on-device Wear QA pass instead.
- **Onboarding's genre selection doesn't yet drive any filtering** — it's persisted (`SettingsRepository.completeOnboarding`) for future personalization, but the app has no genre-tagged station/podcast data to filter Home or Browse by yet. Documented honestly in the screen's own doc comment rather than faking an effect.
- **Designer screen list** (`designer-brief.md` Priority 1, 14 items + design system): all delivered except Onboarding was previously missing and is now built as of this report. Deliberately-scoped-down items from earlier phases remain as documented in `phase-7.tdd.md` (5-item nav's Home/Search/Playlists are now real; podcast-detail Highlights tab remains an honest placeholder; podcast "About" tab shows only title/author/feed since `Podcast` has no `description` field).

## Git Checkpoints

1. `feat: Phase 8 - Downloads screen, error states, and Auto/Wear test coverage` (merged via PR #8)
2. `feat: add onboarding flow with persisted genre picker` (this report's commit, pending)
