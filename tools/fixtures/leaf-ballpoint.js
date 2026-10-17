// autumn leaf, ballpoint (ref 13): leaf outline, veins, patchy hatching in turning directions, wash under
medium({ tooth: 0.3, grain: 0.25, wander: 0.55, taper: 0.5 });
ground('#EEE8DA');
inks(['#2D4FB0', '#1C2F78']);
var leaf = [[60, 1200], [250, 820], [540, 700], [830, 820], [1020, 1200], [830, 1580], [540, 1700], [250, 1580]];
line(leaf, { closed: true, width: 1.8, ink: 1, phase: 'outline' });
line([[0, 1200], [1050, 1200]], { width: 2, ink: 1, phase: 'inner' });
for (var i = -3; i <= 3; i++) if (i) line([[540 + i * 120, 1200], [540 + i * 150, i % 2 ? 760 : 1640]], { width: 1.2, ink: 1, phase: 'inner' });
area([leaf], { mode: 'wash', ink: 0, alpha: 0.18 });
area([leaf], { mode: 'hatch', angle: -40, spacing: 6, width: 1.2, len: 34, jitter: 35, alpha: 0.7 });
area([leaf.slice(4).concat([[60, 1200]])], { mode: 'hatch', angle: 20, spacing: 7, width: 1.2, len: 30, jitter: 30, alpha: 0.6, phase: 'detail' });
text({ y: 0.5, size: 0.075, ink: 1, halo: true });
