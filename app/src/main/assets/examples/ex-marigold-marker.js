// medium: felt marker | layout: ring burst | haiku: a marigold / packs every ring of the year / into one bloom
// learn: concentric wobbly rings, each with its own ink and falling alpha, a dense core, a couple of dropped accents; the poem sits in clear ground below. Never copy this subject or palette.
medium({ tooth: 0.15, grain: 0.25, wander: 0.35, taper: 0.12, blend: 'multiply' });
ground('#F2F0EA');
inks(['#253C8B', '#D9822F', '#C2452B']);
var cx = 540, cy = H * 0.32;
for (var k = 0; k < 8; k++) { // petal rings: alternating radii make scallops, noise wobbles every one
  var R0 = 70 + k * 62, n = 14 + k * 3, pts = [];
  for (var i = 0; i < n * 2; i++) pts.push(polar(cx, cy, R0 + (i % 2 ? 26 : -10) + noise(i * 0.4, k * 2) * 14, i * TAU / (n * 2)));
  line(pts, { closed: true, sharp: true, ink: k % 2 ? 1 : 0, width: 10 - k * 0.6, alpha: 0.8 - k * 0.05, phase: k < 2 ? 'inner' : 'detail' });
}
area([circle(cx, cy, 56)], { mode: 'wash', ink: 2, alpha: 0.85, phase: 'dark' });
for (var k = 0; k < 8; k++) { var b = polar(cx, cy, 90 + k * 58, 0.3 + k); line(circle(b[0], b[1], 5), { closed: true, ink: k % 3, width: 3.5, alpha: 0.8, phase: 'detail' }); } // bead dabs on the rings
area([circle(cx, cy, 560), circle(cx, cy, 380)], { mode: 'stipple', ink: 1, spacing: 26, width: 3.5, density: 0.3, phase: 'detail' }); // dust between rings and out
[[225, H * 0.6], [882, H * 0.56], [130, H * 0.44], [950, H * 0.66]].forEach(function (p, i) { // loose petals have dropped
  line(spiral(p[0], p[1], 2, 20, 1.7, i * 2.1), { ink: i % 3, width: 6, alpha: 0.7, phase: 'detail' });
});
text({ y: 0.68, size: 0.085, ink: 0 });
