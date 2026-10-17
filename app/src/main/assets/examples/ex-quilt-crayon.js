// medium: crayon | layout: panel grid with text band | haiku: grandma's old quilt / nine bright patches on the bed / one corner wears thin
// learn: recurring panes fill every row above and below the text band and bleed off the edges, each motif varied by its own rand stream; faint weave dashes quiet the band without meeting the poem. Never copy this subject or palette.
medium({ tooth: 0.75, grain: 0.4, speckle: 0.08, wander: 0.5, taper: 0.25 });
ground('#E4DBC4');
inks(['#4F4038', '#C66C48', '#9C9A62']);
var by0 = H * 0.5 - 236, by1 = H * 0.5 + 236;
var PH = 334, GUT = 12; // pane height chosen so three rows fit tight above and below the band
function pane(x0, y0, k) {
  var pr = rand(seed * 7 + k * 131); // each pane owns its rand stream
  var x1 = x0 + 350, y1 = y0 + PH, mx = x0 + 175, my = y0 + PH / 2;
  area([[[x0, y0], [x1, y0], [x1, y1], [x0, y1]]], { mode: 'side', ink: 1 + (k % 2), angle: -60 + (k % 3) * 25, spacing: 17, len: 56, alpha: 0.42, sharp: true });
  var m = k % 3;
  if (m === 0) line(spiral(mx, my, 2, 62 + pr() * 20, 2.3, pr() * TAU), { ink: 0, width: 3.5, alpha: 0.9, phase: 'detail' });
  else if (m === 1) for (var j = 0; j < 4; j++) line(wave(x0 + 26, y0 + 86 + j * 58, x1 - 26, 9, 2, pr()), { ink: 0, width: 3, alpha: 0.8, sharp: true, phase: 'detail' });
  else line(circle(mx, my, 58 + pr() * 14), { closed: true, ink: 0, width: 4, gaps: 0.35, alpha: 0.85, phase: 'detail' }); // dashed stitch circle
  line([[x0, y0], [x1, y0], [x1, y1], [x0, y1]], { closed: true, sharp: true, width: 2.5, alpha: 0.7, phase: 'outline' }); // seam
}
var k = 0;
for (var ri = 0; ri < 3; ri++) for (var c = 0; c < 3; c++) { // rows run to -86 above (top bleed) down to 24 px over the band
  pane(-20 + c * 370, by0 - 24 - PH - ri * (PH + GUT), k++);
}
for (var ri = 0; ri < 3; ri++) for (var c = 0; c < 3; c++) pane(-20 + c * 370, by1 + 24 + ri * (PH + GUT), k++); // the last row bleeds off the bottom
line([[-20, by0], [1100, by0], [1100, by1], [-20, by1]], { closed: true, sharp: true, width: 3, gaps: 0.12, alpha: 0.8, phase: 'inner' }); // the band's double rules
for (var t = 0; t < 30; t++) { // faint weave dashes line the band's edges only, never under the poem
  var tx = 40 + t * 35 + (t % 2) * 12;
  line([[tx, by0 + 14], [tx + 20, by0 + 14]], { ink: 0, width: 1.6, alpha: 0.28, phase: 'detail' });
  line([[tx - 14, by1 - 14], [tx + 8, by1 - 14]], { ink: 0, width: 1.6, alpha: 0.28, phase: 'detail' });
}
text({ y: 0.5, size: 0.075, ink: 0, halo: false });
