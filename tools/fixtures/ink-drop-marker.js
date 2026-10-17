// ink drop, marker (ref 16): zigzag rings in 4 inks with low taper, a filled centre, multiply
medium({ tooth: 0.15, grain: 0.3, wander: 0.4, taper: 0.1, blend: 'multiply' });
ground('#F4F1EA');
inks(['#2A4C9C', '#E0A63A', '#C8432E', '#8DB6E0']);
var cx = 540, cy = 760;
for (var k = 0; k < 9; k++) {
  var R0 = 120 + k * 52, n = 26 + k * 6, pts = [];
  for (var i = 0; i < n * 2; i++) pts.push(polar(cx, cy, R0 + (i % 2 ? 18 : -18) + noise(i * 0.3, k) * 20, i * TAU / (n * 2)));
  line(pts, { closed: true, sharp: true, width: 9 - k * 0.4, alpha: 0.75 - k * 0.04, ink: [0, 1, 2, 1, 0, 3, 1, 2, 3][k], phase: k < 3 ? 'inner' : 'detail' });
}
area([circle(cx, cy, 100)], { mode: 'hatch', ink: 0, angle: -20, spacing: 8, width: 11, len: 200, jitter: 4, alpha: 0.9, phase: 'dark' });
text({ y: 0.68, size: 0.08, ink: 0 });
