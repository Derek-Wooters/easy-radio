# TDD Evidence Report — Phase 0 & 1 (Project Scaffolding + Core Playback Engine)

**Source plan**: [`docs/implementation-plan.md`](../implementation-plan.md) (Phase 0, Phase 1)
**Date**: 2026-08-05
**Scope**: Gradle multi-module scaffolding + a working live-radio playback pipeline (phone only), proven against the real KFAN 100.3 stream.

## User Journeys

1. As a developer, I want `RadioStation` to reject malformed data (blank id/name, non-https stream URL), so bad station data fails fast instead of reaching the player.
2. As a developer, I want Media3 player state translated into a simple `PlaybackUiState` (`IDLE`/`BUFFERING`/`PLAYING`/`PAUSED`/`ERROR`), so UI code never has to reason about raw `Player.STATE_*` ints and error state directly.
3. As a developer, I want a `RadioPlaybackController` that drives play/pause/stop against a `Player` and exposes its state as a `StateFlow`, so playback orchestration is unit-testable without a device.
4. As a user, I want to tap Play and hear KFAN 100.3 actually stream, and tap Pause to stop it, so the app is a working radio player, not just passing tests.

Journey 4 is not something a JVM unit test can prove — it required an emulator run, captured below.

## Task Report

| Task | Summary | Validation command | Result |
|---|---|---|---|
| Gradle scaffolding | Multi-module project (`app`, `core:model`, `core:media`), version catalog, wrapper (Gradle 8.14.3) | `./gradlew build` | BUILD SUCCESSFUL |
| `RadioStation` | Data class with id/name/https-url validation + hardcoded KFAN test station | `./gradlew :core:model:test` | 4/4 PASS |
| `PlaybackStateMapper` | Pure function mapping Media3 `Player` state + error to `PlaybackUiState` | `./gradlew :core:media:test` | 6/6 PASS |
| `RadioPlaybackController` | Wraps a Media3 `Player`, drives play/pause/stop, exposes `StateFlow<PlaybackUiState>` | `./gradlew :core:media:test` | 6/6 PASS |
| `EasyRadioPlaybackService` + `MainActivity` | MediaSessionService wiring ExoPlayer; Compose UI bound via `MediaController` | `./gradlew build` (assembles debug/release APK) | BUILD SUCCESSFUL |
| Manual playback verification | Installed debug APK on `Medium_Phone_API_35` emulator, tapped Play/Pause | `adb install` + `adb shell input tap` + `adb logcat` | KFAN audio actually decoded and played (see below) |

## Test Specification

| # | What is guaranteed | Test file | Type | Result |
|---|---|---|---|---|
| 1 | Blank `id` is rejected | `core/model/src/test/kotlin/.../RadioStationTest.kt:blank id is rejected` | unit | PASS |
| 2 | Non-https `streamUrl` is rejected | `RadioStationTest.kt:non-https stream url is rejected` | unit | PASS |
| 3 | KFAN test station has a valid https URL | `RadioStationTest.kt:kfan test station has an https stream url` | unit | PASS |
| 4 | KFAN test station has non-blank id/name | `RadioStationTest.kt:kfan test station has a non-blank id and name` | unit | PASS |
| 5 | `Player.STATE_IDLE` → `IDLE` | `core/media/src/test/kotlin/.../PlaybackStateMapperTest.kt:idle state maps to Idle` | unit | PASS |
| 6 | `Player.STATE_BUFFERING` → `BUFFERING` | `PlaybackStateMapperTest.kt:buffering state maps to Buffering` | unit | PASS |
| 7 | Ready + `playWhenReady` → `PLAYING` | `PlaybackStateMapperTest.kt:ready and playWhenReady maps to Playing` | unit | PASS |
| 8 | Ready + not `playWhenReady` → `PAUSED` | `PlaybackStateMapperTest.kt:ready and not playWhenReady maps to Paused` | unit | PASS |
| 9 | `Player.STATE_ENDED` → `IDLE` | `PlaybackStateMapperTest.kt:ended state maps to Idle` | unit | PASS |
| 10 | Error overrides state → `ERROR` | `PlaybackStateMapperTest.kt:error overrides playback state and maps to Error` | unit | PASS |
| 11 | `play()` sets media item, prepares, and plays, in order | `core/media/src/test/kotlin/.../RadioPlaybackControllerTest.kt:play sets the station media item, prepares, and starts playback` | unit | PASS |
| 12 | `pause()` delegates to `player.pause()` | `RadioPlaybackControllerTest.kt:pause delegates to player pause` | unit | PASS |
| 13 | `stop()` delegates to `player.stop()` | `RadioPlaybackControllerTest.kt:stop delegates to player stop` | unit | PASS |
| 14 | Player becoming ready+playing reflects in `state` | `RadioPlaybackControllerTest.kt:state reflects the player becoming ready and playing` | unit | PASS |
| 15 | Player error reflects in `state` as `ERROR` | `RadioPlaybackControllerTest.kt:state reflects a player error` | unit | PASS |
| 16 | `release()` removes listener and releases player | `RadioPlaybackControllerTest.kt:release removes the listener and releases the player` | unit | PASS |

All 16 test cases run as plain JVM unit tests (no Robolectric, no emulator) — testability was achieved by injecting a `mediaItemFactory` into `RadioPlaybackController` so tests never touch `android.net.Uri` internals, and by mocking the Media3 `Player` interface with MockK.

## Manual Playback Verification (Journey 4)

Environment: `Medium_Phone_API_35` AVD, debug APK installed via `adb install`, launched via `adb shell am start`.

1. Tapped **Play** → UI showed `State: PLAYING` within ~2 seconds.
2. `adb logcat` confirmed real audio decode, not a stale UI value:
   - `MediaCodec will operate in async mode` — audio decoder actually created
   - `MediaSessionService: ... playbackState=PlaybackState {state=PLAYING(3), position=0, buffered position=938 ...}` progressing to `position=13368, buffered position=21247` over ~13 real seconds — position tracking wall-clock time is only possible with live audio actually playing
   - `MediaFocusControl: requestAudioFocus() ... AA=USAGE_MEDIA/CONTENT_TYPE_MUSIC` — audio focus correctly requested
   - `ActivityManager: Background started FGS: Allowed ... EasyRadioPlaybackService` — foreground service started correctly
   - No `PlaybackException` / `ExoPlaybackException` anywhere in the log
3. `adb shell dumpsys media_session` showed the app registered as `active=true` in the system media session stack.
4. Tapped **Pause** → UI showed `State: PAUSED`; playback stopped.

**Stream used**: `https://stream.revma.ihrhls.com/zc1209` — verified via the public [Radio-Browser](https://radio-browser.info) directory as "KFAN FM Sports" (`lastcheckok: 1`), matching KFAN's own iHeartRadio homepage (`kfan.iheart.com`). This satisfies the Phase 1 risk noted in the implementation plan ("verify via station's own listen live page or Radio-Browser's public listing").

**Known gap**: system media notification showed "Timeout while waiting for metadata to sync" warnings — cosmetic only (no title/artwork set on the `MediaItem` yet), audio playback itself was unaffected. Follow-up for Phase 2 when station metadata (name/artwork) is wired into `MediaItem.mediaMetadata`.

## Coverage and Known Gaps

- `core:model` and `core:media` business logic: 16/16 unit tests passing, no skipped tests.
- `app` module (Service/Activity wiring) has no automated tests — this is framework glue code verified manually per above, consistent with the plan's Phase 1 scope ("minimal now-playing screen + mini-player" as a spike, not full UI test coverage).
- No coverage tool (Jacoco/Kover) wired up yet — deferred until the module surface is larger; current confidence comes from 100% pass rate on all written tests plus the manual verification above.

## Git Checkpoints (this branch)

1. `test: add reproducer for RadioStation validation logic` (RED)
2. `fix: implement RadioStation with validation and KFAN test station` (GREEN)
3. `test: add reproducers for playback state mapping and controller` (RED)
4. `fix: implement playback state mapping and radio playback controller` (GREEN)
5. `feat: wire app module playback service and minimal now-playing UI` (build GREEN, manual playback verified)

No refactor commits were needed — implementations were minimal on first pass.
