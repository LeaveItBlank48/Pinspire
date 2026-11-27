# Pinspire 2.0

A visual discovery app for Android (Java). It has the familiar Pinterest loop (discover, save, organize) with its own **color-first** twist: every idea has a *Color DNA*, every board has a *mood*, and any board can become a shareable **moodboard collage**.

## What makes Pinspire different

| | Pinterest | Savee | **Pinspire** |
|---|---|---|---|
| Core idea | Algorithmic feed + boards | Minimal bookmarking for designers | **Inspiration → moods** |
| Color | — | — | **Color DNA** on every pin, browse & filter by color, tap a swatch to copy its hex or find ideas in that shade |
| Boards | Grid of pins | Grid of saves | **Board mood strip** (blended palette) + **Moodboard Studio**: free-form collage you can drag, pinch, rotate, recolor, then save or share as an image |
| Habit | Notifications | — | **Daily Spark**: one shared creative theme per day, with a streak for saving an idea from it |

## Features

**Discover**
- Masonry grid with real image proportions, infinite scroll, pull-to-refresh
- For you (learns locally from what you open/save/hide), Explore, 10 categories
- Color filter row on every feed
- Daily Spark card at the top of Discover
- Your own pins lead the For you feed
- Tap **pinspire.** (or Discover again when already at the top) for a fresh home feed

**Pins**
- Swipeable multi-image gallery, high-res image on the detail page
- Save to any board (bottom sheet; tap a checked board to remove), or long-press *Save* for one-tap save to your last board
- Share (image + link), download to *Pictures/Pinspire*
- Tags (tap to search), creator + license attribution, link to the original
- Color DNA swatches
- **Private notes** on saved pins
- *More like this* related grid
- *Not interested* removes it and tunes your For you feed

**Search**
- Recent searches, browse by color, idea chips, today's spark
- Results with a color filter

**Saved**
- Board covers (3-image collage) with mood strips; *Your creations* pseudo-board
- Board page: mood (tap for hex codes), Moodboard Studio, More ideas (based on the board's top tags), edit name/description, delete
- Remove (with Undo) or move pins between boards

**Create**
- Up to 10 photos per pin via the system photo picker; photos are copied into app storage so they survive the originals being moved/deleted
- Title, description, tags, topic, save straight to a board
- Edit / delete your own pins

## Image sources
- **Pixabay** (recommended) — add your key to `local.properties` (see `PIXABAY_SETUP.md`). Responses are cached for 24 hours as Pixabay requires.
- **No key? It still works.** Without a key, Pinspire uses **Wikimedia Commons** (free, no key). Results there are more varied/archival. Every image shows creator and license; check them before reusing an image.

Saved images are downloaded into private app storage, so boards keep working offline and after remote URLs expire.

## Stability changes in 2.0
- Rebuilt on RecyclerView + Fragments + ViewModel (AndroidX/Material 3); views are recycled instead of rebuilding the whole screen on every change
- Images loaded by Glide, downsampled to view size off the main thread (no more full-size decodes on the UI thread / out-of-memory crashes with camera photos)
- Feed and scroll position survive opening a pin, going back, and rotating the screen
- Stale network results are discarded when you switch feeds; errors show a message with *Try again* instead of failing silently; offline falls back to cached pages
- Corrupted saved data is **backed up, not wiped** (1.x silently reset everything)
- Edge-to-edge layout with proper system-bar and keyboard insets (targetSdk 35), light & dark theme
- Saved Pixabay/Commons pins removed from every board are cleaned up (with their files) on the next launch

## Upgrading from 1.x
Package name (`com.example.pinspire`) and the on-device storage format are unchanged, so installing 2.0 over 1.x with the **same signing key** keeps your boards, saved posts, created posts and learned interests. (Verified on an emulator: 1.x data → 2.0 upgrade.) Do not uninstall first — that deletes app data.

## Build & run
1. Open the project folder in Android Studio and let Gradle sync (needs Android SDK 35; dependencies download from Google/Maven Central).
2. Optional: add `pixabayApiKey=YOUR_KEY` to `local.properties`.
3. Pick your phone or an emulator and press **Run**, or `gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

Minimum Android 7.0 (API 24).

## Code map
```
app/src/main/java/com/example/pinspire/
  MainActivity.java          tabs, back stack, navigation, snackbars
  data/                      Pin, Board, PinRepository (storage), PixabayClient, CommonsClient,
                             ImageStore (files, gallery, share), Palettes (Color DNA), Discovery (categories, sparks)
  ui/                        Discover / Search / PinDetail / Boards / BoardDetail / Create / Studio screens,
                             SaveSheet, PinActions, Dialogs, ColorRow
  ui/feed/                   FeedViewModel (paging), FeedController (grid wiring), adapters
  ui/widget/                 RatioImageView, MoodCanvasView (studio canvas), AbstractArtDrawable
```

## Limitations
- No accounts or cloud sync: everything lives on the device (back up before uninstalling).
- A Pixabay key in an APK can be extracted; a public release should proxy API calls through a server.
- Personalization is a simple on-device heuristic, not machine learning.
