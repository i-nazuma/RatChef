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
5. Tap − / + to scale portions, then **Add to shopping list**. The list adds matching items together
   (e.g. 1 cup + 100 ml cream = 340 ml) and shows which recipe each item came from.

If Instagram won't serve a caption (some posts need a login), the app asks you to paste the caption text.
That path always works.

## Build

**Android Studio:** open this folder, let Gradle sync, press Run. You need Android Studio Ladybug (2024.2)
or newer, and the phone must run Android 8.0 or later.

**No Android Studio:** push the folder to a GitHub repo. The included workflow
(`.github/workflows/build.yml`) runs the tests and uploads an installable APK as a build artifact.

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
| `net/CaptionFetcher.kt` | Reel link → caption (embed page, JSON, og:description) |
| `net/GeminiClient.kt` | Optional AI conversion with a JSON schema |
| `data/Store.kt` | JSON files in private app storage, settings |
| `ui/*` | Jetpack Compose screens |

The parser core is plain Java so it runs in JVM unit tests: `./gradlew testDebugUnitTest`.

## Limits

- Instagram has no official API for captions. If Instagram changes its pages, automatic fetching can break;
  pasting the caption still works.
- Recipes that are only spoken in the video (not written in the caption) aren't captured.
- Google Keep has no public API for personal accounts, so the shopping list lives in the app. The Share button exports it as text.
