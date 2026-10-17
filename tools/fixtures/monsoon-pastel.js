// monsoon, pastel on green (ref 11): rain strokes, a vine with roses as broad spiral strokes
medium({ tooth: 0.7, grain: 0.35, speckle: 0.05, wander: 0.6, taper: 0.35 });
ground('#24503A');
inks(['#F1E6C8', '#3FB4D8', '#D8452E', '#E9A23B']);
var r = rand(seed);
for (var i = 0; i < 140; i++) { var x = r() * 1180 - 50, y = 260 + r() * 600, l = 60 + r() * 120; line([[x, y], [x + l * 0.18, y + l]], { ink: r() < 0.7 ? 1 : 0, width: 3 + r() * 3, alpha: 0.85, phase: 'outline' }); }
line(wave(0, 1990, 1080, 25, 1.2, 1).map(function (p) { return [p[0], p[1] - p[0] * 0.25]; }), { ink: 1, width: 7, alpha: 0.6, phase: 'inner' });
[[150, 1940, 22], [380, 1880, 34], [620, 1830, 46], [860, 1760, 58]].forEach(function (f, k) {
  line(spiral(f[0], f[1], 3, f[2], 2.4, k), { ink: 2, width: f[2] / 4.5, alpha: 0.9, taper: 0.6, phase: 'detail' });
  line(circle(f[0] + f[2] * 1.4, f[1] - f[2] * 1.2, 9), { closed: true, ink: 3, width: 9, phase: 'dark' });
});
text({ y: 0.5, size: 0.085, ink: 0, halo: true });
