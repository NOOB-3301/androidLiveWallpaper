// medium: technical pen | layout: band across text | haiku: five small swallows / the wire holds a low blue chord / one leans into wind
// learn: a ruled band that carries the poem; even dash fields above and below that thin toward the band; the picture's key accents perch on the band's top rule. Never copy this subject or palette.
medium({ tooth: 0.1, grain: 0.08, wander: 0.3, taper: 0 });
ground('#E4E3DD');
inks(['#262626', '#8A5A3C']);
var r = rand(seed);
var yBand = H * 0.5, bandHalf = 218, top = yBand - bandHalf, bot = yBand + bandHalf;
for (var i = 0; i < 120; i++) { // dash fields of weather above and below, thinning toward the band
  var above = i < 60, y = above ? SAFE.y0 + 20 + r() * (top - SAFE.y0 - 130) : bot + 130 + r() * (SAFE.y1 - bot - 170);
  var x = r() * 1140 - 30, d = above ? (top - y) / (top - SAFE.y0) : (y - bot) / (SAFE.y1 - bot);
  line([[x, y], [x + 30 + r() * 52, y + r() * 12 - 6]], { ink: r() < 0.25 ? 1 : 0, width: 1.7, alpha: 0.22 + d * 0.45, gaps: 0.08, phase: 'outline' });
}
[top - 12, top - 2, bot + 2, bot + 12].forEach(function (y) { line([[-10, y], [1090, y]], { width: 3, sharp: true, phase: 'inner' }); }); // the doubled wire carries the poem
for (var x = 16; x < 1080; x += 44) line([[x + r.range(-2, 2), top - 12], [x, top - 30]], { width: 1.6, alpha: 0.9, sharp: true, phase: 'detail' }); // ticks on the top rule
[150, 365, 585, 800, 990].forEach(function (bx, i) { // five swallows perched on the top rule, the last leans into wind
  var by = top - 12, s = 30 + (i % 3) * 9, flap = i === 4 ? 0.45 : 1;
  line([[bx - s, by], [bx - s * 0.35, by - s * 0.8 * flap], [bx, by - s * 0.18]], { width: 2.4, alpha: 0.95, phase: 'inner' });
  line([[bx, by - s * 0.18], [bx + s * 0.35, by - s * 0.8 * flap], [bx + s, by]], { width: 2.4, alpha: 0.95, phase: 'inner' });
  line(circle(bx, by - s * 0.24, 3.5), { closed: true, width: 1.5, alpha: 0.9, phase: 'detail' });
});
line([[812, bot + 44], [782, bot + 158], [822, bot + 172], [762, bot + 300], [798, bot + 314], [752, bot + 430]], { sharp: true, width: 2.4, phase: 'inner' }); // one lightning crack below
text({ y: 0.5, size: 0.08, ink: 0, halo: true });
