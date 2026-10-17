// medium: brush pen | layout: ellipse/box frame | haiku: the wisteria / lets down one long violet finger / a bee takes the stair
// learn: an ornamental ellipse framing the poem, curls on its rim; cascading fronds fill the margins edge to edge and a meadow of grass flicks holds the bottom. Never copy this subject or palette.
medium({ tooth: 0.12, grain: 0.1, wander: 0.55, taper: 0.9 });
ground('#E7D8BA');
inks(['#1E1C1A', '#B03A2E']);
var r = rand(seed);
var cx = 540, cy = H * 0.48, rx = 462, ry = H * 0.135;
line(ellipse(cx, cy, rx, ry), { closed: true, width: 4.5, alpha: 0.95, ink: 1, phase: 'outline' });
line(ellipse(cx, cy, rx - 18, ry - 14), { closed: true, width: 1.6, alpha: 0.55, ink: 1, phase: 'inner' });
for (var i = 0; i < 10; i++) { // rim curls on stalks, alternate inks, each its own size
  var a = i * TAU / 10 + r() * 0.25, px = cx + Math.cos(a) * (rx + 4), py = cy + Math.sin(a) * (ry + 4);
  line([[px, py], [px + Math.cos(a) * 34, py + Math.sin(a) * 34]], { width: 2, ink: 0, alpha: 0.8, phase: 'inner' });
  line(spiral(px + Math.cos(a) * 34, py + Math.sin(a) * 34, 3, 22 + r() * 16, 1.9, a), { width: 2 + r() * 1.5, alpha: 0.9, ink: i % 2 ? 0 : 1, phase: 'detail' });
}
for (var side = 0; side < 2; side++) { // two long hanging stems frame the left and right margins full height
  var sx = side ? 1040 : 40, dir = side ? -1 : 1;
  for (var k = 0; k < 5; k++) {
    var sy = SAFE.y0 + 40 + k * 190 + r() * 60, sway = 30 + r() * 26;
    line([[sx + dir * r() * 14, sy], [sx + dir * sway * 0.6, sy + 110], [sx + dir * 8, sy + 236 + r() * 70]], { width: 3.2, alpha: 0.95, phase: 'inner' });
    line(spiral(sx + dir * 8, sy + 300 + r() * 40, 3, 14 + r() * 12, 2.1, r() * TAU), { ink: r() < 0.4 ? 1 : 0, width: 2, alpha: 0.85, phase: 'detail' });
  }
}
for (var j = 0; j < 14; j++) { // fronds cascade from the top margin between the stems, each its own stream of side ticks
  var x0 = 90 + j * 66 + r.range(-18, 18);
  branch(x0, SAFE.y0 + 14 + r() * 30, TAU / 4 + r.range(-0.2, 0.2), 210 + r() * 170, { depth: 2, shoots: 3, spread: 0.55, shrink: 0.55, pairs: true }).forEach(function (b) {
    line(b.pts, { width: b.depth ? 1.6 : 3.6, alpha: 0.92, phase: b.depth ? 'detail' : 'inner' });
  });
}
for (var p = 0; p < 32; p++) { // falling petals drift through the margins, denser in the open band below the frame
  var fa = r() * TAU, far = rx + 40 + r() * 130;
  var px2 = cx + Math.cos(fa) * far;
  if (px2 > 150 && px2 < 930) px2 = px2 < cx ? 20 + r() * 110 : 1060 - r() * 110; // keep the mid-band for petals, not the frame
  line(spiral(px2, cy + Math.sin(fa) * (ry + 20 + r() * 300), 2, 13 + r() * 10, 1.6, fa), { ink: p % 3 ? 0 : 1, width: 1.8, alpha: 0.75, phase: 'detail' });
}
for (var g = 0; g < 46; g++) { // a meadow of grass flicks covers the bottom margin, swaying harder after every few
  var gx = 30 + g * 22 + r.range(-12, 12), lean = r.range(-30, 30) + Math.sin(g * 0.7) * 18;
  line([[gx, SAFE.y1 + 26], [gx + lean * 0.4, SAFE.y1 - 40 - r() * 40], [gx + lean, SAFE.y1 - 66 - r() * 80]], { width: r() < 0.3 ? 3 : 1.8, alpha: 0.55 + r() * 0.4, phase: 'detail' });
  if (r() < 0.4) line([[gx + 6, SAFE.y1 + 10], [gx + 6 + r.range(-14, 14), SAFE.y1 - 24 - r() * 30]], { width: 1.4, alpha: 0.5, phase: 'dark' });
}
text({ y: 0.48, size: 0.08, ink: 0, halo: true });
