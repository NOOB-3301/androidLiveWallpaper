// Runs kit.js + a drawing program in a fresh node vm context exactly like JsRunner.kt does on Android.
//   node tools/run-program.mjs program.js out.json [--haiku "a|b|c"] [--seconds 12] [--seed 7] [--w 1080] [--h 2400]
// Also importable: runProgram({ program, haiku, seed, seconds, W, H }) -> plan JSON string (throws Error(message)).
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const KIT_PATH = path.join(ROOT, 'app/src/main/assets/kit/kit.js');

// Must match JsRunner.compose() in app/src/main/java/com/haiku/wall/js/JsRunner.kt character for character.
export function compose(inputJson, kit, program) {
  return 'globalThis.__input = ' + inputJson + ';\n' + kit.trimEnd() + '\n;try{(function(){\n' + program + '\n})();}catch(e){__fail(e)}\n__finish()';
}

export function runProgram({ program, haiku = ['an old silent pond', 'a frog jumps into the pond', 'splash! silence again'], seed = 1, seconds = 12, W = 1080, H = 2400 }) {
  const kit = fs.readFileSync(KIT_PATH, 'utf8');
  const input = JSON.stringify({ W, H, haiku, seed, seconds });
  try {
    return vm.runInNewContext(compose(input, kit, program), {}, { timeout: 10000, filename: 'plan.js' });
  } catch (e) {
    // a vm-context error is not an instance of this realm's Error; keep only its message, like Android does
    throw new Error(e && e.message ? e.message : String(e));
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  const args = process.argv.slice(2);
  const opt = (name, d) => { const i = args.indexOf('--' + name); return i >= 0 ? args.splice(i, 2)[1] : d; };
  const haiku = opt('haiku');
  const o = { seconds: +opt('seconds', 12), seed: +opt('seed', 1), W: +opt('w', 1080), H: +opt('h', 2400) };
  if (haiku) o.haiku = haiku.split('|');
  const [src, out] = args;
  if (!src || !out) {
    console.error('usage: node tools/run-program.mjs program.js out.json [--haiku "a|b|c"] [--seconds 12] [--seed 1] [--w 1080] [--h 2400]');
    process.exit(2);
  }
  try {
    const t = Date.now();
    const plan = runProgram({ ...o, program: fs.readFileSync(src, 'utf8') });
    fs.writeFileSync(out, plan);
    const p = JSON.parse(plan);
    const pts = p.marks.reduce((s, m) => s + (m.p ? m.p.length / 3 : m.loops ? m.loops.reduce((a, l) => a + l.length / 2, 0) : 0), 0);
    console.log(`${out}: ${p.marks.length} marks, ${pts} points, ${(plan.length / 1024).toFixed(0)} KB, ${Date.now() - t} ms`);
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }
}
