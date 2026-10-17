// a grate, chalk on black (ref 06): dashed swirl frame, fire built with side + stipple, warm text
medium({ tooth: 0.8, grain: 0.4, speckle: 0.15, wander: 0.6, taper: 0.4 });
ground('#1E1C1B');
inks(['#E08A2E', '#F2C14E', '#B8401E']);
var r = rand(seed);
line([[120, 760], [960, 760], [960, 1360], [120, 1360]], { closed: true, sharp: true, width: 3, gaps: 0.3, alpha: 0.8, phase: 'outline' });
[[120, 760], [960, 760], [960, 1360], [120, 1360]].forEach(function (c, i) {
  line(spiral(c[0] + (i % 3 ? 40 : -40), c[1] + (i < 2 ? -30 : 30), 4, 34, 1.6, i), { width: 2.2, alpha: 0.8, phase: 'detail' });
});
var fire = [[90, 1980], [990, 1980], [990, 1700], [880, 1640], [800, 1700], [700, 1600], [600, 1690], [500, 1590], [400, 1690], [300, 1610], [200, 1700], [90, 1650]];
area([fire], { mode: 'side', ink: 0, angle: -88, spacing: 12, len: 80, alpha: 0.55, sharp: true });
area([fire], { mode: 'side', ink: 2, angle: -80, spacing: 22, len: 50, alpha: 0.45, phase: 'detail', sharp: true });
area([[[120, 1980], [960, 1980], [960, 1860], [120, 1860]]], { mode: 'stipple', ink: 1, spacing: 7, width: 4, density: 0.6, phase: 'dark' });
area([[[100, 500], [980, 500], [980, 760], [100, 760]]], { mode: 'stipple', ink: 0, spacing: 22, width: 2.5, density: 0.25, alpha: 0.6, phase: 'detail' });
text({ y: 0.44, size: 0.085, ink: 0 });
