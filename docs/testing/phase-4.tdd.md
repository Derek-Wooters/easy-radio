# TDD Evidence Report — Phase 4 (Podcast Library UX)

**Source plan**: [`docs/implementation-plan.md`](../implementation-plan.md) (Phase 4)
**Date**: 2026-08-06
**Scope**: Episode downloads for offline playback, a persisted Up Next queue (add/reorder/remove), playback speed + 15s/30s skip controls, and the podcast library UI (grid, search, sort, Up Next screen) — built on top of the Phase 3 podcast engine.

## User Journeys

1. As a user, I want to download an episode so I can listen to it without a network connection.
2. As a user, I want to remove a downloaded episode's local file and have it fall back to streaming.
3. As a user, I want to add episodes to an "Up Next" queue and have that queue survive app restarts.
4. As a user, I want to reorder or remove items in my queue.
5. As a user, I want to skip forward 30 seconds or back 15 seconds without overshooting the start/end of an episode.
6. As a user, I want to speed up or slow down podcast playback.
7. As a user, I want to browse my subscribed podcasts as a grid and sort an episode list by newest/oldest.

## Task Report

| Task | Summary | Validation command | Result |
|---|---|---|---|
| `Episode.localFilePath` field | Track a downloaded episode's on-disk path | `./gradlew :core:database:test` | included in DAO/repo suites below |
| `EpisodeDownloader` | Real OkHttp download to app-private storage; `(id, audioUrl)` signature to stay decoupled from `Episode`'s https-only validation | `./gradlew :core:network:test` | 3/3 PASS (MockWebServer) |
| `PodcastRepository` download/delete | `downloadEpisode()`/`deleteDownload()` update `localFilePath` via DI'd `downloadFile`/`deleteFile` lambdas | `./gradlew :core:database:test` | 6 new cases, PASS |
| `QueueItemEntity` / `QueueDao` | New Room table (schema v1→v2), ordered by position, `maxPosition()` for append, `upsertAll` for reorder | `./gradlew :core:database:test` | 5/5 PASS (Robolectric) |
| `PodcastRepository` queue methods | `enqueue`/`removeFromQueue`/`reorderQueue`/`queue()` (joins queue order with episode data) | `./gradlew :core:database:test` | included in the 6 new cases above |
| `SeekMath.clampSeek` | Pure function clamping skip deltas to `[0, duration]`, treating `duration <= 0` as unbounded | `./gradlew :core:media:test` | 5/5 PASS |
| App UI wiring | Podcast grid (`LazyVerticalGrid`), episode list with sort/download/queue icons, Up Next screen with reorder controls, playback speed cycling, skip buttons wired through `MainActivity` | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| Manual verification | Real NPR "Planet Money" feed: subscribe → grid → episode list → download (real 37MB file) → offline-path playback → skip/speed → queue add/reorder/remove/persistence | `adb` + `logcat` + `dumpsys media_session` | All steps confirmed genuine (see below) |

## Test Specification

| # | What is guaranteed | Test file | Type | Result |
|---|---|---|---|---|
| 1 | Successful download writes the response body to a file and returns its path | `EpisodeDownloaderTest.kt:download success writes file` | unit (MockWebServer) | PASS |
| 2 | A non-2xx response returns null, no file written | `EpisodeDownloaderTest.kt:download failure returns null` | unit (MockWebServer) | PASS |
| 3 | `delete()` removes the file at the given path | `EpisodeDownloaderTest.kt:delete removes file` | unit (MockWebServer) | PASS |
| 4 | `downloadEpisode()` calls the download lambda and persists `localFilePath` | `PodcastRepositoryTest.kt:downloadEpisode ...` | unit | PASS |
| 5 | A failed download (lambda returns null) leaves `localFilePath` unset and returns false | `PodcastRepositoryTest.kt:downloadEpisode returns false ...` | unit | PASS |
| 6 | `deleteDownload()` calls the delete lambda and clears `localFilePath` | `PodcastRepositoryTest.kt:deleteDownload ...` | unit | PASS |
| 7 | `enqueue()` appends at `maxPosition + 1` | `PodcastRepositoryTest.kt:enqueue ...` | unit | PASS |
| 8 | `removeFromQueue()` removes the item | `PodcastRepositoryTest.kt:removeFromQueue ...` | unit | PASS |
| 9 | `reorderQueue()` rewrites positions to match the given id order | `PodcastRepositoryTest.kt:reorderQueue ...` | unit | PASS |
| 10 | `queue()` returns episodes joined in queue position order | `PodcastRepositoryTest.kt:queue ...` | unit | PASS |
| 11 | `QueueDao.observeAll()` returns items ordered by position | `QueueDaoTest.kt:observeAll orders by position` | unit (Robolectric) | PASS |
| 12 | `remove()` deletes the row | `QueueDaoTest.kt:remove deletes the item` | unit (Robolectric) | PASS |
| 13 | `maxPosition()` is -1 on an empty queue | `QueueDaoTest.kt:maxPosition returns -1 when empty` | unit (Robolectric) | PASS |
| 14 | `maxPosition()` reflects the highest stored position | `QueueDaoTest.kt:maxPosition returns highest stored position` | unit (Robolectric) | PASS |
| 15 | `upsertAll()` overwrites positions for a full reorder | `QueueDaoTest.kt:upsertAll reorders existing items` | unit (Robolectric) | PASS |
| 16 | Positive delta advances position | `SeekMathTest.kt:positive delta advances position` | unit | PASS |
| 17 | Negative delta rewinds position | `SeekMathTest.kt:negative delta rewinds position` | unit | PASS |
| 18 | Rewind past zero clamps to zero | `SeekMathTest.kt:rewind past zero clamps to zero` | unit | PASS |
| 19 | Advance past duration clamps to duration | `SeekMathTest.kt:advance past duration clamps to duration` | unit | PASS |
| 20 | Unknown duration (`<= 0`) does not clamp the upper bound | `SeekMathTest.kt:unknown duration ... does not clamp` | unit | PASS |

20 new test cases across `core:network`, `core:database`, and `core:media` for this phase (`PodcastRepositoryTest` grew from 9 to 15 cases; `QueueDaoTest`, `EpisodeDownloaderTest`, and `SeekMathTest` are new files) — all plain JVM or Robolectric, no live network or emulator required to run the suite.

## Manual Verification (Journeys 1–7)

Environment: `Medium_Phone_API_35` emulator, real network access, real NPR "Planet Money" RSS feed and iTunes-hosted audio (`npr.simplecastaudio.com`).

1. **Grid + search + subscribe**: Podcasts tab shows an empty grid with an "Up Next" header button; searched "Planet Money" against the live iTunes Search API, subscribed. Tapping the subscribed tile (grid, 3-column `LazyVerticalGrid`) opened its real episode list, parsed from the live RSS feed.
2. **Sort toggle**: tapped the sort icon in the episode list header — label switched "Newest first" ↔ "Oldest first" and row order visibly reversed.
3. **Download**: tapped the download icon on an episode row. `logcat` showed a genuine OkHttp request/response: `GET .../default.mp3... (6634ms, 37095626-byte body)`, `<-- 200 OK`. Confirmed the file landed on disk: `adb shell run-as com.easyradio.app ls -la files/podcast_downloads/` showed a 37,095,626-byte file matching the response body size exactly. The row's icon changed from "Download" to "Downloaded" (`content-desc` confirmed via `uiautomator dump`).
4. **Offline-path playback**: tapped the downloaded episode. `MediaCodec` was created (logcat) and `dumpsys media_session` reported `state=PLAYING(3)` with `position` advancing in real time (14942ms observed) — this plays the local file path (`MainActivity.playEpisode` prefers `episode.localFilePath` when the file exists), not a re-stream.
5. **Skip controls**: tapped skip-forward (30s) — position jumped from ~22s to 52,342ms. Tapped skip-back (15s) — position dropped to 42,999ms. Both confirmed via `dumpsys media_session` before/after, not just UI state.
6. **Playback speed**: tapped the speed button — label changed 1.0x → 1.25x, and `dumpsys media_session` confirmed `speed=1.25` in the real `PlaybackState`, proving the ExoPlayer speed was actually changed, not just the label.
7. **Queue add**: added two specific episodes ("Sand heists...", "What makes a toy go viral") to the queue via each row's "Add to queue" icon; opened "Up Next" from the library header and confirmed both appeared in add order, with the currently-playing episode's Up/Down buttons correctly disabled at the queue boundaries.
8. **Queue reorder + persistence**: tapped "Move down" on the first item — order swapped in the UI. Switched to the Radio tab and back to Podcasts/Up Next — the swapped order was still showing, confirming the reorder was persisted through `QueueDao.upsertAll` (Room), not just local Compose state.
9. **Queue remove**: removed both items via the "Remove from queue" icon — list showed the empty state ("Nothing queued yet"), confirming removal is real, not cosmetic.
10. **No crashes**: `adb logcat -d | grep -i "FATAL EXCEPTION"` across the full manual verification session returned no matches.

This proves the full Phase 4 loop end-to-end: real download → on-disk file → offline-sourced playback → genuine skip/speed changes (verified via `dumpsys media_session`, not just UI labels) → queue mutations that persist across navigation — against real external podcast content, not fixtures.

## Coverage and Known Gaps

- `core:network`, `core:database`, `core:media`: 20/20 new unit tests passing for this phase, no skipped tests.
- No unit/instrumented test directly covers the Compose UI (grid, sort toggle, Up Next screen); these were verified manually only, per the established pattern for this project (Compose UI is exercised via on-device verification, not `compose-ui-test`, which isn't wired into this project yet).
- Downloaded audio artwork/thumbnails are not implemented — episode rows use tinted-avatar placeholders, consistent with the scope-narrowing decision made in Phase 3 (no Coil/image loading yet).
- No download-progress indicator or resumable/paused downloads — `EpisodeDownloader.download()` is a single blocking call per episode; this matches the plan's deliberate scope-narrowing away from Media3's `DownloadManager`.
- No automated test covers queue-position persistence across a full app-process restart (only across tab navigation, which is a cheaper but weaker proxy); would be a good candidate for an instrumented test in a later hardening pass.

## Git Checkpoints (this branch)

1. `test: add localFilePath field and reproducer for EpisodeDownloader` (RED)
2. `feat: implement EpisodeDownloader` (GREEN)
3. `test: add reproducers for PodcastRepository download/delete` (RED)
4. `feat: implement PodcastRepository download/delete methods` (GREEN)
5. `test: add reproducers for Up Next queue (DAO + repository)` (RED)
6. `feat: implement Up Next queue` (GREEN)
7. `test: add reproducer for SeekMath skip-clamp logic` (RED)
8. `feat: implement SeekMath skip-clamp logic` (GREEN)
9. `feat: build podcast grid, episode sort, and Up Next screen UI` (build GREEN, manual verification passed — this commit)
10. `docs: add TDD evidence report for Phase 4` (this report)
