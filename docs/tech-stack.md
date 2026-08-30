# Easy Radio — Technical Approach

## Language: Kotlin

Kotlin over Java or a cross-platform framework (Flutter/React Native), for three reasons:

1. **It's what the rest of the intended stack requires (or best supports).** Jetpack Compose, Media3, Room, and coroutines/Flow are all Kotlin-first — using Java would mean fighting the tooling for no benefit; using a cross-platform framework would mean losing direct access to them entirely.
2. **The hard part of this app is platform integration, not shared business logic.** MediaSession/MediaLibraryService plumbing, the Android Auto browse tree, and Wear OS controls all live deep in Android-specific APIs. A cross-platform framework buys the least where this app needs the most.
3. **It's Google's recommended, best-supported language for native Android**, which matters for long-term maintenance, library support, and documentation quality across Auto and Wear OS.

## Supporting stack (established in the implementation plan)

| Layer | Choice | Why |
|---|---|---|
| UI | Jetpack Compose | Modern declarative Android UI, required for clean Wear OS (Compose for Wear) reuse of patterns |
| Playback engine | Media3 (ExoPlayer + `MediaLibraryService`) | Single playback/session implementation shared across phone notification, Android Auto, and Wear OS controls |
| Local storage | Room | Subscriptions, episodes, downloads, playback position/speed |
| Async | Kotlin Coroutines + Flow | Standard pairing with Compose and Room |
| Networking | Retrofit/OkHttp | Radio-Browser API (station directory), iTunes Search API (podcast discovery), direct RSS/Atom feed parsing |
| DI | Hilt | Standard for multi-module Android projects |
| Module structure | Multi-module Gradle w/ version catalog | `app`, `wear`, `core/*`, `feature/*` — see implementation plan |

This is a living reference — update it if a stack decision changes during implementation.
