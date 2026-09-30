# RatChef

Android app that turns an Instagram reel into a clean recipe: ingredients, short numbered steps, a portion
scaler, and a shopping list that merges amounts across recipes. No server needed.

## How it works

1. In Instagram tap **Share → RatChef** on a reel (or copy the link and paste it into the app).
2. The app loads the reel's public caption, the same way a browser does.
3. An **offline parser** on the phone turns the caption into a recipe (English and German): it recognises
   headers like *Ingredients / Zutaten / Method / Zubereitung*, emoji bullets, keycap steps like 1️⃣,
   comma lists, trailing amounts like "Mehl: 200 g", and ranges. It also filters out hashtags, macros and "follow for more" lines.
4. Only if the parser isn't sure (or you choose "Always"), the caption text goes to **Gemini** (free tier)
   for conversion. This is optional; without an API key everything stays offline.
5. With **Metric units** on (Settings, default), cups/oz/lb show as g or ml and °F as °C; tsp/tbsp stay.
6. Tap − / + to scale portions, then **Add to shopping list**. The list adds matching items together
   (e.g. 1 cup + 100 ml cream = 340 ml) and shows which recipe each item came from.

Many reels need an Instagram login before the caption can be read. You can **sign in to Instagram inside
RatChef** (Settings → Instagram). You type your password on Instagram's own login page, and the app keeps only the login
cookie on your phone. Signed in, the app requests the post's data the way instagram.com does in a browser. Instagram
doesn't like apps doing this, so keep it to personal use: it could flag the account, and it can stop working at any time.

If a caption still can't be read, the app asks you to paste the caption text. That always works.

## Build

**Android Studio:** open this folder, let Gradle sync, press Run. You need Android Studio Ladybug (2024.2)
or newer, and the phone must run Android 8.0 or later.

**No Android Studio:** every push to `main` runs the tests, builds the APK and publishes it as a GitHub
Release. The newest APK is always at `https://github.com/i-nazuma/RatChef/releases/latest/download/RatChef.apk`.

All builds are signed with the same key (`app/ratchef-sideload.jks`), so a new APK installs over the old one
and keeps your data. The key is only for sideloading; don't reuse it for a Play Store release.

**Command line:** `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
(signed with the debug key, which is fine for personal sideloading).

## Gemini key (optional)

Create a free key at https://aistudio.google.com/apikey and paste it in **Settings**. The default model is
`gemini-flash-lite-latest`, an alias Google keeps pointing at the current Flash-Lite model. If Google retires it, you can change the model in Settings.

## Code map

| Path | What |
| --- | --- |
| `core/RecipeParser.java` | Caption → recipe rules (plain JVM, unit-tested) |
| `core/Quantities.java`, `core/Units.java` | Amount parsing, unit aliases (EN/DE), ½-style formatting |
| `core/ShoppingMerger.java` | Adding up shopping-list items, g→kg / ml→l |
| `net/CaptionFetcher.kt` | Reel link → caption (signed-in media info, embed page, JSON, og:description) |
| `net/InstagramSession.kt`, `ui/InstagramLoginScreen.kt` | Optional Instagram sign-in (WebView cookies) |
| `core/Metric.java` | cups/oz/lb → g/ml, °F → °C, inches → cm |
| `net/GeminiClient.kt` | Optional AI conversion with a JSON schema |
| `data/Store.kt` | JSON files in private app storage, settings |
| `ui/*` | Jetpack Compose screens |

The parser core is plain Java so it runs in JVM unit tests: `./gradlew testDebugUnitTest`.

## Limits

- Instagram has no official API for captions. If Instagram changes its pages, automatic fetching can break;
  pasting the caption still works.
- Recipes that are only spoken in the video (not written in the caption) aren't captured.
- Google Keep has no public API for personal accounts, so the shopping list lives in the app. The Share button exports it as text.
