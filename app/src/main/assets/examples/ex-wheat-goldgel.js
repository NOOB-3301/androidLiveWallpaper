// medium: gold gel on dark | layout: line-field behind text | haiku: dry wheat at dusk / every head leans the same way / no wind I can see
// learn: one family of near-parallel lines whose angle drifts slowly across the sheet, broken into dashes, with one off-centre disturbance; halo keeps the poem clear. Never copy this subject or palette.
medium({ tooth: 0.4, grain: 0.2, speckle: 0.25, wander: 0.4, taper: 0.3 });
ground('#1B2536');
inks(['#D9BE7A', '#C9A564']);
var r = rand(seed);
for (var i = 0; i < 78; i++) {
  var x = -30 + i * 14.6 + r.range(-9, 9); // spacing drifts, angle leans with slow noise
  var lean = noise(i * 0.13, 0) * 130 - 65;
  var y0 = H * 0.15 + r() * H * 0.22, y1 = H + 30;
  line([[x, y1], [x + lean * 0.35, lerp(y1, y0, 0.5)], [x + lean, y0]], { ink: i % 6 ? 0 : 1, width: 1.4 + r() * 0.8, alpha: 0.3 + r() * 0.35, gaps: 0.16, phase: 'inner' });
  if (r() < 0.75) for (var k = 0; k < 7; k++) { // the ear: short ticks hugging the stalk's top
    var t = k / 6, px = x + lean * t, py = lerp(y1, y0, t);
    line([[px, py], [px - 9 - r() * 9, py - 13 - r() * 5]], { ink: 0, width: 1.2, alpha: 0.7, sharp: true, phase: 'detail' });
  }
}
line(circle(856, H * 0.17, 46), { closed: true, width: 1.7, alpha: 0.85, gaps: 0.08, ink: 1, phase: 'outline' }); // one low moon, the off-centre accent
text({ y: 0.5, size: 0.085, ink: 0, halo: true });
