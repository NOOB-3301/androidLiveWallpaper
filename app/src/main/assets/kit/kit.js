/* Haiku Live Wallpaper drawing kit (plan v1). Plain ES2019 script: no modules, no DOM, no require.
 * Runs in androidx.javascriptengine (V8) and in node vm. Input: globalThis.__input = {W,H,haiku,seed,seconds}.
 * The runner appends the program as `;try{(function(){ PROGRAM })();}catch(e){__fail(e)}` and then calls
 * __finish(), which returns the plan JSON string described in docs/CONTRACT.md. Model-facing API: prompts/kit-api.md.
 *
 * The stroke model (Catmull-Rom knots, a slow two-wave hand wander, pressure swell-in and taper-out, breathing
 * width, strokes no longer than one reach of the hand, closed shapes overlapping at the join, washes laid light
 * to dark a little off the line, hatching cut into short wobbly strokes, nearest-next ordering with a downward
 * pull and the `10 + len^0.85` timing) is reimplemented from Katagami's diary-pen, silverpoint and chalkboard
 * kits by arni (https://arni.art), licensed under the Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0). Changes: compact rewrite, plan JSON output, phases, hands, caps.
 */
(function (G) {
  'use strict';
  var IN = G.__input || {};
  var TAU = Math.PI * 2;
  var W = Math.round(num(IN.W, 1080)), H = Math.round(num(IN.H, 2400));
  var S = W / 1080; // px constants below are tuned for a 1080-wide canvas
  var SEED = IN.seed == null ? '1' : String(IN.seed);
  var SECONDS = clamp(num(IN.seconds, 12), 4, 120);
  var HAIKU = Array.isArray(IN.haiku) ? IN.haiku.map(function (s) { return String(s); }) : [];
  var SAFE = { x0: Math.round(60 * S), x1: Math.round(W - 60 * S), y0: Math.round(H * 0.12), y1: Math.round(H * 0.88) };
  SAFE.w = SAFE.x1 - SAFE.x0; SAFE.h = SAFE.y1 - SAFE.y0; SAFE.cx = W / 2; SAFE.cy = (SAFE.y0 + SAFE.y1) / 2;
  var PHASES = ['outline', 'inner', 'area', 'detail', 'dark'];
  var MODES = ['wash', 'hatch', 'stipple', 'side'];
  var MAX_MARKS = 6000, MAX_POINTS = 120000, MAX_CALLS = 20000, MAX_RAW = 400000;
  var REACH = 420 * S; // longest single stroke: one movement of the hand
  var HANDS = 4, HANDS_AFTER = 800;

  // ------------------------------------------------------------------ numbers, randomness
  function num(v, d) { return typeof v === 'number' && isFinite(v) ? v : d; }
  function clamp(x, lo, hi) { if (lo == null) { lo = 0; hi = 1; } return x < lo ? lo : x > hi ? hi : x; }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function hash32(s) {
    var h = 2166136261;
    s = String(s);
    for (var i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
    return h >>> 0;
  }
  function rand(seedValue) {
    var a = hash32(seedValue == null ? SEED : seedValue);
    var r = function () {
      a = (a + 0x6D2B79F5) | 0;
      var t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
    r.range = function (lo, hi) { return lo + (hi - lo) * r(); };
    return r;
  }
  var lattice = (function () {
    var r = rand(SEED + ':noise'), g = new Float64Array(256 * 256);
    for (var i = 0; i < g.length; i++) g[i] = r();
    return g;
  })();
  // Smooth seeded value noise in [0,1); one unit = one lattice cell, so noise(x/120, y/120) has ~120px features.
  function noise(x, y) {
    y = y || 0;
    var ix = Math.floor(x), iy = Math.floor(y), fx = x - ix, fy = y - iy;
    var sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
    var x0 = ix & 255, y0 = iy & 255, x1 = (x0 + 1) & 255, y1 = (y0 + 1) & 255;
    var a = lattice[y0 * 256 + x0], b = lattice[y0 * 256 + x1], c = lattice[y1 * 256 + x0], d = lattice[y1 * 256 + x1];
    return a + (b - a) * sx + (c - a + (a - b + d - c) * sx) * sy;
  }
  var R = rand(SEED + ':kit'); // all kit randomness, consumed in call order, so a seed always draws the same
  Math.random = rand(SEED + ':math'); // programs that use Math.random stay deterministic too

  // ------------------------------------------------------------------ input checking
  function fail(msg) { throw new Error(msg); }
  function hexOf(v, where) {
    var m = typeof v === 'string' && /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(v.trim());
    if (!m) fail(where + ': ' + JSON.stringify(v) + ' is not a hex colour like "#2B3A8C"');
    var h = m[1].toUpperCase();
    if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
    return '#' + h;
  }
  function point(p, where, i) {
    var x, y;
    if (Array.isArray(p)) { x = p[0]; y = p[1]; } else if (p && typeof p === 'object') { x = p.x; y = p.y; }
    if (typeof x !== 'number' || typeof y !== 'number' || !isFinite(x) || !isFinite(y)) {
      fail(where + ': point ' + i + ' is ' + JSON.stringify(p) + ', expected [x, y] with finite numbers');
    }
    return [clamp(x, -W, 2 * W), clamp(y, -H, 2 * H)];
  }
  // [[x,y],...], [{x,y},...] or a flat [x,y,x,y,...]
  function points(v, where) {
    if (!Array.isArray(v)) fail(where + ': expected an array of [x, y] points, got ' + typeof v);
    var out = [], i;
    if (typeof v[0] === 'number') {
      if (v.length % 2) fail(where + ': a flat point list needs an even count of numbers, got ' + v.length);
      for (i = 0; i < v.length; i += 2) out.push(point([v[i], v[i + 1]], where, i / 2));
    } else for (i = 0; i < v.length; i++) out.push(point(v[i], where, i));
    raw += out.length;
    if (raw > MAX_RAW) fail(where + ': more than ' + MAX_RAW + ' input points in total; the plan cap is ' + MAX_POINTS + ' points, so draw fewer or simpler shapes');
    return out;
  }
  function loopsOf(v, where) {
    if (!Array.isArray(v) || !v.length) fail(where + ': expected a list of closed loops [[[x,y],...], ...]');
    var single = typeof v[0] === 'number' || (Array.isArray(v[0]) && typeof v[0][0] === 'number') || (v[0] && typeof v[0] === 'object' && !Array.isArray(v[0]));
    var list = single ? [v] : v, out = [];
    for (var i = 0; i < list.length; i++) {
      var l = points(list[i], where + ' loop ' + i);
      if (l.length >= 3) out.push(l);
    }
    if (!out.length) fail(where + ': every loop needs at least 3 points');
    return out;
  }
  function phaseOf(p, d, where) {
    if (p == null) return d;
    if (PHASES.indexOf(p) < 0) fail(where + ': phase ' + JSON.stringify(p) + ' unknown; use one of ' + PHASES.join('|'));
    return p;
  }
  function inkOf(v, where) {
    var k = v == null ? 0 : v;
    if (typeof k !== 'number' || k !== Math.floor(k) || k < 0 || k > 3) fail(where + ': ink must be an index 0..3 into inks([...]), got ' + JSON.stringify(v));
    return k;
  }

  // ------------------------------------------------------------------ state + public API
  var raw = 0, calls = [], textSpec = null, finished = null;
  var med = { tooth: 0.35, grain: 0.25, speckle: 0, blend: 'normal', wander: 0.5, taper: 0.5 };
  var groundHex = '#F3F0E8', inkList = ['#2B3A8C'];

  function medium(o) {
    o = o || {};
    ['tooth', 'grain', 'speckle', 'wander', 'taper'].forEach(function (k) { if (typeof o[k] === 'number' && isFinite(o[k])) med[k] = clamp(o[k], 0, 1); });
    if (o.blend != null) med.blend = o.blend === 'multiply' ? 'multiply' : 'normal';
  }
  function ground(hex) { groundHex = hexOf(hex, 'ground()'); }
  function inks(list) {
    if (!Array.isArray(list) || list.length < 1 || list.length > 4) fail('inks(): expected 1-4 hex colours like ["#2B3A8C", "#C3272B"], got ' + (Array.isArray(list) ? list.length + ' colours' : JSON.stringify(list)));
    var out = list.map(function (c, i) { return hexOf(c, 'inks() colour ' + i); });
    out.forEach(function (c, i) { if (out.indexOf(c) !== i) fail('inks(): ' + c + ' appears twice; the inks must be distinct colours'); });
    inkList = out;
  }
  function addCall(c) {
    if (calls.length >= MAX_CALLS) fail('more than ' + MAX_CALLS + ' line()/area() calls; the plan holds at most ' + MAX_MARKS + ' marks, so draw fewer, longer lines');
    c.n = calls.length + 1;
    calls.push(c);
  }
  function line(pts, o) {
    o = o || {};
    var where = 'line() call ' + (calls.length + 1);
    var p = points(pts, where);
    if (p.length < 2) fail(where + ': needs at least 2 points, got ' + p.length);
    addCall({
      kind: 'line', pts: p, ink: inkOf(o.ink, where), closed: !!o.closed && p.length > 2, sharp: !!o.sharp,
      width: clamp(num(o.width, 2.5), 0.3, 80), alpha: clamp(num(o.alpha, 0.9), 0.02, 1),
      wander: o.wander == null ? null : clamp(num(o.wander, 0.5), 0, 1), taper: o.taper == null ? null : clamp(num(o.taper, 0.5), 0, 1),
      gaps: clamp(num(o.gaps, 0), 0, 0.8), phase: phaseOf(o.phase, null, where),
    });
  }
  var AREA_DEFAULTS = {
    wash: { alpha: 0.5 },
    hatch: { spacing: 7, width: 1.8, len: 40, alpha: 0.85, angle: -45, jitter: 4 },
    stipple: { spacing: 9, width: 3, density: 0.7, alpha: 0.9 },
    side: { spacing: 16, len: 90, alpha: 0.3, angle: -30, jitter: 6 }, // width defaults to 1.1 x spacing
  };
  function area(loops, o) {
    o = o || {};
    var where = 'area() call ' + (calls.length + 1);
    var mode = o.mode == null ? 'wash' : o.mode;
    if (MODES.indexOf(mode) < 0) fail(where + ': mode ' + JSON.stringify(mode) + ' unknown; use one of ' + MODES.join('|'));
    var d = AREA_DEFAULTS[mode], spacing = clamp(num(o.spacing, d.spacing || 8), 1.5, 400);
    addCall({
      kind: 'area', mode: mode, loops: loopsOf(loops, where).map(function (l) { return o.sharp ? l : smoothLoop(l); }), ink: inkOf(o.ink, where), phase: phaseOf(o.phase, 'area', where),
      alpha: clamp(num(o.alpha, d.alpha), 0.02, 1), angle: num(o.angle, d.angle || 0) * Math.PI / 180,
      spacing: spacing, width: clamp(num(o.width, mode === 'side' ? spacing * 1.1 : d.width || 2), 0.3, 120),
      len: clamp(num(o.len, d.len || 40), 4, 2000), jitter: clamp(num(o.jitter, d.jitter || 0), 0, 90) * Math.PI / 180,
      density: clamp(num(o.density, d.density || 0.7), 0, 1),
    });
  }
  function text(o) {
    if (textSpec) fail('text() called twice; call it exactly once (the poem is always drawn last)');
    o = o || {};
    textSpec = { y: num(o.y, 0.5), size: num(o.size, 0.07), ink: inkOf(o.ink, 'text()'), halo: o.halo !== false };
  }

  // ------------------------------------------------------------------ geometry helpers (also public)
  function polar(cx, cy, r, a) { return [cx + r * Math.cos(a), cy + r * Math.sin(a)]; }
  function arc(cx, cy, r, a0, a1, n) {
    n = n || Math.max(4, Math.ceil(Math.abs(a1 - a0) * r / 10));
    var out = [];
    for (var i = 0; i <= n; i++) out.push(polar(cx, cy, r, lerp(a0, a1, i / n)));
    return out;
  }
  function ellipse(cx, cy, rx, ry, rot, n) {
    rot = rot || 0;
    n = n || Math.max(12, Math.min(240, Math.round(TAU * Math.sqrt((rx * rx + ry * ry) / 2) / 10)));
    var c = Math.cos(rot), s = Math.sin(rot), out = [];
    for (var i = 0; i < n; i++) {
      var a = TAU * i / n, x = rx * Math.cos(a), y = ry * Math.sin(a);
      out.push([cx + x * c - y * s, cy + x * s + y * c]);
    }
    return out;
  }
  function circle(cx, cy, r, n) { return ellipse(cx, cy, r, r, 0, n); }
  function wave(x0, y0, x1, amp, freq, phase) {
    var n = Math.max(2, Math.ceil(Math.abs(x1 - x0) / 8)), out = [];
    for (var i = 0; i <= n; i++) out.push([lerp(x0, x1, i / n), y0 + amp * Math.sin(TAU * freq * i / n + (phase || 0))]);
    return out;
  }
  function spiral(cx, cy, r0, r1, turns, a0) {
    var n = Math.max(8, Math.ceil(turns * TAU * Math.max(r0, r1) / 10)), out = [];
    for (var i = 0; i <= n; i++) { var t = i / n; out.push(polar(cx, cy, lerp(r0, r1, t), (a0 || 0) + TAU * turns * t)); }
    return out;
  }
  // Each point moved d px along the line's normal (left of the drawing direction is negative).
  function offset(pts, d, closed) {
    var n = pts.length, out = [];
    for (var i = 0; i < n; i++) {
      var a = pts[closed ? (i - 1 + n) % n : Math.max(0, i - 1)], b = pts[closed ? (i + 1) % n : Math.min(n - 1, i + 1)];
      var dx = b[0] - a[0], dy = b[1] - a[1], L = Math.hypot(dx, dy) || 1;
      out.push([pts[i][0] - dy / L * d, pts[i][1] + dx / L * d]);
    }
    return out;
  }
  // A stem with side shoots, recursively: ferns, frost, snowflake arms, twigs, veins. Returns [{pts, depth}],
  // depth 0 = the stem. Count = sum over k<=depth of (shoots * (pairs ? 2 : 1))^k.
  var helperR = rand(SEED + ':helpers');
  function branch(x, y, a, len, o) {
    o = o || {};
    var maxD = clamp(Math.round(num(o.depth, 2)), 0, 4), shoots = clamp(Math.round(num(o.shoots, 4)), 0, 12);
    var spread = num(o.spread, 0.7), shrink = num(o.shrink, 0.45), bend = num(o.bend, 0), pairs = o.pairs !== false;
    var out = [];
    (function grow(x, y, a, len, d) {
      var n = Math.max(2, Math.ceil(len / 12)), pts = [[x, y]], ang = a, px = x, py = y;
      for (var i = 1; i <= n; i++) { ang += bend / n; px += Math.cos(ang) * len / n; py += Math.sin(ang) * len / n; pts.push([px, py]); }
      out.push({ pts: pts, depth: maxD - d });
      if (d <= 0) return;
      for (var k = 1; k <= shoots; k++) {
        var t = clamp((k + helperR.range(-0.2, 0.2)) / (shoots + 1), 0.05, 0.95), p = pts[Math.round(t * n)];
        var sides = pairs ? [1, -1] : [k % 2 ? 1 : -1];
        for (var j = 0; j < sides.length; j++) {
          grow(p[0], p[1], a + bend * t + sides[j] * spread * helperR.range(0.9, 1.1), len * shrink * (1 - 0.5 * t) * helperR.range(0.85, 1.15), d - 1);
        }
      }
    })(x, y, a, len, maxD);
    return out;
  }

  // ------------------------------------------------------------------ the pen
  // Centripetal Catmull-Rom through the knots (or straight segments when sharp), sampled every ~1.5 px.
  function dense(knots, closed, sharp) {
    var K = closed ? knots.concat([knots[0]]) : knots, n = K.length, xs = [], ys = [];
    function push(x, y) { xs.push(x); ys.push(y); }
    function at(i) {
      if (closed) { var m = n - 1; return K[((i % m) + m) % m]; }
      if (i < 0) return [2 * K[0][0] - K[1][0], 2 * K[0][1] - K[1][1]];
      if (i > n - 1) return [2 * K[n - 1][0] - K[n - 2][0], 2 * K[n - 1][1] - K[n - 2][1]];
      return K[i];
    }
    for (var i = 0; i < n - 1; i++) {
      var p1 = K[i], p2 = K[i + 1], seg = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]), k = Math.max(1, Math.ceil(seg / 1.5)), j, u;
      if (sharp || n < 3) { for (j = 0; j < k; j++) { u = j / k; push(lerp(p1[0], p2[0], u), lerp(p1[1], p2[1], u)); } continue; }
      var p0 = at(i - 1), p3 = at(i + 2);
      var t1 = Math.max(1e-3, Math.sqrt(Math.hypot(p1[0] - p0[0], p1[1] - p0[1]))), t2 = t1 + Math.max(1e-3, Math.sqrt(seg)), t3 = t2 + Math.max(1e-3, Math.sqrt(Math.hypot(p3[0] - p2[0], p3[1] - p2[1])));
      for (j = 0; j < k; j++) {
        var t = lerp(t1, t2, j / k), c = [0, 1];
        for (var q = 0; q < 2; q++) {
          var A1 = ((t1 - t) * p0[q] + t * p1[q]) / t1, A2 = ((t2 - t) * p1[q] + (t - t1) * p2[q]) / (t2 - t1), A3 = ((t3 - t) * p2[q] + (t - t2) * p3[q]) / (t3 - t2);
          var B1 = ((t2 - t) * A1 + t * A2) / t2, B2 = ((t3 - t) * A2 + (t - t1) * A3) / (t3 - t1);
          c[q] = ((t2 - t) * B1 + (t - t1) * B2) / (t2 - t1);
        }
        push(c[0], c[1]);
      }
    }
    push(K[n - 1][0], K[n - 1][1]);
    return { x: xs, y: ys };
  }
  // The same smooth curve line(points, {closed: true}) draws, as a loop of points every ~4 px.
  function smoothLoop(l) {
    var d = dense(l, true, false), out = [];
    for (var i = 0; i < d.x.length - 1; i += 3) out.push([d.x[i], d.y[i]]);
    return out.length >= 3 ? out : l;
  }
  function cumLen(x, y) {
    var s = [0];
    for (var i = 1; i < x.length; i++) s.push(s[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]));
    return s;
  }
  // One pen line: knots -> dense path -> hand wander -> gaps and canvas clip -> strokes of at most one reach.
  // Returns stroke marks {p:[x,y,w,...], len}.
  function penLine(knots, o) {
    if (o.closed) { var k0 = Math.floor(R() * knots.length); knots = knots.slice(k0).concat(knots.slice(0, k0)); } // each loop starts somewhere new
    var d = dense(knots, o.closed, o.sharp), X = d.x, Y = d.y, i;
    if (o.closed && X.length > 4) { // carry on a little past the start so the loop overlaps at the join
      var L0 = cumLen(X, Y), ov = Math.min(L0[L0.length - 1] * 0.06, (6 + 14 * o.wander) * S);
      for (i = 1; i < X.length && L0[i] <= ov; i++) { X.push(X[i]); Y.push(Y[i]); }
    }
    var s = cumLen(X, Y), amp = 3.2 * S * o.wander;
    var f1 = TAU / ((220 + R() * 130) * S), f2 = TAU / ((70 + R() * 45) * S), ph0 = R() * TAU, ph1 = R() * TAU, ph2 = R() * TAU;
    var keep = [], gapOn = true, gapLeft = o.gaps > 0 ? R() * 40 * S : Infinity, m = 10 * S + o.width;
    for (i = 0; i < X.length; i++) {
      X[i] += amp * (Math.sin(s[i] * f1 + ph0) + 0.5 * Math.sin(s[i] * f2 + ph1));
      Y[i] += amp * (Math.cos(s[i] * f1 * 1.3 + ph1) + 0.5 * Math.sin(s[i] * f2 * 0.8 + ph2));
      if (i && o.gaps > 0) {
        gapLeft -= s[i] - s[i - 1];
        if (gapLeft <= 0) {
          gapOn = !gapOn;
          var g = (6 + R() * 18) * S * (1 + 2 * o.gaps); // longer breaks for a more broken line, dashes of very mixed length
          gapLeft = gapOn ? g * (1 - o.gaps) / o.gaps * R.range(0.3, 1.7) : g;
        }
      }
      keep.push(gapOn && X[i] > -m && X[i] < W + m && Y[i] > -m && Y[i] < H + m);
    }
    // The hand lifts at most every reach; each lift is one pressure stroke. Gaps and the canvas edge only hide parts
    // of it (with a quick narrowing at the break), so a broken line keeps the swell of the stroke it belongs to.
    var out = [], cuts = reachCuts(X, Y, s, o);
    for (var c = 0; c < cuts.length; c++) {
      var a = cuts[c][0], xs = X.slice(a, cuts[c][1]), ys = Y.slice(a, cuts[c][1]), ss = cumLen(xs, ys), wd = pressure(ss, o), run = -1;
      var alpha = clamp(o.alpha * R.range(0.92, 1.03), 0.02, 1);
      for (i = 0; i <= xs.length; i++) {
        if (i < xs.length && keep[a + i]) { if (run < 0) run = i; continue; }
        if (run >= 0 && i - run >= 2) out.push(stroke(xs, ys, ss, wd, run, i, o, alpha));
        run = -1;
      }
    }
    return out;
  }
  // Index ranges [a, b) of the strokes a line is drawn in: cut where it is longer than one reach of the hand, at the
  // sharpest turn near each cut; pieces overlap a little, so the tapered ends cross instead of leaving a thin spot.
  function reachCuts(X, Y, s, o) {
    var L = s[s.length - 1];
    if (L <= REACH) return [[0, X.length]];
    var parts = Math.ceil(L / (REACH * 0.8)), cuts = [0], back = Math.ceil(Math.max(6 * S, o.width * 2) / 1.5), out = [], i;
    for (var k = 1; k < parts; k++) {
      var target = L * (k + R.range(-0.15, 0.15)) / parts, win = L / parts * 0.12, bi = -1, bt = -1;
      for (i = 3; i < X.length - 3; i++) {
        if (Math.abs(s[i] - target) > win) continue;
        var t = Math.abs(Math.atan2(Y[i + 3] - Y[i], X[i + 3] - X[i]) - Math.atan2(Y[i] - Y[i - 3], X[i] - X[i - 3]));
        t = Math.min(t, TAU - t);
        if (t > bt) { bt = t; bi = i; }
      }
      if (bi <= cuts[cuts.length - 1]) { for (bi = 0; bi < s.length && s[bi] < target; bi++); }
      if (bi > cuts[cuts.length - 1] && bi < X.length - 1) cuts.push(bi);
    }
    cuts.push(X.length - 1);
    for (i = 0; i < cuts.length - 1; i++) out.push([Math.max(0, cuts[i] - (i ? back : 0)), cuts[i + 1] + 1]);
    return out;
  }
  // Pressure along one stroke: swell in, taper out, breathe +-(8..20)%.
  function pressure(s, o) {
    var L = s[s.length - 1], w = o.width, tp = o.taper, wd = [];
    var tl = Math.max(1, Math.min(L * 0.42, w * (2 + 10 * tp))), lo = 1 - 0.75 * tp, br = 0.08 + 0.12 * tp;
    var fb = TAU / ((30 + R() * 30) * S), ph = R() * TAU;
    for (var i = 0; i < s.length; i++) {
      var e = Math.max(0, Math.min(1, s[i] / tl, (L - s[i]) / tl));
      wd.push(w * (lo + (1 - lo) * Math.sin(e * Math.PI / 2)) * (1 + br * Math.sin(s[i] * fb + ph)));
    }
    return wd;
  }
  // Points [i0, i1) of a stroke resampled every few px; an end cut by a gap or the canvas edge narrows quickly.
  function stroke(X, Y, s, wd, i0, i1, o, alpha) {
    var L = s[i1 - 1] - s[i0], step = clamp(L / 10, 0.8, 5 * S), cut = 1.5 * o.width + 1, c0 = i0 > 0, c1 = i1 < X.length, p = [], next = s[i0];
    for (var i = i0; i < i1; i++) {
      if (s[i] < next && i !== i1 - 1) continue;
      next = s[i] + step;
      var f = Math.min(1, c0 ? 0.6 + 0.4 * (s[i] - s[i0]) / cut : 1, c1 ? 0.6 + 0.4 * (s[i1 - 1] - s[i]) / cut : 1);
      p.push(X[i], Y[i], Math.max(0.35, wd[i] * f));
    }
    return { k: 's', ink: o.ink, a: alpha, p: p, len: L };
  }

  // ------------------------------------------------------------------ areas
  function bounds(loops) {
    var b = { x0: Infinity, y0: Infinity, x1: -Infinity, y1: -Infinity };
    loops.forEach(function (l) { l.forEach(function (q) { b.x0 = Math.min(b.x0, q[0]); b.y0 = Math.min(b.y0, q[1]); b.x1 = Math.max(b.x1, q[0]); b.y1 = Math.max(b.y1, q[1]); }); });
    return b;
  }
  function polyArea(l) {
    var a = 0;
    for (var i = 0, j = l.length - 1; i < l.length; j = i++) a += (l[j][0] + l[i][0]) * (l[j][1] - l[i][1]);
    return Math.abs(a / 2);
  }
  // Even-odd spans of the loops along parallel rows at angle a: [{v, u0, u1}] in rotated coords (u along a).
  function scan(loops, a, gap, jitterRow) {
    var ca = Math.cos(a), sa = Math.sin(a);
    var rot = loops.map(function (l) { return l.map(function (q) { return [q[0] * ca + q[1] * sa, -q[0] * sa + q[1] * ca]; }); });
    var v0 = Infinity, v1 = -Infinity;
    rot.forEach(function (l) { l.forEach(function (q) { v0 = Math.min(v0, q[1]); v1 = Math.max(v1, q[1]); }); });
    // rows outside the canvas are skipped: project the canvas corners onto the row axis
    var cv = [[0, 0], [W, 0], [0, H], [W, H]].map(function (q) { return -q[0] * sa + q[1] * ca; });
    v0 = Math.max(v0, Math.min.apply(null, cv) - gap); v1 = Math.min(v1, Math.max.apply(null, cv) + gap);
    var out = [], row = 0;
    for (var v = v0 + gap * R.range(0.2, 0.8); v < v1; v += gap * R.range(1 - jitterRow, 1 + jitterRow)) {
      var xs = [];
      rot.forEach(function (l) {
        for (var i = 0; i < l.length; i++) {
          var p = l[i], q = l[(i + 1) % l.length];
          if ((p[1] <= v) !== (q[1] <= v)) xs.push(p[0] + (v - p[1]) * (q[0] - p[0]) / (q[1] - p[1]));
        }
      });
      xs.sort(function (m, n) { return m - n; });
      for (var i = 0; i + 1 < xs.length; i += 2) if (xs[i + 1] - xs[i] > 1) out.push({ v: v, u0: xs[i], u1: xs[i + 1], i: row });
      row++;
    }
    return { rows: out, at: function (u, v) { return [u * ca - v * sa, u * sa + v * ca]; } };
  }
  function inCanvas(q, m) { return q[0] > -m && q[0] < W + m && q[1] > -m && q[1] < H + m; }
  // Hatching the way a hand does it: the hand rests and lays a patch of near-parallel strokes side by side (the rows
  // of one band share their stroke ends, give or take a few px), then turns a little and presses differently for the
  // next patch. Each stroke is a slightly bowed, wandering, tapered pen stroke; ends over- or under-shoot the edge.
  function strokesAlong(c, o, out) {
    var sc = scan(c.loops, c.angle, c.spacing, 0.15), m = 30 * S + c.len, k = clamp(Math.round(c.len / c.spacing), 2, 14);
    var umin = Infinity, umax = -Infinity, bands = {}, inset = o.inset ? c.width * 0.4 : 0;
    sc.rows.forEach(function (row) { umin = Math.min(umin, row.u0); umax = Math.max(umax, row.u1); });
    function band(b) {
      if (bands[b]) return bands[b];
      var ends = [umin - R() * c.len], turns = [], press = [];
      while (ends[ends.length - 1] < umax) ends.push(ends[ends.length - 1] + c.len * R.range(0.7, 1.3));
      for (var j = 0; j < ends.length; j++) { turns.push(c.jitter * R.range(-1, 1)); press.push(R.range(0.75, 1.1)); }
      return (bands[b] = { ends: ends, turns: turns, press: press });
    }
    sc.rows.forEach(function (row) {
      var B = band(Math.floor(row.i / k)), lo = row.u0 + R.range(-2, 2) * S + inset, hi = row.u1 + R.range(-1, 3) * S - inset;
      for (var j = 0; j < B.ends.length - 1; j++) {
        var u0 = Math.max(lo, B.ends[j] + R.range(-0.08, 0.08) * c.len);
        var u1 = Math.min(hi, B.ends[j + 1] + R.range(-0.08, 0.08) * c.len - (o.gap ? R.range(1.5, 5) : -R.range(2, 6)) * S);
        if (u1 - u0 < 3 * S) continue;
        var half = (u1 - u0) / 2, mid = sc.at(u0 + half, row.v + (R() - 0.5) * c.spacing * 0.3);
        var ang = c.angle + B.turns[j] + R.range(-0.035, 0.035), dx = Math.cos(ang) * half, dy = Math.sin(ang) * half;
        var a = [mid[0] - dx, mid[1] - dy], b = [mid[0] + dx, mid[1] + dy], bow = R.range(-0.9, 0.9) * S * Math.min(1, half / 15);
        if (!inCanvas(a, m) && !inCanvas(b, m)) continue;
        var pieces = penLine([a, [mid[0] - Math.sin(ang) * bow, mid[1] + Math.cos(ang) * bow], b], {
          ink: c.ink, width: c.width * R.range(0.8, 1.2), alpha: clamp(c.alpha * B.press[j] * R.range(0.85, 1.05), 0.02, 1), wander: med.wander * o.wander, taper: Math.max(o.taper, med.taper), gaps: 0,
        });
        for (var i = 0; i < pieces.length; i++) out.push(pieces[i]);
      }
    });
  }
  function stipple(c, out) {
    var sc = scan(c.loops, 0, c.spacing, 0.1);
    sc.rows.forEach(function (row) {
      for (var u = row.u0 + R() * c.spacing; u < row.u1; u += c.spacing * R.range(0.7, 1.3)) {
        var q = sc.at(u + (R() - 0.5) * c.spacing * 0.6, row.v);
        if (!inCanvas(q, 4) || R() > c.density * (0.55 + 0.9 * noise(q[0] / 90 + 3.1, q[1] / 90))) continue;
        var w = c.width * R.range(0.6, 1.35), t = R() * TAU, l = w * R.range(0.1, 0.5);
        out.push({ k: 's', ink: c.ink, a: clamp(c.alpha * R.range(0.8, 1.05), 0.02, 1), p: [q[0], q[1], w, q[0] + Math.cos(t) * l, q[1] + Math.sin(t) * l, w * 0.9], len: l });
      }
    });
  }
  // A wash: 3-5 translucent layers, each a few px off the line and warped by slow noise, light to dark.
  function wash(c) {
    var n = 3 + Math.floor(R() * 3), per = 1 - Math.pow(1 - c.alpha, 1 / n), layers = [];
    var loops = c.loops.map(function (l) { return resampleLoop(l, 8 * S); });
    for (var i = 0; i < n; i++) {
      var ang = R() * TAU, off = R.range(1.5, 5) * S, amp = R.range(1, 3) * S, sc = R.range(40, 100) * S, nx = R() * 200, ny = R() * 200;
      var moved = loops.map(function (l) {
        return l.map(function (q) {
          return [q[0] + Math.cos(ang) * off + (noise(q[0] / sc + nx, q[1] / sc) - 0.5) * 2 * amp, q[1] + Math.sin(ang) * off + (noise(q[0] / sc, q[1] / sc + ny) - 0.5) * 2 * amp];
        });
      });
      var top = moved[0][0];
      moved.forEach(function (l) { l.forEach(function (q) { if (q[1] < top[1]) top = q; }); });
      var r = 0;
      moved.forEach(function (l) { l.forEach(function (q) { r = Math.max(r, Math.hypot(q[0] - top[0], q[1] - top[1])); }); });
      layers.push({ k: 'a', ink: c.ink, a: clamp(per * (0.75 + 0.5 * i / Math.max(1, n - 1)), 0.01, 1), cx: top[0], cy: top[1], r: r + 4 * S, loops: moved });
    }
    return layers;
  }
  function resampleLoop(l, step) {
    var out = [];
    for (var i = 0; i < l.length; i++) {
      var a = l[i], b = l[(i + 1) % l.length], k = Math.max(1, Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / step));
      if (k === 1 && out.length && Math.hypot(a[0] - out[out.length - 1][0], a[1] - out[out.length - 1][1]) < step * 0.5) continue;
      for (var j = 0; j < k; j++) out.push([lerp(a[0], b[0], j / k), lerp(a[1], b[1], j / k)]);
    }
    return out.length >= 3 ? out : l;
  }

  // ------------------------------------------------------------------ building the plan
  function lum(hex) {
    var v = [1, 3, 5].map(function (i) {
      var c = parseInt(hex.substr(i, 2), 16) / 255;
      return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
  }
  function contrast(a, b) { var x = lum(a), y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); }

  function build() {
    var units = {}, marks = 0, pts = 0;
    PHASES.forEach(function (p) { units[p] = []; });
    calls.forEach(function (c) {
      var where = c.kind + '() call ' + c.n;
      if (c.ink >= inkList.length) fail(where + ': ink ' + c.ink + ' does not exist; inks() declared ' + inkList.length + ' colour(s), so use ink 0..' + (inkList.length - 1));
      var made = [];
      if (c.kind === 'line') {
        var wander = c.wander == null ? med.wander : c.wander, taper = c.taper == null ? med.taper : c.taper;
        made = penLine(c.pts, { ink: c.ink, width: c.width, alpha: c.alpha, closed: c.closed, sharp: c.sharp, wander: wander, taper: taper, gaps: c.gaps });
        var L = 0;
        made.forEach(function (m) { L += m.len; });
        var ph = c.phase || (L >= 60 * S ? 'inner' : 'detail');
        made.forEach(function (m) { units[ph].push(unitOf([m], 10 + Math.pow(m.len, 0.85))); });
      } else if (c.mode === 'wash') {
        made = wash(c);
        var size = c.loops.reduce(function (s, l) { return s + polyArea(l); }, 0), u = unitOf(made, 30 + Math.sqrt(size) * 1.5);
        u.wash = contrast(inkList[c.ink], groundHex);
        units[c.phase].push(u);
      } else {
        if (c.mode === 'hatch') strokesAlong(c, { wander: 0.4, taper: 0, gap: true }, made);
        else if (c.mode === 'side') strokesAlong(c, { wander: 0.5, taper: 0.75, gap: false, inset: true }, made); // the side of a stick lands and lifts softly
        else stipple(c, made);
        made.forEach(function (m) { units[c.phase].push(unitOf([m], 10 + Math.pow(m.len, 0.85))); });
      }
      c.marks = made.length;
      made.forEach(function (m) { pts += m.p ? m.p.length / 3 : m.loops.reduce(function (s, l) { return s + l.length; }, 0); });
      marks += made.length;
    });
    if (marks > MAX_MARKS || pts > MAX_POINTS) {
      var top = calls.slice().sort(function (a, b) { return b.marks - a.marks; }).slice(0, 3).map(function (c) {
        return c.kind + '() call ' + c.n + (c.mode ? ' (' + c.mode + ', spacing ' + Math.round(c.spacing) + ')' : '') + ' made ' + c.marks + ' marks';
      });
      fail('plan has ' + marks + ' marks (max ' + MAX_MARKS + ') and ' + pts + ' points (max ' + MAX_POINTS + '). Biggest: ' + top.join('; ') +
        '. Raise hatch/stipple/side spacing or len, shrink areas, draw fewer or shorter lines, or keep shapes inside the canvas.');
    }
    return { units: units, marks: marks };
  }
  function unitOf(ms, cost) {
    var a = ms[0], z = ms[ms.length - 1];
    return { marks: ms, cost: cost, sx: a.p ? a.p[0] : a.cx, sy: a.p ? a.p[1] : a.cy, ex: z.p ? z.p[z.p.length - 3] : z.cx, ey: z.p ? z.p[z.p.length - 2] : z.cy };
  }
  // The pen goes to the nearest unused start, with a small pull that finishes what is above before going down
  // (cost = distance + 0.25 * dy). A grid keeps it fast for thousands of marks.
  function nearestOrder(list, at) {
    var n = list.length;
    if (n < 2) return list.slice();
    var C = 96 * S, x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity, i;
    list.forEach(function (u) { x0 = Math.min(x0, u.sx); y0 = Math.min(y0, u.sy); x1 = Math.max(x1, u.sx); y1 = Math.max(y1, u.sy); });
    var gw = Math.floor((x1 - x0) / C) + 1, gh = Math.floor((y1 - y0) / C) + 1, cells = [];
    for (i = 0; i < gw * gh; i++) cells.push([]);
    function cellOf(x, y) { return [clamp(Math.floor((x - x0) / C), 0, gw - 1), clamp(Math.floor((y - y0) / C), 0, gh - 1)]; }
    list.forEach(function (u, k) { var c = cellOf(u.sx, u.sy); cells[c[1] * gw + c[0]].push(k); });
    var out = [], ax = at[0], ay = at[1], maxR = Math.max(gw, gh);
    while (out.length < n) {
      var c0 = cellOf(ax, ay), best = -1, bc = Infinity, bcell = -1;
      for (var r = 0; r <= maxR; r++) {
        for (var cy = c0[1] - r; cy <= c0[1] + r; cy++) {
          if (cy < 0 || cy >= gh) continue;
          var edge = cy === c0[1] - r || cy === c0[1] + r;
          for (var cx = c0[0] - r; cx <= c0[0] + r; cx += edge ? 1 : 2 * r) {
            if (cx >= 0 && cx < gw) {
              var cell = cells[cy * gw + cx];
              for (var j = 0; j < cell.length; j++) {
                var u = list[cell[j]], cost = Math.hypot(u.sx - ax, u.sy - ay) + 0.25 * (u.sy - ay);
                if (cost < bc) { bc = cost; best = j; bcell = cy * gw + cx; }
              }
            }
            if (r === 0) break;
          }
        }
        if (best >= 0 && bc <= 0.75 * r * C) break;
      }
      var cl = cells[bcell], pick = list[cl[best]];
      cl[best] = cl[cl.length - 1]; cl.pop();
      out.push(pick);
      ax = pick.ex; ay = pick.ey;
    }
    return out;
  }

  function finish() {
    if (finished) return finished;
    if (!textSpec) fail('the program never called text(); call text({y, size, ink, halo}) exactly once');
    if (HAIKU.length !== 3) fail('__input.haiku must hold 3 lines, got ' + HAIKU.length);
    if (textSpec.ink >= inkList.length) fail('text(): ink ' + textSpec.ink + ' does not exist; inks() declared ' + inkList.length + ' colour(s)');
    var tInk = inkList[textSpec.ink], cr = contrast(tInk, groundHex);
    if (cr < 4.5) fail('text ink ' + tInk + ' has contrast ' + cr.toFixed(1) + ':1 on ground ' + groundHex + ', need >=4.5; pick a darker or lighter text ink, or another ground');
    // Width needs no check here: the Player shrinks the font until the widest line fits 0.9 W by real glyph metrics
    // (Patrick Hand averages ~0.36 em per char, so the contract's 0.46 estimate would shrink poems below the refs).
    var size = clamp(textSpec.size, 0.035, 0.12), px = size * W, bh = 3 * 1.25 * px, cy = textSpec.y * H;
    if (cy - bh / 2 < SAFE.y0 - 0.5 || cy + bh / 2 > SAFE.y1 + 0.5) {
      fail('text block spans y ' + Math.round(cy - bh / 2) + '..' + Math.round(cy + bh / 2) + ' (size ' + size + ' = ' + Math.round(px) + 'px font) but SAFE is y ' +
        SAFE.y0 + '..' + SAFE.y1 + '; use text.y between ' + ((SAFE.y0 + bh / 2) / H).toFixed(3) + ' and ' + ((SAFE.y1 - bh / 2) / H).toFixed(3));
    }
    var b = build(), hands = b.marks > HANDS_AFTER ? HANDS : 1, t = 0, at = [W * 0.3, H * 0.1], all = [];
    PHASES.forEach(function (ph) {
      var list = b.units[ph];
      if (!list.length) return;
      var washes = list.filter(function (u) { return u.wash != null; }).sort(function (a, c) { return a.wash - c.wash; });
      var ord = washes.concat(nearestOrder(list.filter(function (u) { return u.wash == null; }), at)), sum = 0, prev = at;
      ord.forEach(function (u) { u.gap = 6 + Math.hypot(u.sx - prev[0], u.sy - prev[1]) * 0.04; prev = [u.ex, u.ey]; sum += u.gap + u.cost; });
      at = prev;
      var hand = 0, acc = 0, clock = [], i;
      for (i = 0; i < hands; i++) clock.push(t);
      ord.forEach(function (u, k) { // several hands each take one stretch of the path, so their strokes stay near each other
        if (hand < hands - 1 && acc >= sum * (hand + 1) / hands) { hand++; u.gap = 6; }
        acc += u.gap + u.cost;
        clock[hand] += u.gap; u.t0 = clock[hand]; clock[hand] += u.cost; u.t1 = clock[hand];
        var D = u.t1 - u.t0, n = u.marks.length;
        u.marks.forEach(function (m, j) { // a wash's layers overlap in time, each later layer starting a bit later
          m.t0 = n > 1 ? u.t0 + D * 0.35 * j / n : u.t0;
          m.t1 = n > 1 ? u.t0 + D * (0.6 + 0.4 * (j + 1) / n) : u.t1;
          all.push(m);
        });
      });
      t = Math.max.apply(null, clock);
    });
    var total = Math.round(SECONDS * 1000), adv = 0;
    HAIKU.forEach(function (l) { adv += l.length * 0.46 * px; });
    var textCost = 40 + adv * 2, poem = all.length ? Math.min(Math.max(1500, 0.3 * total, total * textCost / (textCost + t)), Math.max(1500, 0.6 * total)) : total;
    poem = Math.round(Math.min(poem, total));
    var pic = total - poem, f = t > 0 ? pic / t : 0;
    var out = all.map(function (m) {
      var t0 = Math.min(pic - 1, Math.round(m.t0 * f)), t1 = Math.min(pic, Math.max(t0 + 1, Math.round(m.t1 * f)));
      if (m.k === 's') return { k: 's', ink: m.ink, a: r2(m.a), t0: t0, t1: t1, p: m.p.map(r1) };
      return { k: 'a', ink: m.ink, a: r2(m.a), t0: t0, t1: t1, cx: r1(m.cx), cy: r1(m.cy), r: r1(m.r), loops: m.loops.map(function (l) { var q = []; l.forEach(function (v) { q.push(r1(v[0]), r1(v[1])); }); return q; }) };
    });
    out.sort(function (a, c) { return a.t0 - c.t0; });
    out.push({ k: 't', t0: pic, t1: total });
    var plan = {
      v: 1, w: W, h: H, total: total, ground: groundHex, inks: inkList,
      medium: { tooth: r2(med.tooth), grain: r2(med.grain), speckle: r2(med.speckle), blend: med.blend },
      haiku: HAIKU, text: { y: Math.round(textSpec.y * 1000) / 1000, size: Math.round(size * 1000) / 1000, ink: textSpec.ink, halo: textSpec.halo }, marks: out,
    };
    // ASCII only, so the result can be read back in chunks without splitting a character
    finished = JSON.stringify(plan).replace(/[\u007f-￿]/g, function (ch) { return '\\u' + ('000' + ch.charCodeAt(0).toString(16)).slice(-4); });
    return finished;
  }
  function r1(v) { return Math.round(v * 10) / 10; }
  function r2(v) { return Math.round(v * 100) / 100; }

  // A program error re-thrown with the program's own line number, so a repair prompt can point at it.
  function failProgram(e) {
    var msg = e && e.message != null ? e.message : String(e), name = e && e.name && e.name !== 'Error' ? e.name + ': ' : '';
    var frames = String((e && e.stack) || '').split('\n'), end = G.__KIT_END, line = 0;
    for (var i = 0; i < frames.length && end; i++) {
      var m = /:(\d+):\d+\)?\s*$/.exec(frames[i]);
      if (m && +m[1] > end + 1) { line = +m[1] - end - 1; break; }
    }
    throw new Error((line ? 'program line ' + line + ': ' : '') + name + msg);
  }

  var api = {
    W: W, H: H, SAFE: SAFE, haiku: HAIKU.slice(), seed: IN.seed == null ? 1 : IN.seed, TAU: TAU,
    medium: medium, ground: ground, inks: inks, line: line, area: area, text: text,
    circle: circle, ellipse: ellipse, arc: arc, wave: wave, spiral: spiral, polar: polar, branch: branch, offset: offset,
    rand: rand, noise: noise, lerp: lerp, clamp: clamp, __finish: finish, __fail: failProgram,
  };
  for (var k in api) G[k] = api[k];
})(globalThis);
// keep this the last line: the runner maps error line numbers to program lines from it
var __KIT_END = (function () { var m = /:(\d+):\d+\)?\s*$/.exec(String(new Error().stack).split('\n')[1] || ''); return m ? +m[1] : 0; })();
