# Pinspire — Pixabay setup (optional)

Pinspire works without any setup: with no key it loads images from Wikimedia Commons. Pixabay gives more modern, "aesthetic" photos and supports exact color filtering, so it is recommended.

1. Sign in at https://pixabay.com/api/docs/ and copy your API key (shown next to the `key` parameter).
2. Open `local.properties` in the project root (next to `settings.gradle`).
3. Replace the placeholder line with your real key: `pixabayApiKey=1234567-abcdef...` (no quotes, no spaces around `=`).
   The placeholder `YOUR_PIXABAY_API_KEY` is treated as "no key".
4. Sync Gradle and run the app again.

`local.properties` is in `.gitignore`, so the key is never committed. Note that a key built into an APK can be extracted by anyone who has the APK; a public release should call Pixabay through your own server instead.

API responses are cached on the device for 24 hours, as Pixabay's terms require. Saving a pin downloads a private copy of the image, because Pixabay's image URLs expire.
