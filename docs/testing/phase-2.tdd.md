# TDD Evidence Report — Phase 2 (Radio Station Directory)

**Source plan**: [`docs/implementation-plan.md`](../implementation-plan.md) (Phase 2)
**Date**: 2026-08-05
**Scope**: Live station search against the public Radio-Browser directory, a curated fallback list, and a browse/search UI wired to the existing Phase 1 playback pipeline.

## User Journeys

1. As a user, I want to see a short list of known-good stations (KFAN + a couple others) before I search anything, so the app isn't empty on first launch.
2. As a user, I want to search for a station by name and get real results back, so I'm not limited to the one hardcoded test station from Phase 1.
3. As a developer, I want malformed or unverified directory entries (non-https streams, entries that failed their last health check) filtered out before they ever reach the player.
4. As a developer, I want a directory outage to degrade gracefully (empty search results) rather than crash the app.
5. As a user, I want tapping any station — curated or searched — to actually play it through the existing player.

## Task Report

| Task | Summary | Validation command | Result |
|---|---|---|---|
| `core:network` module | Retrofit + kotlinx.serialization client for Radio-Browser | `./gradlew :core:network:test` | 10/10 PASS |
| `RadioStationMapper` | DTO → `RadioStation`, filtering non-https/unverified/blank entries | `./gradlew :core:network:test` | 5/5 PASS |
| `RadioStationRepository` | Combines curated list + live search, never throws on failure | `./gradlew :core:network:test` | 5/5 PASS |
| `CuratedRadioStations` | KFAN + AZPM NPR 89.1 + BBC World Service, all verified live | `./gradlew :core:model:test` | 3/3 PASS (7/7 total in `core:model`) |
| `RadioBrowseScreen` + `MainActivity` wiring | Search box + curated/search list + mini-player, reusing Phase 1 `MediaController` | `./gradlew build` | BUILD SUCCESSFUL |
| Manual verification | Installed on emulator, searched "jazz24", played a result never hardcoded in the app | `adb install` + `adb shell input` + `adb logcat` | Real audio played (see below) |

## Test Specification

| # | What is guaranteed | Test file | Type | Result |
|---|---|---|---|---|
| 1 | Verified https station maps correctly (id/name/url/image) | `RadioStationMapperTest.kt:verified https station maps to a playable RadioStation` | unit | PASS |
| 2 | Missing `stationuuid` falls back to stream URL as id | `RadioStationMapperTest.kt:station without a stable uuid falls back to the stream url as id` | unit | PASS |
| 3 | Non-https stream URL filtered out | `RadioStationMapperTest.kt:non-https stream url is filtered out` | unit | PASS |
| 4 | Blank name filtered out | `RadioStationMapperTest.kt:blank name is filtered out` | unit | PASS |
| 5 | Failed last health check filtered out | `RadioStationMapperTest.kt:station that failed its last health check is filtered out` | unit | PASS |
| 6 | `curatedStations()` returns the injected curated list | `RadioStationRepositoryTest.kt:curatedStations returns the curated list` | unit | PASS |
| 7 | `search()` delegates to the API and maps valid results | `RadioStationRepositoryTest.kt:search delegates to the api and maps valid results` | unit | PASS |
| 8 | `search()` filters out unmappable DTOs | `RadioStationRepositoryTest.kt:search filters out unmappable dtos` | unit | PASS |
| 9 | Blank query short-circuits without calling the API | `RadioStationRepositoryTest.kt:search returns an empty list for a blank query without calling the api` | unit | PASS |
| 10 | API failure returns empty list, never throws | `RadioStationRepositoryTest.kt:search returns an empty list instead of throwing when the api fails` | unit | PASS |
| 11 | `CuratedRadioStations.ALL` includes KFAN | `CuratedRadioStationsTest.kt:curated list includes the KFAN test station` | unit | PASS |
| 12 | Curated list has more than one station | `CuratedRadioStationsTest.kt:curated list has more than one station` | unit | PASS |
| 13 | No duplicate ids in curated list | `CuratedRadioStationsTest.kt:curated list has no duplicate ids` | unit | PASS |

All 13 new tests run as plain JVM unit tests. The Retrofit `RadioBrowserApi` interface was faked directly in `RadioStationRepositoryTest` (no MockWebServer needed since the interface itself has no Retrofit-specific runtime requirement to satisfy in a fake) — kept the test suite fast and network-independent.

## Manual Verification (Journeys 2 and 5 together)

Environment: same `Medium_Phone_API_35` emulator as Phase 1, updated debug APK installed via `adb install -r`.

1. Launched the app — curated list rendered immediately: KFAN FM 100.3, AZPM NPR 89.1, BBC World Service.
2. Typed "jazz24" into the search box — after the 400ms debounce, three real results appeared from the live Radio-Browser API (`Jazz24`, `Jazz24`, `Jazz24 [AAC/64 kbit]`), none of which are hardcoded anywhere in the app.
3. Tapped the third result — mini-player showed `Jazz24 / State: PLAYING`.
4. `adb logcat` confirmed genuine playback, not a stale UI value: `playbackState=PlaybackState {state=PLAYING(3), position=2049 ...}` advancing to `position=14853` over ~12 real seconds, buffered position growing ahead of it. No `PlaybackException` anywhere in the log.

This proves the full loop: live network search → DTO parsing → filtering → UI rendering → tap-to-play → real audio decode, for a station that only exists because Radio-Browser returned it at that moment.

## Coverage and Known Gaps

- `core:network` and the `CuratedRadioStations` addition to `core:model`: 13/13 new unit tests passing, no skipped tests.
- **Favoriting/persistence was deliberately not added in this phase.** The original Phase 2 plan mentioned "favoriting persists," but the module layout only introduces Room in Phase 3 (podcasts) and full persistence is Phase 7. Adding a separate ad-hoc persistence mechanism now would fork the storage strategy; deferred to stay aligned with the phased plan rather than silently expanding scope.
- The Home screen UI here is a functional search/browse list, not the full presets-shelf/recently-played design from `MainScreen.png` — that visual polish is sequenced to land once real designer screens exist (per `docs/designer-brief.md`), consistent with the "get radio + podcasts working" priority over UI polish.
- Radio-Browser is queried against a single fixed mirror (`de1.api.radio-browser.info`) rather than the documented DNS SRV mirror-selection approach — acceptable for this phase, flagged as a follow-up if that mirror becomes unreliable.

## Git Checkpoints (this branch)

1. `test: add reproducers for Radio-Browser mapper and station repository` (RED)
2. `feat: add curated fallback station list` (GREEN, `core:model`)
3. `fix: implement Radio-Browser mapper, repository, and API factory` (GREEN, `core:network`)
4. `feat: add station search/browse UI backed by RadioStationRepository` (build GREEN, manual verification passed)
