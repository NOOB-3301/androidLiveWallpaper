// medium: ballpoint | layout: line-field behind text | haiku: an old bicycle / two circles lean on the wall / one tyre, soft and flat
// learn: two ring fields cross and make moiré; run them behind the poem and let the halo cut them away from the letters; one red accent only. Never copy this subject or palette.
medium({ tooth: 0.3, grain: 0.2, wander: 0.55, taper: 0.5 });
ground('#F3F0E8');
inks(['#2B3A8C', '#C3272B']);
var r = rand(seed);
var w1 = [300, H * 0.4], w2 = [840, H * 0.6];
for (var k = 1; k <= 24; k++) line(circle(w1[0], w1[1], k * 16 + r() * 4), { closed: true, width: 1.3 + r() * 0.5, alpha: 0.75, gaps: 0.05, phase: k < 3 ? 'detail' : 'inner' });
for (var k = 1; k <= 20; k++) line(circle(w2[0], w2[1], k * 16 + r() * 4), { closed: true, width: 1.2 + r() * 0.5, alpha: 0.7, gaps: 0.06, phase: k < 3 ? 'detail' : 'inner' });
line([[w1[0], w1[1]], [548, H * 0.5], [w2[0], w2[1]]], { width: 3.2, alpha: 1, phase: 'outline' }); // the frame joins the hubs
line([[556, H * 0.51], [622, H * 0.32], [w2[0], w2[1]]], { width: 2.6, alpha: 0.95, phase: 'inner' }); // seat tube and fork
line([[596, H * 0.325 - 16], [676, H * 0.325 - 22]], { width: 4, alpha: 0.95, phase: 'inner' }); // the saddle
line([[w2[0], w2[1]], [w2[0] - 34, w2[1] - 58]], { width: 2.4, alpha: 0.95, phase: 'inner' }); // steering column
line(circle(648, H * 0.33, 30), { closed: true, width: 2, gaps: 0.25, ink: 1, phase: 'detail' }); // the bell, one red accent
area([[[70, H * 0.66], [1010, H * 0.66], [992, H * 0.7], [88, H * 0.7]]], { mode: 'hatch', angle: 4, spacing: 7, width: 1.5, len: 30, jitter: 10, alpha: 0.5, sharp: true, phase: 'detail' }); // ground shadow
text({ y: 0.47, size: 0.075, ink: 0, halo: true });
