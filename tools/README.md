# tools (node 22)

```sh
npm install                                   # @napi-rs/canvas for previews
node --test tools/                            # kit tests (= npm test); writes tools/out/smoke.png and smoke-half.png (40% of the time)
node tools/run-program.mjs prog.js plan.json [--haiku "a|b|c"] [--seconds 12] [--seed 1] [--w 1080] [--h 2400]
node tools/preview.mjs plan.json out.png [width=540] [--t ms]   # final frame, or the frame at t ms
```

- `run-program.mjs` runs `app/src/main/assets/kit/kit.js` + a program in a fresh `vm` context with the same script
  `JsRunner.kt` builds (`compose()` in both must stay identical; Android reads the result back in slices).
  Importable: `runProgram({program, haiku, seed, seconds, W, H})` returns the plan JSON or throws `Error(message)`.
- `preview.mjs` mirrors `render/Ink.kt` + `render/Player.kt` (same noise tiles, stroke quads/bevels/caps, area reveal
  circle, Patrick Hand text with the grained halo). Importable: `render(plan, width, atMs)` returns a canvas.
- `fixtures/*.js` are kit regression programs in the style of `docs/ref` (ballpoint rings, pencil moon and pear,
  gold gel frost, chalk grate, crayon window, pastel monsoon, technical pen, charcoal ripples, marker ink drop).
  They are not the LLM's few-shot examples (those live in `assets/examples/`).
- The model-facing API is `app/src/main/assets/prompts/kit-api.md`; keep it in step with kit.js.

Build steps
1. JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug   
2. adb install -r app/build/outputs/apk/debug/app-debug.apk  
