// the level's bubble, technical pen (ref 04): rain hatch above and below a ruled band, a lightning zigzag
medium({ tooth: 0.12, grain: 0.1, wander: 0.25, taper: 0.05 });
ground('#E9E6E0');
inks(['#2A2420', '#7A5A44']);
area([[[0, 260], [1080, 260], [1080, 900], [0, 900]]], { mode: 'hatch', ink: 1, angle: 75, spacing: 26, len: 80, jitter: 6, width: 1.6, sharp: true });
area([[[0, 1500], [1080, 1500], [1080, 2200], [0, 2200]]], { mode: 'hatch', ink: 1, angle: 75, spacing: 26, len: 80, jitter: 6, width: 1.6, sharp: true });
[930, 950, 1450, 1470].forEach(function (y) { line([[-10, y], [1090, y]], { width: y % 2 ? 2 : 3, phase: 'outline' }); });
for (var x = 30; x < 1080; x += 36) { line([[x, 900], [x, x % 180 < 36 ? 860 : 880]], { width: 1.6, sharp: true, phase: 'detail' }); line([[x, 1500], [x, x % 180 < 36 ? 1540 : 1520]], { width: 1.6, sharp: true, phase: 'detail' }); }
line([[790, 260], [760, 380], [800, 400], [740, 560], [780, 580], [730, 700]], { sharp: true, width: 2.2, phase: 'inner' });
text({ y: 0.5, size: 0.08, ink: 0, halo: false });
