// node --test tools/   — kit caps, validation, ordering/timing, determinism, and a rendered smoke frame.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { runProgram, KIT_PATH } from './run-program.mjs';
import { render } from './preview.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const run = (program, o = {}) => JSON.parse(runProgram({ program, ...o }));
const TXT = "text({ y: 0.5, size: 0.07, ink: 0 });";

test('kit.js is ES2019 (no ?. or ??)', () => {
  const src = fs.readFileSync(KIT_PATH, 'utf8').replace(/\/\*[\s\S]*?\*\/|\/\/.*$/gm, '').replace(/'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"/g, "''");
  assert.doesNotMatch(src, /\?\?|\?\.(?!\d)/);
  assert.doesNotMatch(src, /\b(import|export|require|document|window)\b/);
});

test('caps: marks, points, inks throw readable errors', () => {
  assert.throws(() => run('for (var i = 0; i < 6100; i++) line([[i % 1000, 300], [i % 1000 + 3, 302]]);' + TXT), /plan has \d+ marks \(max 6000\).*line\(\) call/);
  assert.throws(() => run('area([[100,100],[1000,100],[1000,2300],[100,2300]], { mode: "stipple", spacing: 2 });' + TXT), /max 6000.*stipple, spacing 2/);
  assert.throws(() => run('for (var i = 0; i < 600; i++) line(wave(0, 300 + i * 3, 1080, 30, 6), { width: 1 });' + TXT), /points \(max 120000\)/);
  assert.throws(() => run('inks(["#000000","#111111","#222222","#333333","#444444"]);' + TXT), /inks\(\): expected 1-4/);
  assert.throws(() => run('inks(["#2b3a8c","#2B3A8C"]);' + TXT), /appears twice/);
  assert.throws(() => run('ground("blue");' + TXT), /"blue" is not a hex colour/);
  assert.throws(() => run('line([[0,0],[10,10]], { ink: 1 });' + TXT), /ink 1 does not exist/);
  assert.throws(() => run('area(circle(500,500,100), { mode: "fill" });' + TXT), /mode "fill" unknown; use one of wash\|hatch\|stipple\|side/);
  assert.throws(() => run('line([[0,0],[NaN,10]]);' + TXT), /point 1 is \[null,10\]/);
});

test('text: exactly once, contrast >= 4.5, inside SAFE', () => {
  assert.throws(() => run('line([[0,0],[100,100]]);'), /never called text\(\)/);
  assert.throws(() => run(TXT + TXT), /text\(\) called twice/);
  assert.throws(() => run('ground("#FFFFFF"); inks(["#AAAAAA"]);' + TXT), /text ink #AAAAAA has contrast 2\.\d:1 on ground #FFFFFF, need >=4\.5/);
  run('ground("#FFFFFF"); inks(["#767676"]);' + TXT); // 4.54:1 passes
  assert.throws(() => run('text({ y: 0.1, size: 0.07 });'), /SAFE is y 288\.\.2112; use text\.y between 0\.\d+ and 0\.\d+/);
});

test('program errors carry the program line', () => {
  assert.throws(() => run('var a = 1;\nline([[0, 0]]);\n' + TXT), /^Error: program line 2: line\(\) call 1: needs at least 2 points/);
  assert.throws(() => run('\n\nnope();\n' + TXT), /program line 3: ReferenceError: nope is not defined/);
});

test('medium clamped, coordinates clamped', () => {
  const p = run('medium({ tooth: 7, grain: -1, blend: "screen" }); line([[-9000, 100], [9000, 100]]);' + TXT);
  assert.deepEqual(p.medium, { tooth: 1, grain: 0, speckle: 0, blend: 'normal' });
  for (const m of p.marks) if (m.p) for (let i = 0; i < m.p.length; i += 3) assert.ok(m.p[i] >= -1080 && m.p[i] <= 2160);
});

const SCENE = `medium({ wander: 0.6 }); inks(["#2B3A8C", "#C3272B"]);
for (var k = 1; k < 30; k++) line(circle(700, 700, k * 18), { closed: true, width: 1.4 });
line([[100, 1500], [900, 1500]], { phase: 'outline' });
area([circle(400, 1700, 300)], { mode: 'hatch', spacing: 4 });
area([circle(700, 1800, 150)], { mode: 'wash', ink: 1 });
line([[500, 2000], [520, 2010]], { phase: 'dark' });
text({ y: 0.42, size: 0.07, ink: 0, halo: true });`;

test('order and timing: phases, sorted, monotonic, total, poem share', () => {
  for (const seconds of [6, 12, 30]) {
    const p = run(SCENE, { seconds });
    assert.equal(p.total, seconds * 1000);
    const ms = p.marks;
    const last = ms[ms.length - 1];
    assert.equal(last.k, 't');
    assert.equal(last.t1, p.total);
    assert.ok(last.t1 - last.t0 >= Math.max(1500, 0.3 * p.total), `poem gets ${last.t1 - last.t0} ms`);
    for (let i = 0; i < ms.length; i++) {
      assert.ok(Number.isInteger(ms[i].t0) && Number.isInteger(ms[i].t1));
      assert.ok(ms[i].t1 > ms[i].t0 && ms[i].t0 >= 0 && ms[i].t1 <= p.total);
      if (i) assert.ok(ms[i].t0 >= ms[i - 1].t0, 'sorted by t0');
      if (ms[i].k !== 't') assert.ok(ms[i].t1 <= last.t0, 'picture ends before the poem');
    }
    assert.ok(ms.length - 1 > 800, 'this scene uses parallel hands');
  }
});

test('phases do not overlap: outline < inner < area < detail < dark', () => {
  const p = run(`line([[100,400],[900,400]], { width: 5, phase: 'detail' });
    line([[100,500],[900,500]], { width: 9, phase: 'outline' });
    area([[[100,700],[900,700],[900,900],[100,900]]], { mode: 'wash', sharp: true });
    line([[100,600],[900,600]], { width: 7, phase: 'inner' });
    line([[100,1000],[900,1000]], { width: 11, phase: 'dark' });` + TXT);
  const byWidth = { 5: 'detail', 7: 'inner', 9: 'outline', 11: 'dark' };
  const span = {};
  for (const m of p.marks.filter((m) => m.k !== 't')) {
    const wmax = Math.max(...m.p ? m.p.filter((_, i) => i % 3 === 2) : [0]);
    const ph = m.k === 'a' ? 'area' : byWidth[[5, 7, 9, 11].find((w) => wmax >= w * 0.85 && wmax <= w * 1.25)];
    span[ph] = [Math.min(span[ph] ? span[ph][0] : Infinity, m.t0), Math.max(span[ph] ? span[ph][1] : -Infinity, m.t1)];
  }
  const order = Object.keys(span).sort((a, b) => span[a][0] - span[b][0]);
  assert.deepEqual(order, ['outline', 'inner', 'area', 'detail', 'dark']);
  for (let i = 1; i < order.length; i++) assert.ok(span[order[i]][0] >= span[order[i - 1]][1], `${order[i]} starts after ${order[i - 1]} ends`);
});

test('determinism: same seed same plan, other seed other plan', () => {
  const a = runProgram({ program: SCENE, seed: 42 }), b = runProgram({ program: SCENE, seed: 42 }), c = runProgram({ program: SCENE, seed: 43 });
  assert.equal(a, b);
  assert.notEqual(a, c);
  // Math.random is seeded too
  const r = 'line([[Math.random() * 1000, 300], [500, 900]]);' + TXT;
  assert.equal(runProgram({ program: r, seed: 5 }), runProgram({ program: r, seed: 5 }));
});

test('strokes stay within one reach and taper', () => {
  const p = run('line(wave(-200, 1000, 1300, 60, 2), { width: 4 });' + TXT);
  const strokes = p.marks.filter((m) => m.k === 's');
  assert.ok(strokes.length >= 3, 'a 1300px line is drawn in several strokes');
  for (const m of strokes) {
    let L = 0, wmax = 0;
    for (let i = 3; i < m.p.length; i += 3) L += Math.hypot(m.p[i] - m.p[i - 3], m.p[i + 1] - m.p[i - 2]);
    for (let i = 2; i < m.p.length; i += 3) wmax = Math.max(wmax, m.p[i]);
    assert.ok(L <= 430, `stroke ${L.toFixed(0)}px`);
    assert.ok(m.p[2] < 0.8 * wmax && m.p[m.p.length - 1] < 0.8 * wmax, 'swells in and tapers out');
  }
});

test('plan is ASCII JSON with non-ASCII haiku escaped', () => {
  const s = runProgram({ program: TXT, haiku: ['古池や', 'café — naïve', 'ok'] });
  assert.match(s, /^[\x20-\x7e]*$/);
  assert.deepEqual(JSON.parse(s).haiku, ['古池や', 'café — naïve', 'ok']);
});

test('smoke: rings + hatch + text render to PNG', () => {
  const program = fs.readFileSync(path.join(HERE, 'fixtures/smoke.js'), 'utf8');
  const plan = run(program, { haiku: ['rain on the still pond', 'each ring keeps the beat, and yet', 'water leaves no scar'] });
  const out = path.join(HERE, 'out');
  fs.mkdirSync(out, { recursive: true });
  fs.writeFileSync(path.join(out, 'smoke.png'), render(plan, 540).toBuffer('image/png'));
  fs.writeFileSync(path.join(out, 'smoke-half.png'), render(plan, 540, plan.total * 0.4).toBuffer('image/png'));
  assert.ok(fs.statSync(path.join(out, 'smoke.png')).size > 50000);
});
