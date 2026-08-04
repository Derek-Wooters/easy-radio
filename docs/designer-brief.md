# Easy Radio — Designer Brief

**Working title:** Easy Radio
**Platform:** Android phone (v1), with Android Auto and Wear OS companion screens to follow later
**Status:** Requesting screens for the first build phase (phone app only — radio + podcasts)

## 1. What the app does

A single Android app that combines two things people currently use separate apps for:
- **Live internet radio** — browse/search stations, play live streams. Primary test station is KFAN 100.3 (Minneapolis sports radio); the station directory should feel like it scales to hundreds of stations, not just one.
- **Podcasts** — subscribe to shows, browse episodes, download for offline, and listen — comparable to Pocket Casts.

Both content types share one player, one queue, and one mini-player, so a listener can flip from a live station to a podcast episode without leaving a persistent "now playing" bar.

## 2. Design direction & references

Three reference screenshots are in `referenceScreens/` — use them as the starting direction, not a pixel-for-pixel copy:

| File | What to borrow from it |
|---|---|
| `MainScreen.png` | iHeartRadio's home layout — horizontal "presets" shelf, live station cards with quick-play, recently-played shelf, bottom nav + persistent mini-player |
| `PocketCastMain.png` | Pocket Casts' podcast library — dense grid of square show artwork, minimal chrome |
| `SelectedPodcastScreen.png` | Pocket Casts' show detail — tabs (Now Playing / Episodes / Highlights / About), episode rows with listen/download actions |

Overall feel we're going for: **Pocket Casts' clean, content-forward density** applied across **both** radio and podcasts, rather than iHeart's busier, ad-supported layout. Treat the iHeart reference as a functional/layout reference (what modules exist on a home screen) more than a visual-style reference.

## 3. Platform & technical constraints

- Built in **Jetpack Compose** — components should be designed as reusable pieces (cards, list rows, chips, buttons) rather than one-off full-screen compositions.
- Must support **light and dark mode** — design both, or design in a way that a single palette maps cleanly to both (tokens/variables preferred over hardcoded per-screen colors).
- Must work across standard Android phone sizes (small phones to large phones/foldables) — avoid fixed-width layouts; design for how shelves/grids reflow.
- Live radio content needs a **"LIVE" indicator** distinct from podcast/on-demand content wherever the two appear together (search results, mini-player, now playing).
- Standard Android system chrome applies (status bar, gesture nav bar, system back) — don't design custom system bars.

## 4. Screens needed now (Priority 1)

These cover the phone-only build (radio + podcasts, no car or watch yet).

### Design system / foundations
- App icon
- Splash/launch screen
- Color palette — light + dark
- Typography scale
- Icon set: play, pause, skip ±15/30s, download, subscribe/follow, favorite/star, live badge, queue/up-next, search, settings

### Screens
1. **Home / Discover** — presets shelf, live radio dial, recently played, quick access into Stations/Podcasts/Playlists. *(ref: MainScreen.png)*
2. **Live Radio Browse** — searchable/filterable station list or grid (genre, city), each station card shows quick-play.
3. **Now Playing — Radio** — full-screen player: station artwork, name/tagline, live badge, favorite toggle, basic transport controls (no scrubber — it's live).
4. **Podcasts Library** — grid of subscribed show artwork. *(ref: PocketCastMain.png)*
5. **Podcast Show Detail** — episode list with listen/download actions, subscribe toggle, tabs for Episodes/About. *(ref: SelectedPodcastScreen.png)*
6. **Now Playing — Podcast** — full-screen player: artwork, scrubber/progress, skip ±15/30s, playback speed control, sleep timer entry point.
7. **Up Next / Queue** — reorderable list of what's playing next.
8. **Unified Search** — single search surface returning both stations and podcasts, clearly labeled by type.
9. **Downloads** — list of downloaded episodes with storage-used indicator, remove action.
10. **Favorites / My Stations** — saved/quick-access stations, separate from full directory browse.
11. **Settings** — theme toggle, download preferences (quality, wifi-only, auto-download rules), sleep timer defaults.
12. **Persistent mini-player** — bottom bar component, needs states for both "radio playing" and "podcast playing" (podcast state shows progress; radio state shows live badge instead).
13. **Onboarding (optional, nice-to-have)** — first-run pick-your-favorite-genres/stations flow.
14. **Empty & error states** — no downloads yet, no subscriptions yet, offline/no connection, stream failed to load.

## 5. Hold off on these (design later, not part of this request)

We'll come back for these once the phone app is built — no need to design them now:
- **Android Auto** — mostly Google's own templated UI; the only design need will be a couple of category icons and an artwork-safe-zone spec.
- **Wear OS** — compact now-playing screen, a simple picker list, and a complication icon. Scope (remote control of the phone vs. fully standalone) isn't locked yet, so designing this now risks rework.

## 6. Deliverable format

- Figma file, organized by the screen list above (one frame group per numbered screen), plus a dedicated page for the design system/foundations.
- Component variants for key states: default / pressed / disabled / loading, and light / dark.
- Icons as SVG.
- Any custom illustration/empty-state art exported as SVG or PNG @1x/2x/3x.
- Naming convention: match the screen numbers/names above so we can map files to feature modules directly.

## 7. Open questions for the designer

- Final app name/branding — "Easy Radio" is a working title only.
- Any preference between a tab-bar vs. rail navigation for the three main sections (Home / Podcasts / Library or similar)?
