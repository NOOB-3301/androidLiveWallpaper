// harvest moon, pencil (ref 08): hatched dark side, white side-of-the-stick light, a hatched shadow, poem below
medium({ tooth: 0.6, grain: 0.35, wander: 0.5, taper: 0.7 });
ground('#EACDB2');
inks(['#3A3A3E', '#FBF6EE']);
var cx = 470, cy = 760, R0 = 230, mid = [cx + 15, cy - 25];
line(circle(cx, cy, R0), { closed: true, width: 1.2, alpha: 0.4, gaps: 0.1, phase: 'outline' });
var dark = arc(cx, cy, R0, -0.68, 2.6).concat([mid]), lit = arc(cx, cy, R0, 2.6, 5.6).concat([mid]);
area([dark], { mode: 'hatch', angle: 75, spacing: 5, width: 1.3, alpha: 0.75, len: 34, jitter: 18 });
area([dark], { mode: 'hatch', angle: -50, spacing: 8, width: 1.1, alpha: 0.5, len: 26, jitter: 20, phase: 'detail' });
area([lit], { mode: 'hatch', angle: 20, spacing: 14, width: 0.9, alpha: 0.3, len: 22, jitter: 25 });
area([lit], { mode: 'side', ink: 1, angle: -40, spacing: 8, alpha: 0.55, len: 60, phase: 'detail' });
area([ellipse(cx + 140, cy + 300, 340, 50)], { mode: 'hatch', angle: 3, spacing: 4, width: 1, alpha: 0.45, len: 70, jitter: 6, phase: 'detail' });
area([ellipse(cx + 60, cy + 300, 200, 30)], { mode: 'hatch', angle: 85, spacing: 4, width: 1, alpha: 0.5, len: 26, jitter: 8, phase: 'dark' });
text({ y: 0.55, size: 0.085, ink: 0, halo: true });
