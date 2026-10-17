// pear on the south wall, pencil (ref 03): a fan of shoots tied to wires, small scribbled knots
medium({ tooth: 0.7, grain: 0.3, wander: 0.45, taper: 0.6 });
ground('#EFE7D6');
inks(['#4A4A4A']);
var r = rand(seed);
for (var w = 0; w < 4; w++) line([[0, 1330 + w * 250], [1080, 1330 + w * 250]], { width: 1, alpha: 0.3, gaps: 0.35, phase: 'outline' });
for (var i = 0; i < 9; i++) {
  var x = 130 + i * 102, bx = 520 + (i - 4) * 14;
  line([[bx, 2380], [lerp(bx, x, 0.5) + r.range(-10, 10), 1800], [x, 1320]], { width: 1.8, alpha: 0.85, phase: 'inner' });
  line([[bx + 4, 2380], [x + 5, 1325]], { width: 0.9, alpha: 0.35, gaps: 0.2, phase: 'inner' });
  for (var k = 0; k < 5; k++) {
    var t = 0.15 + k * 0.17 + r.range(-0.04, 0.04), px = lerp(x, bx, t), py = lerp(1320, 2380, t);
    line(spiral(px, py, 2, 9 + r() * 4, 2.2, r() * TAU), { width: 1.4, alpha: 0.8, phase: 'detail' });
  }
}
text({ y: 0.4, size: 0.08, ink: 0 });
