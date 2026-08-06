# TDD Evidence Report — Phase 3 (Podcast Engine)

**Source plan**: [`docs/implementation-plan.md`](../implementation-plan.md) (Phase 3)
**Date**: 2026-08-05
**Scope**: Podcast discovery (iTunes Search), RSS/iTunes-namespace feed parsing, Room persistence for subscriptions/episodes, and playback with resume-from-position — wired into the existing Phase 1/2 playback engine and UI.

## User Journeys

1. As a user, I want to search for a podcast by name and get real results, so I can find shows to subscribe to.
2. As a developer, I want duplicate search results (same feed) removed before they reach the UI, so the app doesn't crash on a `LazyColumn` key collision.
3. As a user, I want subscribing to a podcast to fetch its real episode list from the show's RSS feed, so I see actual content, not placeholders.
4. As a developer, I want RSS parsing to handle real-world feed variance (missing `guid`, both `itunes:duration` formats, missing `pubDate`) without crashing.
5. As a user, I want to tap an episode and hear it actually play, whichever tab I'm on, so playback controls are never invisible while audio is running.
6. As a user, I want playback to resume near where I left off, not restart from zero, when I return to an episode.

## Task Report

| Task | Summary | Validation command | Result |
|---|---|---|---|
| `Podcast` / `Episode` models | Data classes with id/title/https-url validation | `./gradlew :core:model:test` | 6/6 PASS (13/13 total in module) |
| `ItunesSearchApi` + mapper | Retrofit client for Apple's iTunes Search API, DTO→`Podcast` filtering | `./gradlew :core:network:test` | 4/4 PASS |
| `PodcastFeedParser` | Namespace-aware DOM parser, RSS/iTunes XML → `List<Episode>` | `./gradlew :core:network:test` | 7/7 PASS |
| `core:database` module | Room schema (KSP), `PodcastDao`/`EpisodeDao` | `./gradlew :core:database:test` | 6/6 PASS (Robolectric, in-memory DB) |
| `PodcastRepository` | Orchestrates search/subscribe/episodes/position, never throws on network failure | `./gradlew :core:database:test` | 9/9 PASS |
| Duplicate-result bugfix | `distinctBy(id)` after mapping search results | `./gradlew :core:database:test` | 1/1 PASS (regression test) |
| App UI wiring + `NowPlayingBar` fix | Radio/Podcasts tabs, tab-independent mini-player, resume-on-play | `./gradlew build` | BUILD SUCCESSFUL |
| Manual verification | Real NPR "Planet Money" feed: search → subscribe → episodes → play → pause → relaunch → resume | `adb` + `logcat` | All steps confirmed genuine (see below) |

## Test Specification

| # | What is guaranteed | Test file | Type | Result |
|---|---|---|---|---|
| 1 | Valid `Podcast` constructs | `PodcastTest.kt:valid podcast constructs successfully` | unit | PASS |
| 2 | Blank id/title and non-https feed url rejected | `PodcastTest.kt` (3 cases) | unit | PASS |
| 3 | Valid `Episode` constructs | `EpisodeTest.kt:valid episode constructs successfully` | unit | PASS |
| 4 | Blank id and non-https audio url rejected | `EpisodeTest.kt` (2 cases) | unit | PASS |
| 5 | Valid iTunes DTO maps to `Podcast` | `ItunesPodcastMapperTest.kt:valid dto maps to a Podcast` | unit | PASS |
| 6 | Blank collection name / non-https feed url filtered | `ItunesPodcastMapperTest.kt` (2 cases) | unit | PASS |
| 7 | Blank artwork URL becomes null | `ItunesPodcastMapperTest.kt:blank artwork url becomes null` | unit | PASS |
| 8 | Parses guid, pubDate (RFC822), H:MM:SS duration | `PodcastFeedParserTest.kt:parses a well-formed feed...` | unit | PASS |
| 9 | Parses plain-seconds `itunes:duration` | `PodcastFeedParserTest.kt:parses plain-seconds duration format` | unit | PASS |
| 10 | Falls back to enclosure URL as id when guid missing | `PodcastFeedParserTest.kt:falls back to enclosure url as id` | unit | PASS |
| 11 | Non-https enclosure skipped | `PodcastFeedParserTest.kt:skips items with a non-https enclosure url` | unit | PASS |
| 12 | Missing title skipped | `PodcastFeedParserTest.kt:skips items with no title` | unit | PASS |
| 13 | Missing pubDate/duration → null fields, no throw | `PodcastFeedParserTest.kt:missing pubDate and duration produce null fields` | unit | PASS |
| 14 | Malformed XML → empty list, no throw | `PodcastFeedParserTest.kt:malformed xml returns an empty list` | unit | PASS |
| 15 | `PodcastDao` upsert/observeAll/delete round-trip | `PodcastDaoTest.kt` (3 cases, Robolectric) | unit | PASS |
| 16 | `EpisodeDao` upsertAll/observeByPodcast/position round-trip | `EpisodeDaoTest.kt` (3 cases, Robolectric) | unit | PASS |
| 17 | `search()` delegates + maps + dedupes + degrades gracefully | `PodcastRepositoryTest.kt` (5 cases) | unit | PASS |
| 18 | `subscribe()` persists podcast and fetches episodes | `PodcastRepositoryTest.kt:subscribe stores the podcast and fetches its episodes` | unit | PASS |
| 19 | `unsubscribe()` removes the podcast | `PodcastRepositoryTest.kt:unsubscribe removes the podcast` | unit | PASS |
| 20 | `episodesFor()` reflects stored episodes | `PodcastRepositoryTest.kt:episodesFor reflects episodes stored for that podcast` | unit | PASS |
| 21 | `savePosition`/`lastPosition` round-trip, defaults to 0 | `PodcastRepositoryTest.kt` (2 cases) | unit | PASS |

30 new test cases across `core:model`, `core:network`, and `core:database`, all plain JVM or Robolectric — no live network or emulator required to run the suite.

## Manual Verification (Journeys 1, 3, 5, 6)

Environment: `Medium_Phone_API_35` emulator, real network access, real NPR "Planet Money" RSS feed (`feeds.npr.org`).

1. **Search**: typed "PlanetMoney" in the Podcasts tab — real results returned from Apple's iTunes Search API (Planet Money, Planet Money Summer School, several single-episode entries).
2. **Duplicate-result crash found and fixed**: first attempt crashed with `IllegalArgumentException: Key "https://feeds.npr.org/510289/podcast.xml" was already used` — iTunes Search returned Planet Money's feed twice. Added a regression test (`search removes duplicate results with the same feed url`) and fixed via `distinctBy(id)` in `PodcastRepository.search()`. Reinstalled — no crash, same query now works cleanly.
3. **Subscribe**: tapped Subscribe on "Planet Money" — row switched to a delete icon, confirming reactive state from `PodcastDao.observeAll()`.
4. **Episodes populate from RSS**: tapped the subscribed show — real episode list appeared ("Sand heists and property rights in the Caribbean", "What makes a toy go viral", etc.) with real descriptions, fetched and parsed from the live feed.
5. **Mini-player gap found and fixed**: tapping an episode played it (confirmed via logcat: `MediaCodec` created, `state=PLAYING` with position advancing 0ms → 27,705ms in real time), but no mini-player was visible — it only existed inside `RadioBrowseScreen`. Fixed by extracting `NowPlayingBar` to the `MainActivity` level, rendered independent of the active tab. Reinstalled — mini-player now shows the episode (title, "Planet Money" tagline, pause icon, no LIVE badge) and stays visible when switching to the Radio tab.
6. **Resume-from-position**: paused at `position=169032` (2:49), pressed Home (triggers `onStop()`'s immediate `savePosition`), relaunched the app fresh, navigated back to the same episode, tapped it — logcat showed `seekTo` landing exactly on `position=169032, buffered position=169032`, then `state=PLAYING` with position continuing to advance from there (181092ms → 184094ms → 187669ms).

This proves the full loop end-to-end: live search → real RSS fetch/parse → Room persistence surviving app restart → genuine audio playback → position resume — against a real external podcast feed, not fixtures.

## Coverage and Known Gaps

- `core:model`, `core:network`, `core:database`: 30/30 new unit tests passing, no skipped tests.
- Two real bugs were found only through manual verification, not unit tests: the duplicate-result crash (network response shape wasn't covered by any existing fixture) and the tab-scoped mini-player (a UI composition gap, not something a unit test would catch). Both now have direct fixes; the duplicate-result one also has a regression test.
- No podcast downloads, playback speed, or skip-silence yet — those are Phase 4 (Podcast Library UX) per the plan.
- No automated instrumentation test covers the resume-from-position flow (it's inherently cross-process-restart); it's verified manually above and would be a good candidate for an instrumented test in a later hardening pass.

## Git Checkpoints (this branch)

1. `test: add reproducers for Podcast and Episode models` (RED)
2. `feat: implement Podcast and Episode models` (GREEN)
3. `test: add reproducers for iTunes mapper and RSS feed parser` (RED)
4. `feat: implement iTunes mapper, RSS feed parser, and podcast fetchers` (GREEN)
5. `test: add reproducers for podcast/episode Room DAOs` (RED)
6. `feat: implement Room schema for podcasts and episodes` (GREEN)
7. `test: add reproducer for PodcastRepository` (RED)
8. `feat: implement PodcastRepository` (GREEN)
9. `fix: deduplicate podcast search results by feed url` (RED→GREEN, bug found via manual verification)
10. `feat: wire podcast UI into app with tab-independent now-playing bar` (build GREEN, manual verification passed, includes the mini-player fix)
