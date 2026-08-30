# Easy Radio — Implementation Plan

**Source request**: `plan.md`
**Related docs**: [`tech-stack.md`](./tech-stack.md), [`designer-brief.md`](./designer-brief.md)
**Status**: Approved direction — phases not yet started

## Requirements Restatement

Build a native Android app that:
- Streams **live internet radio**, with **KFAN 100.3** (Minneapolis sports radio) as the primary test station, extensible to a broader station directory
- Functions as a **podcast player/manager** comparable to Pocket Casts (subscribe, browse episodes, download, playback controls, queue)
- Runs on **phone** first, then becomes controllable from **Android Auto**, then from **Wear OS**
- Reuses visual direction from `referenceScreens/` (iHeartRadio-style home + live dial, Pocket Casts-style podcast grid and episode list)

## Pattern Grounding

At plan time the repo contained only `plan.md`, three reference screenshots, and an empty git init — no existing Android project or code conventions to mirror. Conventions are established from scratch using current Android best practice: Kotlin, Jetpack Compose, Media3, Hilt, Room, coroutines/Flow, multi-module Gradle with a version catalog. Full rationale in [`tech-stack.md`](./tech-stack.md).

Reference screenshots map to concrete screens:
| Screenshot | Maps to |
|---|---|
| `MainScreen.png` (iHeartRadio home) | Home/Discover screen — presets, live radio dial, recently played, mini-player |
| `PocketCastMain.png` | Podcasts Library grid screen |
| `SelectedPodcastScreen.png` | Podcast show detail / episode list screen |

## Architecture Decision

A single Media3 (`MediaLibraryService` + ExoPlayer) playback engine backs **all three surfaces** (phone notification, Android Auto browse/now-playing, Wear OS controls) — avoids three separate player implementations.

**Wear OS scope, open decision**: Wear OS can either (a) **remote-control the phone's session** over the Wearable Data Layer API (simpler, requires phone nearby via Bluetooth) or (b) be a **standalone player** with its own downloads/streaming over LTE/WiFi (matches Pocket Casts/Spotify, roughly doubles Wear-side work). Plan defaults to remote-control first, standalone as a stretch phase.

## Module Layout

```
easy-radio/
  app/                    # phone UI (Compose)
  wear/                   # Wear OS app (Compose for Wear)
  core/model/             # shared data models
  core/media/             # ExoPlayer + MediaLibraryService, shared by app + Auto
  core/database/          # Room: subscriptions, episodes, downloads, playback position
  core/network/           # Retrofit/OkHttp: station directory + podcast RSS + iTunes search
  core/designsystem/      # theme, components, icons
  feature/radio/
  feature/podcasts/
  feature/player/
  feature/library/
  feature/search/
  feature/settings/
```

Android Auto needs no separate module — enabled via manifest declarations in `app` plus the shared `core/media` `MediaLibraryService` browse tree.

## Phased Plan

Order was deliberately revised so the phone app (radio + podcasts) is fully working before vehicle/wearable surfaces are introduced — Phases 0–4 form a shippable "radio + podcasts on phone" v0.1 before Auto/Wear complexity starts.

### Phase 0 — Project Scaffolding
- Android Studio project, Kotlin, version catalog, multi-module Gradle setup above
- CI-friendly `./gradlew build` baseline, lint/ktlint config
- **Validate**: `./gradlew build` succeeds with empty modules

### Phase 1 — Core Playback Engine & Media Session (radio-first)
- `core/media`: ExoPlayer + `MediaLibraryService`, audio focus handling, becoming-noisy receiver, foreground service notification
- Hardcode KFAN's stream as the first playable item to prove the pipeline end-to-end
- Phone UI: minimal now-playing screen + mini-player
- **Risk to resolve here**: need KFAN's actual authorized stream URL
- **Validate**: play/pause/stop KFAN stream from a debug screen, survives backgrounding

### Phase 2 — Radio Station Directory
- `core/network`: integrate **Radio-Browser API** (free, crowdsourced, public stream URLs) for search/browse of stations beyond KFAN
- Local curated JSON fallback for KFAN + a handful of hand-picked stations
- Feature: Home screen (presets, live dial, recently played) per `MainScreen.png`
- **Validate**: search returns playable stations, favoriting persists

### Phase 3 — Podcast Engine
- `core/database`: Room schema for subscriptions, episodes, downloads, playback position/speed
- `core/network`: iTunes Search API for podcast discovery, direct RSS/Atom feed parsing per subscribed show
- Playback of podcast episodes through the same `core/media` engine (variable speed, skip-silence optional, resume position)
- **Validate**: subscribe to a real podcast, episode list populates from RSS, playback resumes where left off

### Phase 4 — Podcast Library UX (Pocket Casts parity)
- Podcasts grid screen (`PocketCastMain.png`)
- Show detail/episode list screen (`SelectedPodcastScreen.png`) — episodes, listen/download actions, sort/filter
- Up Next queue, downloads manager (Media3 `DownloadManager`), playback speed + skip 15/30s controls
- **Validate**: download an episode, play it offline, queue reorders correctly

### Phase 5 — Android Auto Integration
- Expose station/podcast browse tree via `MediaLibraryService.onGetChildren`
- Auto manifest metadata (`automotive_app_desc.xml`), content styling, icon/artwork sizing per Auto guidelines
- Test with Desktop Head Unit (DHU)
- **Validate**: browse + play stations/podcasts from DHU without phone screen interaction

### Phase 6 — Wear OS Companion App
- Wear module: remote-control phone's `MediaSession` via Wearable Data Layer `MessageClient`/`DataClient` (play/pause/skip/now-playing metadata sync)
- Compact now-playing screen + simple station/podcast picker
- **Validate**: control phone playback from watch, now-playing metadata reflects on watch within ~1s

### Phase 7 — Persistence & Settings
- Settings screen (theme, download quality/limits, auto-download rules, sleep timer)
- Playback position/favorites sync across the app's own storage (no backend in v1 — local Room only)
- **Validate**: force-kill app, relaunch, state (subscriptions, position, favorites) intact

### Phase 8 — Testing, Polish, Designer Handoff
- Unit tests for repositories/view models, instrumentation tests for playback service
- Manual QA pass across phone/Auto/Wear
- Confirm designer screen list delivered (see [`designer-brief.md`](./designer-brief.md))

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| No confirmed legal/authorized stream URL for KFAN 100.3 | High | Verify via station's own "listen live" page or Radio-Browser's public listing before hardcoding; do not scrape a private iHeartRadio endpoint |
| Wear OS scope (remote vs. standalone) affects Phase 6 estimate significantly | Medium | Defaulting to remote-control-first; revisit if standalone is wanted |
| Google Play policies for Android Auto apps (content rating, quality guidelines) | Medium | Budget review time before Play submission; not a dev blocker |
| Some podcast RSS feeds require specific User-Agent or block third-party players (dynamic ad insertion) | Medium | Handle per-feed request headers, graceful fallback error state |
| Background audio correctness (audio focus, foreground service, becoming-noisy) across 3 surfaces | Medium | Centralize entirely in `core/media`, test each surface against the same session |

## Estimated Complexity: LARGE

Phases 0–4 (phone app, radio + podcasts) is the first shippable milestone. Phases 5–6 (Auto, Wear) layer onto a `core/media` engine already proven against two content types. Phases 7–8 close out persistence and polish. Multi-week effort even as an MVP.

## Acceptance

- [ ] Phases 0–4 complete: phone app plays live radio (KFAN + directory) and podcasts (subscribe/download/queue)
- [ ] Phase 5 complete: full browse/playback from Android Auto DHU
- [ ] Phase 6 complete: Wear OS controls phone playback
- [ ] Phase 7 complete: state survives app restart
- [ ] Designer screens from `designer-brief.md` delivered and implemented
