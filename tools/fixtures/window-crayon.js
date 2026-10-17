// frost on the window, crayon (ref 07): terracotta panes laid with the side of the crayon, white snowflakes
medium({ tooth: 0.8, grain: 0.4, speckle: 0.08, wander: 0.6, taper: 0.3 });
ground('#4E2A1C');
inks(['#F4EEE4', '#6B3524', '#C8714A']);
var panes = [];
for (var r = 0; r < 2; r++) for (var c = 0; c < 3; c++) panes.push([[c * 360 + 14, r === 0 ? 300 : 1520], [c * 360 + 346, r === 0 ? 300 : 1520], [c * 360 + 346, r === 0 ? 880 : 2100], [c * 360 + 14, r === 0 ? 880 : 2100]]);
area(panes, { mode: 'side', ink: 2, angle: -70, spacing: 7, len: 70, alpha: 0.85, sharp: true });
area([[[0, 905], [1080, 905], [1080, 1495], [0, 1495]]], { mode: 'side', ink: 1, angle: -75, spacing: 9, len: 60, alpha: 0.6, sharp: true });
panes.forEach(function (p, i) {
  var cx = (p[0][0] + p[1][0]) / 2, cy = (p[0][1] + p[2][1]) / 2;
  for (var a = 0; a < 6; a++) branch(cx, cy, a * TAU / 6 + i * 0.3, 150, { depth: 1, shoots: 3, spread: 0.8, shrink: 0.4, pairs: true }).forEach(function (b) {
    line(b.pts, { width: b.depth ? 3 : 4.5, alpha: 0.95, sharp: true, phase: 'detail' });
  });
});
text({ y: 0.5, size: 0.08, ink: 0, halo: false });
