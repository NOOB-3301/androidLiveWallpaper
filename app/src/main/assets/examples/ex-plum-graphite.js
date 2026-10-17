// medium: graphite pencil | layout: object above text | haiku: a plum on the sill / afternoon tips it over / shadow, not a stone
// learn: one still object lit from one side; tone by hatch layers plus a side-of-the-lead shadow; a quiet echo below. Never copy this subject or palette.
medium({ tooth: 0.65, grain: 0.35, wander: 0.5, taper: 0.7 });
ground('#F1E6D2');
inks(['#3A3A3E']);
var r = rand(seed);
var cx = 540, cy = H * 0.28, R = 260;
line(circle(cx, cy, R), { closed: true, width: 1.3, alpha: 0.45, gaps: 0.1, phase: 'outline' });
line(arc(cx, cy, R, 0.9, 4.3), { width: 2.2, alpha: 0.9, phase: 'inner' }); // the lit contour presses harder
line([[cx - 30, cy - R - 10], [cx - 4, cy - R - 44], [cx + 52, cy - R - 26]], { width: 2.4, phase: 'detail' }); // stem
var dark = arc(cx, cy, R, 0.95, 4.4).concat([[cx + 16, cy]]);
area([dark], { mode: 'hatch', angle: 62, spacing: 5, width: 1.3, len: 48, jitter: 14, alpha: 0.75 });
area([dark], { mode: 'hatch', angle: -35, spacing: 8, width: 1, len: 34, jitter: 16, alpha: 0.5, phase: 'detail' });
area([ellipse(cx + 240, cy + R - 12, 380, 54)], { mode: 'hatch', angle: 4, spacing: 4, width: 1.1, len: 84, jitter: 4, alpha: 0.42, phase: 'detail' }); // cast shadow, thrown away from the light
area([ellipse(cx + 110, cy + R - 6, 200, 30)], { mode: 'hatch', angle: 86, spacing: 4, width: 1, len: 30, jitter: 7, alpha: 0.5, phase: 'dark' }); // contact dark under the fruit
var sill = cy + R + 84; // the sill's firm edge right under the shadow, then grain only below it
line([[-20, sill], [1100, sill]], { width: 1.8, alpha: 0.6, gaps: 0.12, phase: 'outline' });
line([[-20, sill + 26], [1100, sill + 26]], { width: 1, alpha: 0.35, gaps: 0.2, phase: 'detail' });
for (var i = 0; i < 30; i++) { // faint wood-grain echo below the sill, one direction
  var y = sill + 58 + r() * 110;
  line([[20 + r() * 300, y], [420 + r() * 620, y + r.range(-3, 3)]], { width: 0.9, alpha: 0.2, sharp: true, phase: 'detail' });
}
text({ y: 0.62, size: 0.085, ink: 0, halo: true });
