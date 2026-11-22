# Pinspire — Android Java • Savee-inspired visual discovery

A Pinterest-style **offline-first Android app with live online image discovery**. This project extends the previous Pinspire source and keeps the `com.example.pinspire` application ID and existing on-device storage format.

## Features in this version
- Minimalist, two-column staggered-height image feed.
- **Online discovery:** Fetches new, real images from Wikimedia Commons while scrolling or tapping **Load more inspiration**. Requires internet; **no API key** or Firebase needed.
- **For you:** Picks categories using locally recorded preferences. On a fresh installation the feed explores several categories; later it favors categories whose posts you've opened or saved. This is a lightweight heuristic, **not machine learning** or Pinterest's algorithm.
- **Explore random:** Makes the feed choose topics at random rather than using preferences. Category chips let you browse Architecture, Interiors, Fashion, Art, Photography, Nature, Food, and Design.
- **Search online:** Enter a query and tap Search or the keyboard's search action. Images come from Wikimedia Commons search results, whose relevance varies by query.
- Open a post, view details and creator/license when available, and open its Wikimedia Commons source page.
- Save online images into existing boards. Saved image references and metadata are persisted in on-device SharedPreferences JSON.
- Create and rename/delete collections, add/remove posts from collections.
- Create local posts with multiple images selected from your Android photo picker; edit titles/descriptions/images or delete your own posts.
- Existing saved posts/boards remain compatible when updating the same app with the same signing key.

## Run on phone
1. Open the **Pinspire** directory in Android Studio; allow the Android Gradle Plugin to sync and install Android SDK 35 if needed.
2. Confirm Android Studio uses a Gradle JDK supported by your installed Gradle and plugin versions. This project specifies AGP 8.7.3 and Java source compatibility 17.
3. Select your connected Android phone (USB debugging enabled) and click **Run** to install the development build directly.
4. Or build a debug APK using `gradlew.bat assembleDebug` if you have a configured Gradle wrapper. The ZIP contains Gradle build files but **does not contain `gradle-wrapper.jar` or a generated wrapper script**; your existing configured project wrapper or a compatible local Gradle installation may be needed. `app/build/outputs/apk/debug/app-debug.apk` is the expected result.
5. To keep local data, install over your previous Pinspire install **using the same signing key and package name**. A different debug key may cause Android to reject the update. Do not uninstall without backing up your local data.

## How the feed works
1. `requestMore()` picks a category (random in Explore mode, interest-weighted in For you mode).
2. A background worker calls Wikimedia Commons' public MediaWiki API for image search results and 650px thumbnails.
3. The results are added to the two-column feed. Scrolling near the bottom requests another batch. The UI limits the visible live list to 100 posts for a modest footprint.
4. Viewing a post adds +1 to its category preference, saving adds +5, and Not interested subtracts 6. Those counters persist locally across restarts. For You selects a random category 30% of the time to keep variety.
5. Image thumbnails are loaded on background threads and temporarily cached in memory.

## Rights, privacy, and limitations
- Online images are provided by **Wikimedia Commons** and have different licenses. The post details screen shows available creator/license metadata and links to the source page. Check the image's license and attribution requirements before redistribution or commercial use. This app does not claim ownership of these images.
- Your local posts and boards are stored in your device's app data. Imported local photos use persisted document URI permissions where possible.
- **Not a networked social platform:** There are no accounts, follower relationships, cross-device sync, shared user uploads, personalized server-side recommendations, or Firebase yet.
- The online feed requires connectivity. If the Commons API is unavailable, the starter/local posts remain viewable, and you can retry loading.
- This is a **student project**, not a production-hardened app. A production version should use RecyclerView, more robust paging/caching, Room, access controls, and a moderation workflow.
- Search results may include irrelevant images, and providers' API policies or availability can change.
- **This ZIP was not compiled or installed on a phone in the provided environment.** Build and test on your hardware before relying on it for a presentation.

## 90-second demonstration
1. Open Discover, tap **Explore random**, and scroll to show new images loading.
2. Select **Interiors** or **Photography**; open an image to see the source credit.
3. Save an image to Favorites; repeat with a few photos in the same category.
4. Tap **For you**; explain that local counters favor categories with higher interaction, while still showing other topics.
5. Open Collections > Favorites to show saved posts; create a board and rename it.
6. Go to Create to publish your own multi-image post, then edit or delete it.

**Project entry point:** `app/src/main/java/com/example/pinspire/MainActivity.java`. The interface is implemented programmatically using Android native views, so this project does not have XML layout files.


## UI update: swipe gallery + logo home (November 2026)
- Open a post containing multiple images: **swipe left/right on its main photo** to move between them; a count and thumbnail selector are displayed. The old Next image button is replaced. Single-image posts behave as before.
- Tap **pinspire.** in the top-left corner from any screen to return to a clean Discover home. This clears the search and active category, switches back to For You, requests a fresh feed, and returns to the top. Your saved boards and locally learned interests remain unchanged.
- Pixabay API setup remains the same: add `pixabayApiKey=...` to your machine's `local.properties` in the project root. Never commit your key.
- This source ZIP has not been compiled on an Android emulator in this environment; build and test with Android Studio.

## Discover navigation reset update (November 2026)
- Tap **pinspire.** or the bottom **Discover** tab at any time to return to the default **For You** home, clear search/category filters, scroll to top, and retrieve a fresh first page.
- Tap **Explore random** to start a newly loaded random discovery feed (also clears searches and categories).
- Tap **For you** to return to the fresh personalized home. **New mix** refreshes whichever discovery mode is selected.
- Saved posts/boards and local interest preferences are not erased. No additional libraries are required.
- This update has been source-checked, not compiled or tested on a device here.
