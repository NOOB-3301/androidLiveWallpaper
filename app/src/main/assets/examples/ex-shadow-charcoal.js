// medium: charcoal | layout: diagonal split | haiku: afternoon roof edge / a shadow folds the white wall / the pot keeps its cool
// learn: one slightly bowed diagonal as two close lines; one side shaded along the crease thinning away like a vignette; the lit side keeps a faint wall wash and small accents so bare paper stays above a quarter of the canvas. Never copy this subject or palette.
medium({ tooth: 0.85, grain: 0.5, speckle: 0.2, wander: 0.6, taper: 0.5 });
ground('#EFEDE6');
inks(['#23272B']);
var r = rand(seed);
var crease = [[-20, H * 0.68], [340, H * 0.59], [760, H * 0.45], [1100, H * 0.38]];
line(crease, { width: 4.5, gaps: 0.06, phase: 'outline' });
line(offset(crease, 20), { width: 3, gaps: 0.2, alpha: 0.6, phase: 'inner' });
line(offset(crease, 44), { width: 1.6, gaps: 0.32, alpha: 0.35, phase: 'detail' });
var shade = crease.concat([[1100, H + 20], [-20, H + 20]]);
area([shade], { mode: 'side', angle: -38, spacing: 20, len: 96, alpha: 0.32, sharp: true }); // base tone over the whole shade
area([crease.concat([[1100, crease[3][1] + 300], [1100, crease[3][1] + 6], [(crease[2][0] + crease[3][0]) / 2, (crease[2][1] + crease[3][1]) / 2 + 150]])], { mode: 'side', angle: -42, spacing: 13, width: 3, len: 60, alpha: 0.6, phase: 'inner', sharp: true }); // dense right under the fold, thinning downward
area([crease.concat([[1100, crease[3][1] + 130], [1100, crease[3][1] + 6], [(crease[2][0] + crease[3][0]) / 2, (crease[2][1] + crease[3][1]) / 2 + 66]])], { mode: 'hatch', angle: -42, spacing: 9, width: 2.6, len: 26, alpha: 0.72, phase: 'dark', sharp: true }); // darkest pass just under the fold
for (var d = 0; d < 9; d++) { // dust motes and short ticks drift through the shade
  var dx = r() * 980, dy = crease[2][1] + 120 + r() * (H - crease[2][1] - 170);
  line([[dx, dy], [dx + r() * 26, dy + r() * 8]], { width: 2.2 + r() * 1.6, alpha: 0.3 + r() * 0.4, phase: 'dark' });
}
var lit = [[-20, -20], [1100, -20]].concat(crease.reverse()); // the lit wall: a visible soft wash plus sparse stipple, so the paper never reads empty
area([lit], { mode: 'hatch', angle: 22, spacing: 24, width: 2, len: 44, alpha: 0.2, phase: 'inner' });
for (var w = 0; w < 5; w++) { // a soft drifting stipple fleck on the lit wall
  var wx = 120 + (w * 190) % 800, wy = SAFE.y0 + 30 + r() * 260;
  for (var s2 = 0; s2 < 5; s2++) line([[wx + r() * 22, wy + r() * 8], [wx + r() * 26 + 3, wy + r() * 10]], { width: 2.2, alpha: 0.22, phase: 'detail' });
}
line([[940, H * 0.13], [1000, H * 0.14], [1024, H * 0.175], [992, H * 0.19]], { width: 1.6, alpha: 0.45, phase: 'detail' }); // one plaster crack
line([[240, H * 0.28], [282, H * 0.29], [306, H * 0.315], [270, H * 0.33]], { width: 1.6, alpha: 0.4, phase: 'detail' });
var pot = [[296, H * 0.8], [382, H * 0.8], [362, H * 0.8 + 88], [316, H * 0.8 + 88]]; // the pot sits down in the shade, where it thins: it keeps its cool
line(pot.concat([pot[0]]), { width: 2.8, alpha: 0.75, sharp: true, gaps: 0.1, phase: 'inner' });
line(ellipse(339, H * 0.8, 43, 9), { closed: true, width: 2.2, alpha: 0.7, phase: 'inner' });
area([pot], { mode: 'hatch', angle: 68, spacing: 6, width: 2, len: 24, alpha: 0.45, sharp: true });
line([[272, H * 0.8 + 88], [410, H * 0.8 + 88]], { width: 2.8, alpha: 0.7, gaps: 0.14, phase: 'detail' }); // its thin base line
for (var c = 0; c < 7; c++) { // dry stems lean out of the pot
  var cxx = 324 + r() * 34;
  line([[cxx, H * 0.8], [cxx + r.range(-16, 16), H * 0.8 - 26 - r() * 40]], { width: 1.8, alpha: 0.65, phase: 'detail' });
}
line(spiral(339, H * 0.8 - 88, 2, 15, 2, r()), { width: 1.6, alpha: 0.5, phase: 'detail' }); // one dry curl above the stems
text({ y: 0.47, size: 0.08, ink: 0, halo: true });
