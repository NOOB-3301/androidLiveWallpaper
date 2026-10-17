// medium: ballpoint | layout: object above text | haiku: steam over the bowl / a hairline crack goes nowhere / the clay keeps cooling
// learn: a vessel drawn face-on — rim ellipse, a flat belly from a param loop, a small foot — hatched in two passes, steam as faint spirals, a cast shadow and fallen accents so the lower third stays alive; no table, no perspective. Never copy this subject or palette.
medium({ tooth: 0.25, grain: 0.15, speckle: 0.05, blend: 'multiply', wander: 0.3, taper: 0.25 });
ground('#F3F0E8');
inks(['#2B3A8C', '#C3272B']);
var R = rand(seed);
var bx = 540, rim = H * 0.27, bw = 322, dz = H * 0.155; // dz = bowl depth below the rim
line(ellipse(bx, rim, bw, 72), { closed: true, width: 2.6, alpha: 0.9, phase: 'outline' });
line(ellipse(bx, rim, bw - 44, 40), { closed: true, width: 1.4, alpha: 0.5, phase: 'inner' });
var belly = [];
for (var t = 0; t <= 26; t++) { // left rim, down the belly, up to the right rim — wobbled, the loop closes along the rim
  var a = t / 26 * Math.PI, wob = 1 + Math.sin(a * 4 + R()) * 0.02;
  belly.push([bx - Math.cos(a) * bw * wob, rim + Math.sin(a) * dz * wob]);
}
line(belly, { width: 2.4, alpha: 0.85, phase: 'inner' });
area([belly], { mode: 'hatch', angle: 8, spacing: 7, width: 1.7, len: 40, alpha: 0.6 }); // body tone; the loop closes itself along the rim chord
area([belly], { mode: 'hatch', angle: 74, spacing: 13, width: 1.3, len: 22, alpha: 0.42, phase: 'inner' }); // second pass, darker patches
line([[bx - 66, rim + dz + 4], [bx - 74, rim + dz + 54], [bx - 52, rim + dz + 58]], { width: 2, alpha: 0.8, phase: 'inner' }); // small foot
line([[bx + 66, rim + dz + 4], [bx + 74, rim + dz + 54], [bx + 52, rim + dz + 58]], { width: 2, alpha: 0.8, phase: 'inner' });
line(ellipse(bx, rim + dz + 58, 74, 14), { closed: true, width: 2, alpha: 0.75, phase: 'inner' });
area([[[bx - 300, rim + dz + 84], [bx + 340, rim + dz + 88], [bx + 300, rim + dz + 122], [bx - 270, rim + dz + 118]]], { mode: 'hatch', angle: 2, spacing: 9, width: 1.5, len: 26, alpha: 0.42, phase: 'detail' }); // the cast shadow widens right
line([[bx - 92, rim + dz * 0.55], [bx - 78, rim + dz * 0.62], [bx - 92, rim + dz * 0.7], [bx - 80, rim + dz * 0.78]], { ink: 1, width: 1.2, alpha: 0.85, phase: 'detail' }); // one red hairline crack, going nowhere
for (var i = 0; i < 3; i++) { // steam rises as faint wide spirals, leaning as it cools
  line(spiral(bx - 120 + i * 116, rim - 120 - i * 40, 2 + i, 16 + i * 7, -1.9, R() * TAU), { width: 1.4, alpha: 0.32, phase: 'detail' });
}
for (var q = 0; q < 7; q++) { // tiny wet marks spur the rim line
  var qa = R() * TAU;
  line([[bx + Math.cos(qa) * (bw + 2), rim + Math.sin(qa) * 74], [bx + Math.cos(qa) * (bw + 10), rim + Math.sin(qa) * 80]], { width: 1.3, alpha: 0.45, phase: 'detail' });
}
for (var p = 0; p < 64; p++) { // a scatter field of short dashes thins away from the bowl and stops short of the poem
  var px = R() * 1080, py = R() < 0.5 ? SAFE.y0 + R() * (H * 0.14) : H * 0.8 + R() * (SAFE.y1 - H * 0.8);
  line([[px, py], [px + 10 + R() * 22, py + R() * 6]], { width: 1.5, alpha: 0.3 + R() * 0.3, phase: 'detail' });
}
line(spiral(190, H * 0.86, 2, 18, 1.8, R()), { ink: 1, width: 1.6, alpha: 0.7, phase: 'detail' }); // one fallen leaf, the single red friend
line(spiral(940, H * 0.905, 2, 15, 1.6, R()), { width: 1.6, alpha: 0.6, ink: 0, phase: 'detail' });
text({ y: 0.64, size: 0.075, ink: 0, halo: true });
