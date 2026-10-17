# Haiku Wall

AI-generated haiku poems drawn stroke by stroke on your Android home screen.

Every day (or hour), Claude writes a fresh haiku about your chosen theme. The app renders it as hand-drawn artwork, playing the animation once. Beautiful, minimal, runs in the background.

## Features

- **Fresh haiku daily** – themes: nature, seasons, love, solitude, and 12 more. Mix and match or set your own.
- **Animated hand-drawn art** – strokes appear in real time, no generic AI slop.
- **Lightweight** – runs in a separate process, draws only what changed, stops when done.
- **Configurable AI** – use Anthropic or any OpenAI-compatible endpoint.
- **Replay on unlock** – see the animation again when you come back to your home screen.

## Setup

1. Get an API key: [Anthropic](https://console.anthropic.com) or [OpenRouter](https://openrouter.ai).
2. Create `.env` in the project root:
   ```
   OPENROUTER_API_KEY=your-key
   OPENROUTER_MODEL=anthropic/claude-haiku-5.5
   ```
   Or leave empty to set it in the app later.
3. Build and install:
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Open the app, pick a theme, tap "Set as wallpaper".

## How It Works

1. **Generate** – the app calls the LLM every hour (or on demand) to write a haiku.
2. **Code** – the LLM also outputs a drawing program for that haiku.
3. **Render** – a JS sandbox runs the program, which produces drawing commands.
4. **Draw** – the wallpaper service plays the animation on your home screen.
5. **Judge** – an image judge scores the result; if it's too faint, regenerate.

## Architecture

- **Main process**: settings UI, LLM calls, job scheduling.
- **Wallpaper process**: animation playback, no network or JS.
- **Kit**: hand-drawn stroke model with swell, taper, and wander. Can layer 6000+ marks without slowdown.

## License

Apache 2.0 (except Patrick Hand font: OFL).

## Attribution

Art kit ported from [Katagami](https://katagami.ai/).
