// late frost, gold gel on brown (ref 12): one snowflake from branch(), a dotted rule, gold text
medium({ tooth: 0.45, grain: 0.2, speckle: 0.25, wander: 0.4, taper: 0.3 });
ground('#3A1C12');
inks(['#D8B26A', '#C9A35A']);
for (var a = 0; a < 6; a++) {
  branch(530, 700, a * TAU / 6 - TAU / 4, 330, { depth: 2, shoots: 5, spread: 0.75, shrink: 0.35 }).forEach(function (b) {
    line(b.pts, { width: b.depth ? 1.2 : 2, alpha: 0.9, sharp: true, ink: 1, phase: b.depth ? 'detail' : 'inner' });
  });
}
line(circle(530, 700, 9), { closed: true, width: 1.5 });
line([[0, 1080], [1080, 1080]], { width: 1.6, phase: 'outline' });
for (var x = 30; x < 1060; x += 14) line([[x, 1098], [x + 0.5, 1098.5]], { width: 3, phase: 'detail' });
text({ y: 0.6, size: 0.085, ink: 0 });
