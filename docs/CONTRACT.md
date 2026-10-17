# Haiku Live Wallpaper — build contract (v1)

Android live wallpaper. An LLM (OpenRouter) writes a haiku and a small JS program that draws it with a
hand-made medium (ballpoint, chalk, pencil, gold gel...). The JS runs once in a sandbox and produces a
timed **plan** (JSON). A headless wallpaper process plays the plan once as a stroke-by-stroke drawing,
then draws nothing until the next plan. Reference artwork: `docs/ref/*.jpg` (16 frames — the quality bar).

Package `com.haiku.wall`. Gradle module `app`. minSdk 28, compileSdk/targetSdk 35. Kotlin, no Rust/NDK/WebView.
No dependencies beyond the list in "Dependencies". JSON = `org.json` (platform) + `android.util.JsonReader`.

## Processes (hard rules)
- **main process**: Compose settings UI, WorkManager worker, OpenRouter calls, JS sandbox. Alive only on demand.
- **`:wallpaper` process** (`android:process=":wallpaper"` on the service): `HaikuWallpaperService` + `Player`.
  NO network, NO JS, NO Compose, NO WorkManager in code reachable from the service. Small memory: free the
  drawing bitmap when playback finishes; keep only `final.webp`-style redraw (see Player).
- The only link between them is files in `filesDir/` (same uid) written by `PlanStore`.

## Ownership (each agent edits ONLY its files)
| Area | Files | Owner |
|---|---|---|
| A app shell | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/**`, `gradlew*`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `AndroidManifest.xml`, `res/**`, `com/haiku/wall/{App,Config}.kt`, `ui/**`, `work/**`, `Prefs.kt` | A |
| B kit + JS runner | `assets/kit/kit.js`, `assets/prompts/kit-api.md`, `com/haiku/wall/js/**`, `tools/**` (node: preview, tests), `package.json` | B |
| C renderer + wallpaper | `com/haiku/wall/render/**`, `com/haiku/wall/wall/**`, `com/haiku/wall/store/PlanStore.kt` | C |
| D LLM pipeline | `com/haiku/wall/gen/**`, `assets/prompts/{system,direct,judge}.md` (NOT kit-api.md) | D |
| E examples (phase 2) | `assets/examples/**`, `assets/fallback/**`, `tools/build-fallback.mjs` | E |

If you need a dependency or manifest entry you don't own, append one line to `docs/REQUESTS.md`
(`<agent>: <what> — <why>`), do not edit the file.

## Dependencies (A puts exactly these in app/build.gradle.kts; versions pinned, known-good)
AGP 8.7.3, Gradle 8.10.2, Kotlin 2.0.21 (+ `org.jetbrains.kotlin.plugin.compose`), JDK 17.
`androidx.core:core-ktx`, `androidx.activity:activity-compose`, Compose BOM 2024.12.01 (`material3`, `ui`, `ui-tooling-preview`),
`androidx.lifecycle:lifecycle-runtime-ktx`, `androidx.work:work-runtime-ktx:2.10.0`,
`androidx.javascriptengine:javascriptengine:1.0.0`, `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`,
`org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0`.

## Config / .env (owner A)
Project-root `.env` has `OPENROUTER_API_URL=` (default `https://openrouter.ai/api/v1/chat/completions`),
`OPENROUTER_API_KEY=` and `OPENROUTER_MODEL=` (default `anthropic/claude-haiku-5.5`).
`app/build.gradle.kts` reads `.env` (then falls back to process env vars of the same names) and emits
`BuildConfig.OPENROUTER_API_URL`, `BuildConfig.OPENROUTER_API_KEY`, `BuildConfig.OPENROUTER_MODEL`.
Missing values must NOT fail the build.
```kotlin
object Config { fun apiUrl(): String; fun apiKey(): String; fun model(): String; fun judgeEnabled(): Boolean /* default true */ }
```

## Coordinates and screen
Plan canvas is `W = 1080`, `H = round(1080 * screenH / screenW)` (e.g. 2400). The Player scales uniformly with
`scale = max(sw/W, sh/H)` and centre-crops. SAFE area (keep text and key marks inside):
`x∈[60,1020]`, `y∈[H*0.12, H*0.88]` (lock-screen clock on top, nav/dock bottom).

## Plan JSON (v1) — produced by kit.js `__finish`, consumed by C
```jsonc
{ "v":1, "w":1080, "h":2400, "total":12000,            // total ms
  "ground":"#F3F0E8", "inks":["#2B3A8C","#C3272B"],     // 1..4 inks, index = mark.ink
  "medium":{"tooth":0.5,"grain":0.3,"speckle":0,"blend":"normal"},  // tooth/grain/speckle 0..1, blend normal|multiply
  "haiku":["line1","line2","line3"],
  "text":{"y":0.45,"size":0.07,"ink":0,"halo":true},    // y = centre of block as fraction of h; size = font px as fraction of w (Player shrinks to fit 90% width)
  "marks":[                                              // sorted by t0 ascending
   {"k":"s","ink":0,"a":0.9,"t0":0,"t1":300,"p":[x,y,w, x,y,w, ...]},          // stroke: polyline, per-point width px, n>=2 points. a = alpha 0..1
   {"k":"a","ink":1,"a":0.35,"t0":500,"t1":1400,"cx":300,"cy":900,"r":260,
        "loops":[[x,y,x,y,...],[...]]},                   // filled area (even-odd), revealed by a circle growing from (cx,cy) to radius r
   {"k":"t","t0":10000,"t1":12000}                        // the haiku text, revealed letter by letter, line by line, last mark
  ]}
```
Stipple dots are 2-point strokes. Hand wander, pressure swell/taper are baked into stroke points/widths by the kit.
The Player knows nothing about pens.

### Drawing rules (Player in Kotlin and `tools/preview.mjs` in node MUST agree)
- ground: fill with `ground`, add faint paper grain once.
- stroke: filled outline polygon built from the polyline and half-widths (not per-segment lines — alpha beads), round ends.
- area: fill loops with EVEN_ODD, clipped to circle(cx,cy,r*f).
- every ink paints through a repeating noise tile whose alpha = `lerp(1, noise, tooth)`, plus coarse `grain`
  and random `speckle` dropouts; `blend:"multiply"` uses MULTIPLY. Noise is generated procedurally (seeded), no image assets.
- text: font `assets/fonts/hand.ttf` (Patrick Hand, OFL). 3 lines centred on x=W/2, line height 1.25×font px, block centred at `text.y*h`.
  If `halo`, first stroke the glyph outlines in `ground` colour, width 0.35×font px, round join (hides picture under the letters), then fill in ink.
  Letters reveal left→right, line 1 then 2 then 3, progress = (t-t0)/(t1-t0) over total characters.
- Incremental playback: draw only marks' new slices each frame onto an offscreen bitmap, then copy the dirty rect to the surface
  (`SurfaceHolder.lockCanvas(dirty)`; pixels outside dirty are preserved — verified in Android docs). 30 fps cap via Choreographer.
  Stroke slice = polyline between old and new arc-length; area slice = ring between old and new radius; text slice = clip between old/new x.

## Kit (JS) — `assets/kit/kit.js`, plain ES2019 script, NO DOM, NO modules, runs in V8 sandbox and in node
Runner prepends `globalThis.__input = {W,H,haiku:[..3],seed,seconds};` then kit.js, then
`;(function(){ <PROGRAM> })();`, then `__finish()` whose value is the plan JSON string. Program globals (documented model-facing in
`assets/prompts/kit-api.md`, B owns that text and keeps it exact):
```
W,H,SAFE,haiku,seed
medium({tooth,grain,speckle,blend,wander,taper})   wander/taper 0..1 shape the hand (baked into points)
ground(hex)   inks([hex..max 4])
line(points,{ink,width,closed,alpha,phase})        phase: outline|inner|detail|dark  (draw order)
area(loops,{ink,mode,angle,spacing,alpha,phase})   mode: wash|hatch|stipple|side
text({y,size,ink,halo})                            at most once; always drawn last
helpers: circle(cx,cy,r,n) ellipse wave(x0,y0,x1,amp,freq) polar(cx,cy,r,a) arc rand(seed)->fn noise(x,y) lerp clamp TAU
```
Order of drawing (done()): phases outline → inner → area → detail → dark → text; inside a phase the pen goes to the
nearest next mark (small pull downward); several "hands" run in parallel when marks > 800. Poem gets ≥30% of total time and ≥1500 ms.
Hard caps (kit throws `Error` with a readable message, which becomes the repair prompt): marks ≤ 6000, total points ≤ 120000,
inks ≤ 4, text contrast vs ground ≥ 4.5:1, text inside SAFE, medium values clamped, coordinates clamped to [-W, 2W].
`__finish()` is also node-testable: `tools/` has `preview.mjs plan.json out.png` (uses `@napi-rs/canvas`, mirrors drawing rules) and `node --test`.

## Kotlin interfaces
```kotlin
// B  com.haiku.wall.js
object JsRunner { suspend fun run(input: String /*json for __input*/, program: String): String /*plan json*/ }  // throws JsError(message)
//   uses androidx.javascriptengine: heap cap 128MB, 10s timeout (close isolate), reads assets/kit/kit.js, new isolate per run, closes it.
//   If JavaScriptSandbox is unavailable -> throw JsError("JS sandbox not supported on this device").

// C  com.haiku.wall.render / store
class Plan { companion object { fun parse(json: String): Plan } ; val w:Int; val h:Int; val total:Long; val haiku:List<String>; val ground:Int; val inks:IntArray }
object PlanRenderer { fun renderFinal(plan: Plan, outW: Int, outH: Int): android.graphics.Bitmap }   // offscreen, same Player drawing code, for thumbnails/preview
object PlanStore {
  fun save(ctx: Context, planJson: String)               // atomic: tmp+rename to current.json; also history/<ms>.json, keep last 30
  fun currentJson(ctx: Context): String                  // current.json, else a random assets/fallback/*.json
  fun currentMtime(ctx: Context): Long
  fun history(ctx: Context): List<java.io.File>
  fun setReplayOnUnlock(ctx: Context, on: Boolean); fun replayOnUnlock(ctx: Context): Boolean   // flag file, readable cross-process
  fun recentHaiku(ctx: Context, n: Int): List<String>    // first lines of last n plans' haiku (variety memory)
}
// D  com.haiku.wall.gen
class GenerateException(msg: String, cause: Throwable? = null): Exception(msg, cause)
data class GenResult(val planJson: String, val haiku: List<String>)
object Pipeline { suspend fun generate(ctx: Context, theme: String, w: Int, h: Int, seconds: Int): GenResult }  // theme e.g. "rain, city night"
// A  com.haiku.wall.work
class GenerateWorker : CoroutineWorker     // reads Prefs (themes, custom, seconds), screen size, calls Pipeline.generate, PlanStore.save
object Scheduler { fun apply(ctx: Context, intervalHours: Int /*0 = off*/); fun generateNow(ctx: Context) }
```

## Pipeline (D) — quality is the product
1 **Direct**: model returns JSON `{haiku[3], image, medium, palette{ground,inks[]}, layout, mood}`. Haiku must be 5-7-5-ish, concrete image, a turn;
  avoids repeating `PlanStore.recentHaiku`. 2 **Compose**: system prompt (cache_control ephemeral) = kit-api.md + medium cards + layout notes +
  a "never" list + 4 example programs from `assets/examples/` (file header comments carry `medium|layout|haiku`); user = concept → JS program only.
3 **Run** via JsRunner; on `JsError` → repair call with the error text, max 2. 4 **Judge** (if `Config.judgeEnabled()`): render thumbnail with
`PlanRenderer.renderFinal(...,270,600)`, JPEG base64 data-URL image to the model, rubric (concept, cohesion/one medium, text legibility must be 5,
balance/safe areas, craft) 1-5 each; average < 4 → one revision through step 2 with the critique, then accept the better. Call
`https://openrouter.ai/api/v1/chat/completions` with `HttpURLConnection`, `Authorization: Bearer`, `cache_control` as a JSON body field on the
system content block (NOT an HTTP header), 120 s timeouts, parse JSON robustly (strip code fences). Never log the key.

## Non-goals (cut on purpose)
AGSL, RenderNode, NDK/Rust, parallax, binary plan format, server, accounts, ahead-of-time next plan, in-app key entry.
Each knowing shortcut gets a one-line comment `shortcut: <limit>, <when to upgrade>`.
