# TDD Evidence Report — Phase 7 (Persistence & Settings + Design Alignment)

**Source plan**: [`docs/implementation-plan.md`](../implementation-plan.md) (Phase 7)
**Date**: 2026-08-11
**Scope**: A Settings screen (theme, download quality, Wi-Fi-only / auto-download, sleep timer) backed by DataStore persistence, plus a sleep timer that pauses playback. This phase also absorbed a round of **design alignment** against `docs/designs/`: moving primary navigation to the bottom, and matching the Settings, Live Radio, Podcasts library, mini-player, podcast episode list, and a new full-screen Now Playing to their designs.

## User Journeys

1. As a user, I want to switch the app theme (System/Light/Dark) and have it apply immediately.
2. As a user, I want my settings to survive a force-kill / relaunch (no backend — local only).
3. As a user, I want a sleep timer that pauses playback after a chosen duration.
4. As a user, I want to set download quality, Wi-Fi-only downloads, and auto-download.
5. As a user, I want the app's navigation and screens to match the agreed designs.

## Task Report

| Task | Summary | Validation command | Result |
|---|---|---|---|
| `SleepTimer` | Pure `remainingMs` / `isExpired` math driven by the elapsed-realtime clock; non-positive duration = no timer | `./gradlew :core:media:test` | 4/4 PASS |
| `AppSettings` / `ThemeMode` / `DownloadQuality` | Settings model with defaults and pure `ThemeMode.resolveDarkTheme(systemInDark)` | `./gradlew :core:model:test` | 3/3 PASS |
| `SettingsRepository` | Preferences DataStore persisting `AppSettings` as a `Flow`, defaults for absent/unknown keys; shared via `EasyRadioGraph` | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| Settings screen + wiring | Segmented Appearance control, Download quality / Wi-Fi-only / auto-download, Sleep timer default; theme applied via `EasyRadioTheme(darkTheme = …)`; a `LaunchedEffect` polls `SleepTimer.isExpired` and pauses | on-device | verified (see below) |
| Bottom navigation | Replaced the top `PrimaryTabRow` with a Material3 `Scaffold` + bottom `NavigationBar` | on-device | verified |
| Design alignment: Settings (`7e`), Radio (`2d`), Podcasts (`4a`), mini-player (`7h`), episode list (`5b`), Now Playing (`3b/6b`) | Match the designs; adds a full-screen `NowPlayingScreen` reached from the mini-player | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL; screenshots below |

## Test Specification

| # | What is guaranteed | Test file | Type | Result |
|---|---|---|---|---|
| 1 | Sleep-timer `remaining` counts down from the full duration | `SleepTimerTest.kt:remaining counts down from the full duration` | unit | PASS |
| 2 | `remaining` clamps to zero once the duration has elapsed | `SleepTimerTest.kt:remaining clamps to zero once the duration has elapsed` | unit | PASS |
| 3 | `isExpired` is false before the duration and true at/after it | `SleepTimerTest.kt:isExpired is false before the duration and true at or after` | unit | PASS |
| 4 | A non-positive duration means "no timer" — never expires | `SleepTimerTest.kt:a non-positive duration means no timer is set - never expires` | unit | PASS |
| 5 | `ThemeMode.SYSTEM` follows the system dark flag | `AppSettingsTest.kt:system theme mode follows the system dark flag` | unit | PASS |
| 6 | `LIGHT`/`DARK` ignore the system flag | `AppSettingsTest.kt:light and dark modes ignore the system flag` | unit | PASS |
| 7 | `AppSettings` defaults: System theme, no timer, HIGH quality, Wi-Fi-only, no auto-download | `AppSettingsTest.kt:defaults are system theme, no sleep timer, wifi-only high-quality downloads, no auto-download` | unit | PASS |

7 new/updated unit cases in `:core:media` and `:core:model`, all plain JVM. The `SettingsRepository` (DataStore), the Compose screens, and the design-alignment UI are Android-runtime glue verified on-device below, per this project's pattern.

## On-Device Verification (`Medium_Phone_API_35`)

1. **Persistence (core Phase 7 validation).** Opened Settings, chose **Dark**; the whole UI flipped instantly. `am force-stop` + relaunch → the app **reopened in Dark theme**, proving the DataStore setting survived the restart (the plan's "force-kill app, relaunch, state intact"). Subscriptions/positions/queue already persist via Room from Phases 3–4.
2. **Settings vs design `7e`.** Appearance is a segmented `Light | Dark | System` control; Downloads shows `Download quality → High`, `Wi-Fi only`, `Auto-download new episodes`; Playback shows `Sleep timer default → Off`. Matches the design's sections and controls.
3. **Bottom navigation.** Radio / Podcasts / Settings render as a bottom `NavigationBar` with icons + labels and the correct selected state; the mini-player sits just above it.
4. **Live Radio vs `2d`.** Header search icon + `All Cities` / `All Genres` filter chips (dropdowns wired to Radio-Browser search) + circled play buttons.
5. **Podcasts header vs `4a`.** Search behind the header magnifier + an overflow (⋮) menu whose "Up Next" item opens the queue.
6. **Podcast episode list vs `5b`.** Real Planet Money data: header avatar + title + "NPR", `All episodes / Newest`, and rows with thumbnail + title + "4 days ago · 34 min" meta + 2-line description + `Listen` / add-to-queue / download.
7. **Mini-player vs `7h`.** Playing a podcast shows the thin top progress bar and the speed / skip-15 / play-pause / skip-30 controls; radio stays bar-less with the LIVE badge.
8. **Now Playing (`3b/6b`).** Tapping the mini-player opens a full-screen player. Radio: LIVE badge, large artwork, big play/pause. Podcast (Planet Money): large real artwork, position bar with live labels **`0:51 / 37:20`**, speed + skip controls. Back / the collapse chevron returns to the list.

No `FATAL EXCEPTION` was observed across the verification session.

## Coverage and Known Gaps

- `:core:media` + `:core:model`: 7/7 new unit tests passing; full suite (`./gradlew test`) green.
- `SettingsRepository` (DataStore) and all Compose screens are verified on-device only (no `compose-ui-test` harness in this project).
- **Sleep-timer pause is not timed end-to-end on-device** (shortest option is 15 min). The countdown/expiry is unit-tested (`SleepTimer`) and the wiring (`LaunchedEffect` → `isExpired` → `pause()`) is in place.
- **Design gaps deliberately left** (out of scope for this app): the design's 5-item bottom nav (Home / Search / Radio / Podcasts / Playlists) — the app ships 3 (Radio / Podcasts / Settings); the podcast-detail Now Playing / Highlights / About tabs and Following/Preset chips; and the sleep-timer / queue affordances on the full Now Playing screen (omitted rather than shipped as dead controls).
- The Live Radio `All Cities` / `All Genres` chips filter via free-text Radio-Browser search over a small hardcoded option set — an approximation, not a dedicated geo/tag API.

## Git Checkpoints (merged via PR #5)

1. `test: add RED spec for SleepTimer countdown/expiry (Phase 7)` (RED)
2. `feat: implement SleepTimer countdown/expiry math (Phase 7)` (GREEN — 4/4)
3. `feat: add AppSettings model + ThemeMode resolution (Phase 7)` (GREEN — model tests)
4. `feat: settings screen with DataStore persistence + theme + sleep timer (Phase 7)` (build GREEN; persistence verified)
5. `fix: move primary navigation to a bottom NavigationBar`
6. `feat: align settings screen with the design (7e)` (adds DownloadQuality)
7. `feat: align Live Radio browse with the design (2d)`
8. `feat: align Podcasts library header with the design (4a)`
9. `feat: mini-player progress bar + time-left for podcasts (7h)`
10. `feat: align podcast show detail episode list with the design (5b)`
11. `feat: full-screen Now Playing reached from the mini-player (3b/6b)`
12. `docs: add TDD evidence report for Phase 7` (this report)
