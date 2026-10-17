// smoke: ballpoint ring fields (ref 01), a hatched pear (ref 03/08 pencil hatching), the poem
medium({ tooth: 0.35, grain: 0.2, wander: 0.6, taper: 0.5 });
ground('#F1EEE6');
inks(['#2B3A8C', '#1F2A66']);
var r = rand(seed);
[[880, 520, 26], [190, 1820, 24]].forEach(function (c) {
  for (var k = 1; k <= c[2]; k++) line(circle(c[0], c[1], k * 17 + r() * 4), { closed: true, width: 1.5, alpha: 0.85, gaps: 0.04, phase: k < 4 ? 'detail' : 'inner' });
});
for (var j = 0; j < 40; j++) line(arc(-500, 2900, 1500 + j * 26, -1.3, -0.2), { width: 1.3, alpha: 0.7, phase: 'outline' });
var pear = [[560, 1180], [610, 1240], [640, 1330], [700, 1400], [720, 1500], [680, 1590], [560, 1630], [440, 1590], [400, 1500], [420, 1400], [480, 1330], [510, 1240]];
line(pear, { closed: true, width: 2.2, phase: 'outline', ink: 1 });
area([pear], { mode: 'hatch', angle: -50, spacing: 8, width: 1.4, alpha: 0.75 });
area([[[600, 1420], [700, 1420], [700, 1620], [560, 1620]]], { mode: 'hatch', angle: 30, spacing: 7, width: 1.3, ink: 1 });
line([[560, 1180], [566, 1120], [590, 1090]], { width: 2.4, ink: 1 });
text({ y: 0.45, size: 0.07, ink: 1, halo: true });
