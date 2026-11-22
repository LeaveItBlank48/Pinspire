# Pinspire — Pixabay setup

1. Sign in at https://pixabay.com/api/docs/ to see your API key beside the `key` field.
2. Open the **root Pinspire folder** in Android Studio.
3. Find or create `local.properties` in that folder (next to `settings.gradle`).
4. Add a line: `pixabayApiKey=YOUR_ACTUAL_KEY` (no quotes, no spaces around `=`).
5. Sync Gradle, connect your Android phone, and press Run. The image feed now uses Pixabay.
6. Test: search `korean streetwear`, choose Fashion, scroll, open a result, and save it to a board.

The app keeps the existing UI, offline-created posts, editable posts, boards, and on-device interest scoring. Existing saved Wikimedia posts are unaffected. New Pixabay results contain source attribution; saving a result downloads a copy into app-private storage so the saved image remains available when the temporary search URL expires. The user's personal content is not deleted during an in-place signed update.

**Limitations:** Pixabay may have few images for niche searches, such as Korean streetwear. The app will show no results instead of substituting unrelated images. The API response cache is retained on-device for 24 hours, using app-private cache storage. Production deployments need additional robustness around cache eviction, rate limits, and error recovery. This student demo stores the API access key in the APK build; that key is recoverable by people who have the APK. Never put a secret key in an APK. A publicly released version needs a backend proxy that protects API credentials, enforces terms and rate limits, and implements a 24-hour cache. Review Pixabay's license and restrictions before redistribution or commercial release. This ZIP has not been emulator-tested or compiled here.
