# Drawing kit (plan v1)

You write one plain JavaScript program (ES2019; no DOM, no imports, no async). It runs once, calls the functions below, and the kit turns the calls into a timed, stroke-by-stroke drawing that plays on a phone wallpaper, then writes the haiku last. The kit bakes the hand in: every line wanders slowly, swells in and tapers out, breathes in width, and is split into strokes no longer than one movement of the hand (420 px). You decide the picture; the kit makes it look drawn.

## Canvas
- `W` = 1080, `H` ≈ 2400 (from the phone's aspect). Pixels, origin top-left, y grows downward.
- `SAFE = {x0: 60, x1: 1020, y0: 0.12*H, y1: 0.88*H, w, h, cx, cy}`: the lock-screen clock covers the top 12% and the dock the bottom 12%. Keep the poem and the key marks inside; let backgrounds run to the edges.
- `haiku` (3 strings), `seed` (number), `TAU` = 2π. `Math.random` is seeded, so a seed always draws the same picture.
- Angles in helpers are radians, screen style: 0 → right, TAU/4 → down. `area` angles are degrees, same direction.
- Stroke parts outside the canvas are dropped and cost nothing, so a ring may hang off an edge.

## Setup (call first; order does not matter)
`medium({tooth, grain, speckle, blend, wander, taper})` all 0..1 (clamped); defaults tooth .35, grain .25, speckle 0, blend "normal", wander .5, taper .5.
- tooth: paper tooth showing through every mark (0 smooth ink, 1 dry chalk). grain: coarse blotchy pressure. speckle: random dropouts (glitter of gel ink, skips of chalk).
- blend: "multiply" makes overlapping inks darken like markers/watercolour; "normal" otherwise. Use multiply only on a light ground.
- wander: how far the hand drifts off the true path (≈3 px at 1). taper: pressure swell/taper (0 even like a technical pen, 1 brush pen).

`ground(hex)` paper colour, default "#F3F0E8". `inks([hex, ...])` 1-4 distinct colours, default ["#2B3A8C"]; marks pick one by index with `ink`.

## Marks
### `line(points, opts)`
`points`: `[[x, y], ...]` (≥2). They are knots of a smooth curve through every point. Options (default):
- `ink` 0 · `width` 2.5 px (0.3-80) · `alpha` 0.9
- `closed` false: joins back to the start, overlapping a little at the join; the start point is chosen at random.
- `sharp` false: true draws straight segments between the points (polygons, rectangles, zigzags, stars, lightning, snowflake arms). Without it, 4 rectangle corners become a round blob.
- `gaps` 0 (0-0.8): fraction of the line left out in random breaks of mixed length; 0.03-0.1 for a pen that skips, 0.3-0.5 for broken charcoal/dashed lines.
- `wander`, `taper`: override the medium for this line.
- `phase`: "outline" | "inner" | "detail" | "dark". Default "inner" if the line is ≥60 px long, else "detail".

### `area(loops, opts)`
`loops`: one loop `[[x,y],...]` or a list of loops `[[[x,y],...], ...]`; filled even-odd, so a loop inside another is a hole. Loops follow the same smooth curve as `line(points, {closed: true})`; pass `sharp: true` for polygons. Options shared by all modes: `ink` 0, `alpha`, `phase` (default "area"; any phase is allowed), `sharp` false.
- `mode: "wash"` (default): watercolour/ink wash. 3-5 translucent layers, each a few px off the line and warped, laid light to dark; each spreads as a circle from the loop's top point. `alpha` .5 = overall strength. Costs 3-5 marks whatever its size.
- `mode: "hatch"`: hand hatching. Rows `spacing` px apart (7) at `angle` degrees (-45 = rising to the right "/", 45 = "\", 0 = horizontal, 90 = vertical), strokes about `len` px long (40), `width` 1.8, `alpha` .85. The hand works in patches: neighbouring strokes share their ends, each patch turns by up to ±`jitter` degrees (4) and presses differently. Cross-hatch = a second call with another angle. Tone = spacing (smaller is darker) + alpha + layers.
- `mode: "stipple"`: dots on a `spacing` grid (9), kept with probability `density` (.7, modulated by slow noise so dots clump), dot size `width` (3), `alpha` .9.
- `mode: "side"`: the side of a chalk, crayon, pastel or pencil: broad soft strokes (`width` = 1.1×`spacing`, spacing 16), `len` 90, `angle` -30, `jitter` 6, `alpha` .3. Builds tone with visible directional streaks; layer two calls (different angle or ink) for rich colour.

### `text({y, size, ink, halo})` - exactly once
The haiku, 3 centred lines, font Patrick Hand, line height 1.25×font. `y` = centre of the block as a fraction of H (default .5); `size` = font px / W (default .07, clamped .035-.12; the player shrinks it if the widest line would pass 90% of W); `ink` 0; `halo` true = a ground-coloured outline under the letters hides drawing behind them. The block (3.75 × size × W tall) must sit inside SAFE vertically. The text ink must have WCAG contrast ≥4.5:1 against `ground` (not against your shapes), so put the poem over open ground or a halo, never on a busy dark patch you drew.

## Order and time (affects composition)
- Phases play in order: outline → inner → area → detail → dark → poem. Put the silhouette in "outline", inner contours and big line fields in "inner", hatching/washes/side tone in "area", small accents (dots, ticks, veins, sparkles) in "detail", the deepest accents last in "dark".
- Inside a phase, washes go first (faintest first), then the pen jumps to the nearest unused start, finishing what is above before moving down. With more than 800 marks, 4 hands draw at once, each on its own stretch of the path.
- Cost: stroke 10 + len^0.85, area 30 + 1.5×√(area in px²), travel 6 + 0.04×distance. The picture is scaled to fill the time before the poem; the poem gets max(1500 ms, 30%) at the end and appears letter by letter.
- So: a few long lines draw slowly and deliberately; thousands of hatch strokes rush. Balance big gestures with texture.

## Caps (errors stop the run)
- ≤ 6000 marks, ≤ 120000 points, ≤ 20000 line/area calls, ≤ 4 distinct inks, text once, contrast ≥ 4.5, text block inside SAFE.
- Budget: a line costs 1 mark per ~340 px; a stroke costs ~11 points, plus 1 per 5 px beyond 50 px. hatch ≈ area/(spacing×len) marks, side the same with its spacing, stipple ≈ area/spacing² × density. A 600×600 hatch at spacing 6, len 40 ≈ 1500 marks.
- Bad input throws an Error whose message names the call and the fix (e.g. `program line 12: area() call 4: mode "fill" unknown; use one of wash|hatch|stipple|side`, `text ink #A8A8A8 has contrast 2.3:1 on ground #FFFFFF, need >=4.5`). Fix exactly that and keep the rest.

## Helpers (return point lists; combine freely)
- `circle(cx, cy, r, n?)` closed loop (n ≈ circumference/10). `ellipse(cx, cy, rx, ry, rot?, n?)`.
- `arc(cx, cy, r, a0, a1, n?)` open, from angle a0 to a1. `polar(cx, cy, r, a)` → `[x, y]`.
- `wave(x0, y0, x1, amp, freq, phase?)` horizontal sine from x0 to x1 at y0; `freq` = whole waves across.
- `spiral(cx, cy, r0, r1, turns, a0?)` roses, curls, shells, swirls.
- `branch(x, y, a, len, {depth: 2, shoots: 4, spread: 0.7, shrink: 0.45, bend: 0, pairs: true})` → `[{pts, depth}]`: a stem (depth 0) with side shoots, recursively: ferns, frost, snowflake arms, twigs, veins, coral. shoots per stem, spread = shoot angle (rad), shrink = child/parent length, bend = total curl (rad), pairs = shoots on both sides. Count = Σ (shoots × 2)^k for k ≤ depth (depth 2, 4 shoots → 73 lines).
- `offset(points, d, closed?)` the line moved d px sideways (parallel copies, rims, double lines).
- `rand(s)` → seeded `r()` in [0,1) with `r.range(a, b)`. `noise(x, y)` smooth value noise in [0,1), features 1 unit wide (use `noise(x/150, y/150)`). `lerp(a, b, t)`, `clamp(x, lo, hi)`.

## Media (starting points)
| medium | ground / inks | medium() | marks |
|---|---|---|---|
| ballpoint | cream / blue-black | tooth .3 grain .2 wander .55 taper .5 | width 1.3-2.2, hatch spacing 5-8 |
| pencil | warm white / graphite #3A3A3E | tooth .65 grain .35 wander .5 taper .7 | width 1-2, alpha .5-.8, hatch + side for tone |
| charcoal | any / near-black | tooth .9 grain .5 speckle .2 wander .6 | width 3-8, gaps .2-.5, side |
| chalk on board | slate/black / pale chalks | tooth .8 grain .45 speckle .15 wander .6 taper .4 | width 3-6, side for colour |
| crayon | paper or coloured / waxy colours | tooth .8 grain .4 speckle .08 wander .6 taper .3 | width 3-5, side spacing 6-10 alpha .7-.9 |
| soft pastel | dark or tinted / light colours | tooth .7 grain .35 wander .6 taper .35 | width 5-14, side |
| gold or silver gel pen | dark ground / metallic | tooth .45 grain .2 speckle .25 wander .4 taper .3 | width 1.2-2.5 |
| brush pen | light / black + 1 colour | tooth .1 grain .15 wander .6 taper 1 | width 3-7, wash for colour |
| technical pen | light / black | tooth .1 grain .1 wander .25 taper 0 | width 1-2, sharp lines, even hatch |
| marker | white / 2-4 bright | tooth .15 grain .3 wander .4 taper .1 blend multiply | width 8-14, alpha .7 |

One medium per picture. Ink colours of one medium family; 1-2 inks often read best.

## Make it look hand-made
- Never perfect geometry. Jitter radii and positions with `r()` or `noise`; draw rings as several broken arcs or with `gaps` .03-.1; let concentric families vary in spacing.
- Vary width and alpha along a family of lines (e.g. `width: 1.2 + r()`), and press harder on the near/dark side.
- Build tone the way the medium does: hatch in 2-3 layers at different angles with rising alpha, side strokes for chalk/crayon/pastel, washes laid light to dark. Leave paper showing; do not fill everything.
- Fields of many similar lines (ripples, rain, strings, grain) look best when each line differs a little in length, angle, start and width.
- Let shapes overlap and run off the edges; strong diagonals and one clear focal shape beat even scatter.
- Keep the poem's area clear or covered by `halo: true`.

## Examples
```js
// ballpoint ripples
medium({ tooth: .3, wander: .6 }); ground('#F1EEE6'); inks(['#2B3A8C']);
var r = rand(seed);
for (var k = 1; k < 26; k++) line(circle(860, 560, k * 17 + r() * 5), { closed: true, width: 1.3 + r() * .6, gaps: .05 });
text({ y: .5, size: .07, ink: 0 });
```
```js
// pencil moon: outline, two hatch layers on the dark side, white pencil light
medium({ tooth: .65, grain: .35, taper: .7 }); ground('#EACDB2'); inks(['#3A3A3E', '#FBF6EE']);
var cx = 470, cy = 760, R = 230, m = [cx + 15, cy - 25];
line(circle(cx, cy, R), { closed: true, width: 1.2, alpha: .45, phase: 'outline' });
var dark = arc(cx, cy, R, -0.7, 2.6).concat([m]);
area(dark, { mode: 'hatch', angle: 75, spacing: 5, width: 1.3, alpha: .75, len: 34, jitter: 18 });
area(dark, { mode: 'hatch', angle: -50, spacing: 8, alpha: .5, len: 26, phase: 'detail' });
area(arc(cx, cy, R, 2.6, 5.6).concat([m]), { mode: 'side', ink: 1, angle: -40, spacing: 8, alpha: .55, len: 60, phase: 'detail' });
text({ y: .58, size: .085, ink: 0 });
```
```js
// gold gel snowflake on brown
medium({ tooth: .45, speckle: .25, wander: .4, taper: .3 }); ground('#3A1C12'); inks(['#D8B26A']);
for (var a = 0; a < 6; a++) branch(540, 700, a * TAU / 6 - TAU / 4, 320, { shoots: 5, shrink: .35 }).forEach(function (b) {
  line(b.pts, { width: b.depth ? 1.2 : 2, sharp: true, phase: b.depth ? 'detail' : 'inner' });
});
text({ y: .62, size: .085, ink: 0 });
```
