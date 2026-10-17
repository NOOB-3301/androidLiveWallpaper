// Renders a plan the way the Player does (docs/CONTRACT.md "Drawing rules"), final frame by default.
//   node tools/preview.mjs plan.json out.png [width=540] [--t ms]
// Mirrors app/src/main/java/com/haiku/wall/render/{Ink,Player}.kt: same noise tiles (seed, hash, value noise),
// per-segment quads + bevel joins + round caps for strokes, even-odd areas clipped to the reveal circle,
// Patrick Hand text centred per line with a ground-coloured halo, inks painted through the tooth tile.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createCanvas, GlobalFonts, Path2D } from '@napi-rs/canvas';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
GlobalFonts.registerFromPath(path.join(ROOT, 'app/src/main/assets/fonts/hand.ttf'), 'Hand');

const T = 256;
const lerp = (a, b, t) => a + (b - a) * t;
const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
const smooth = (t) => t * t * (3 - 2 * t);
function hash(x, y, s) { // Kotlin Int arithmetic, as Ink.hash
  let h = (Math.imul(x, 374761393) + Math.imul(y, 668265263) + Math.imul(s, 1442695041)) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h & 0xffffff) / 16777216;
}
function vn(x, y, cell, s) {
  const p = T / cell, fx = (x + 0.5) / cell, fy = (y + 0.5) / cell, ix = Math.floor(fx), iy = Math.floor(fy);
  const tx = smooth(fx - ix), ty = smooth(fy - iy), x0 = ix % p, x1 = (ix + 1) % p, y0 = iy % p, y1 = (iy + 1) % p;
  return lerp(lerp(hash(x0, y0, s), hash(x1, y0, s), tx), lerp(hash(x0, y1, s), hash(x1, y1, s), tx), ty);
}
const rgbOf = (hex) => [1, 3, 5].map((i) => parseInt(hex.substr(i, 2), 16));
function lum(hex) { const [r, g, b] = rgbOf(hex).map((v) => v / 255); return 0.2126 * r + 0.7152 * g + 0.0722 * b; }

// [paper, ink0, ink1, ...] as canvases, like Ink.tiles
function tiles(plan) {
  const groundInt = (0xff000000 | parseInt(plan.ground.slice(1), 16)) | 0;
  const seed = (Math.imul(plan.total | 0, 31) + groundInt) | 0;
  const n = T * T, fine = new Float32Array(n), coarse = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const x = i % T, y = (i / T) | 0;
    const f = 0.45 * vn(x, y, 2, seed) + 0.35 * vn(x, y, 4, seed + 1) + 0.2 * hash(x, y, seed + 2);
    const c = 0.6 * vn(x, y, 16, seed + 3) + 0.4 * vn(x, y, 64, seed + 4);
    fine[i] = clamp01((f - 0.5) * 2.6 + 0.5);
    coarse[i] = clamp01((c - 0.5) * 2 + 0.5);
  }
  const m = plan.medium || {}, tooth = m.tooth || 0, grain = m.grain || 0, speckle = m.speckle || 0;
  const make = (fill) => {
    const cv = createCanvas(T, T), g = cv.getContext('2d'), img = g.createImageData(T, T);
    for (let i = 0; i < n; i++) { const [r, gg, b, a] = fill(i); img.data.set([r, gg, b, Math.round(clamp01(a) * 255)], i * 4); }
    g.putImageData(img, 0, 0);
    return cv;
  };
  const dark = lum(plan.ground) < 0.4;
  const out = [make((i) => { const a = 0.055 * (1 - fine[i]) ** 2 + 0.02 * (1 - coarse[i]); return dark ? [255, 255, 255, a * 0.6] : [0, 0, 0, a]; })];
  const alpha = new Float32Array(n);
  for (let i = 0; i < n; i++) {
    const drop = fine[i] < speckle * 0.5 || hash(i % T, (i / T) | 0, seed + 5) < speckle * 0.05;
    alpha[i] = drop ? 0 : lerp(1, fine[i], tooth) * lerp(1, coarse[i], grain);
  }
  for (const ink of plan.inks) { const [r, g, b] = rgbOf(ink); out.push(make((i) => [r, g, b, alpha[i]])); }
  return out;
}

export function render(plan, outW = 540, at = Infinity) {
  const s = outW / plan.w, outH = Math.round(plan.h * s);
  const cv = createCanvas(outW, outH), g = cv.getContext('2d');
  const tl = tiles(plan), pat = tl.map((t) => g.createPattern(t, 'repeat'));
  g.fillStyle = plan.ground;
  g.fillRect(0, 0, outW, outH);
  g.fillStyle = pat[0];
  g.fillRect(0, 0, outW, outH);
  const ink = (k, a) => {
    g.fillStyle = pat[k + 1];
    g.globalAlpha = a;
    g.globalCompositeOperation = plan.medium && plan.medium.blend === 'multiply' ? 'multiply' : 'source-over';
  };
  const end = at >= plan.total;
  for (const mk of plan.marks) {
    if (!end && mk.t0 > at) break;
    const d = mk.t1 - mk.t0, f = end || d <= 0 ? 1 : clamp01((at - mk.t0) / d);
    if (mk.k === 's') { ink(mk.ink, mk.a); g.fill(strokePath(mk.p, f, s), 'nonzero'); }
    else if (mk.k === 'a') {
      g.save();
      g.beginPath();
      g.arc(mk.cx * s, mk.cy * s, Math.max(0, mk.r * f * s), 0, Math.PI * 2);
      g.clip();
      g.beginPath();
      for (const l of mk.loops) {
        g.moveTo(l[0] * s, l[1] * s);
        for (let i = 2; i < l.length; i += 2) g.lineTo(l[i] * s, l[i + 1] * s);
        g.closePath();
      }
      ink(mk.ink, mk.a);
      g.fill('evenodd');
      g.restore();
    } else if (mk.k === 't') drawText(g, plan, f, s, pat);
  }
  g.globalAlpha = 1;
  g.globalCompositeOperation = 'source-over';
  return cv;
}

// The first fraction f of a stroke: per-segment quads, bevels on the outer side of bends, round caps at the ends.
function strokePath(p, f, s) {
  const P = new Path2D();
  const n = p.length / 3, c = [0];
  for (let i = 1; i < n; i++) c.push(c[i - 1] + Math.hypot(p[i * 3] - p[i * 3 - 3], p[i * 3 + 1] - p[i * 3 - 2]));
  const len = c[n - 1], s1 = f * len;
  const X = (i) => p[i * 3] * s, Y = (i) => p[i * 3 + 1] * s, Hw = (i) => p[i * 3 + 2] * 0.5 * s;
  for (let j = 0; j < n - 1; j++) {
    if (c[j] >= s1) break;
    const sl = c[j + 1] - c[j];
    if (sl < 1e-4) continue;
    if (j + 1 < n - 1 && c[j + 1] < s1) joint(P, j + 1, X, Y, Hw);
    const u1 = clamp01((s1 - c[j]) / sl), ex = X(j + 1) - X(j), ey = Y(j + 1) - Y(j), L = Math.hypot(ex, ey) || 1;
    const nx = -ey / L, ny = ex / L, x1 = X(j) + ex * u1, y1 = Y(j) + ey * u1, h0 = Hw(j), h1 = lerp(Hw(j), Hw(j + 1), u1);
    P.moveTo(X(j) - nx * h0, Y(j) - ny * h0);
    P.lineTo(x1 - nx * h1, y1 - ny * h1);
    P.lineTo(x1 + nx * h1, y1 + ny * h1);
    P.lineTo(X(j) + nx * h0, Y(j) + ny * h0);
    P.closePath();
  }
  circle(P, X(0), Y(0), Hw(0));
  if (f >= 1) circle(P, X(n - 1), Y(n - 1), Hw(n - 1));
  return P;
}
function circle(P, x, y, r) { P.moveTo(x + r, y); P.arc(x, y, r, 0, Math.PI * 2); P.closePath(); }
function joint(P, v, X, Y, Hw) {
  const l0 = Math.hypot(X(v) - X(v - 1), Y(v) - Y(v - 1)), l1 = Math.hypot(X(v + 1) - X(v), Y(v + 1) - Y(v));
  if (l0 < 1e-4 || l1 < 1e-4) return;
  const x = X(v), y = Y(v), h = Hw(v);
  const d0x = (x - X(v - 1)) / l0, d0y = (y - Y(v - 1)) / l0, d1x = (X(v + 1) - x) / l1, d1y = (Y(v + 1) - y) / l1;
  const cross = d0x * d1y - d0y * d1x, dot = d0x * d1x + d0y * d1y;
  if (dot < -0.5) { circle(P, x, y, h); return; }
  if (Math.abs(cross) < 1e-3) return;
  const sg = cross > 0 ? -h : h, ax = x - d0y * sg, ay = y + d0x * sg, bx = x - d1y * sg, by = y + d1x * sg;
  P.moveTo(x, y);
  if ((ax - x) * (by - y) - (ay - y) * (bx - x) >= 0) { P.lineTo(ax, ay); P.lineTo(bx, by); } else { P.lineTo(bx, by); P.lineTo(ax, ay); }
  P.closePath();
}

function drawText(g, plan, f, s, pat) {
  const w = plan.w, t = plan.text, lines = plan.haiku;
  g.font = '100px Hand';
  const widest = Math.max(0, ...lines.map((l) => g.measureText(l).width / 100));
  const fpx = widest > 0 ? Math.min(t.size * w, (0.9 * w) / widest) : t.size * w;
  g.font = `${fpx * s}px Hand`;
  const m = g.measureText('Hg'), asc = m.fontBoundingBoxAscent / s, desc = m.fontBoundingBoxDescent / s;
  const lh = 1.25 * fpx, total = lines.reduce((a, l) => a + l.length, 0);
  let shown = f * total, first = 0;
  g.textAlign = 'left';
  g.textBaseline = 'alphabetic';
  g.lineJoin = 'round';
  g.lineCap = 'round';
  lines.forEach((l, i) => {
    const base = t.y * plan.h + (i - (lines.length - 1) / 2) * lh + (asc - desc) / 2; // = Player's base - (fm.ascent + fm.descent)/2
    const adv = g.measureText(l).width / s, x0 = w / 2 - adv / 2;
    const c = Math.min(l.length, Math.max(0, shown - first));
    first += l.length;
    if (c <= 0) return;
    const xr = x0 + g.measureText(l.slice(0, Math.floor(c))).width / s + (c % 1) * (g.measureText(l[Math.floor(c)] || '').width / s);
    g.save();
    g.beginPath();
    g.rect((x0 - 0.25 * fpx) * s, (base - asc - 0.25 * fpx) * s, (xr - x0 + (c >= l.length ? 0.5 * fpx : 0.25 * fpx)) * s, (asc + desc + 0.5 * fpx) * s);
    g.clip();
    g.globalAlpha = 1;
    g.globalCompositeOperation = 'source-over';
    if (t.halo) { // ground colour, then the paper grain again, so the halo only hides marks under the letters
      g.lineWidth = 0.35 * fpx * s;
      g.strokeStyle = plan.ground;
      g.strokeText(l, x0 * s, base * s);
      g.strokeStyle = pat[0];
      g.strokeText(l, x0 * s, base * s);
    }
    g.fillStyle = pat[t.ink + 1];
    g.globalCompositeOperation = plan.medium && plan.medium.blend === 'multiply' ? 'multiply' : 'source-over';
    g.fillText(l, x0 * s, base * s);
    g.restore();
  });
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const args = process.argv.slice(2);
  const ti = args.indexOf('--t');
  const at = ti >= 0 ? +args.splice(ti, 2)[1] : Infinity;
  const [src, out, width] = args;
  if (!src || !out) { console.error('usage: node tools/preview.mjs plan.json out.png [width=540] [--t ms]'); process.exit(2); }
  const cv = render(JSON.parse(fs.readFileSync(src, 'utf8')), width ? +width : 540, at);
  fs.writeFileSync(out, cv.toBuffer('image/png'));
  console.log(out);
}
