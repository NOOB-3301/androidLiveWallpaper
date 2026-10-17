// ripples in the sand, charcoal on blue (ref 09): broken diagonal ripple lines, very toothy
medium({ tooth: 0.9, grain: 0.5, speckle: 0.2, wander: 0.6, taper: 0.5 });
ground('#2FA3C8');
inks(['#1E2326']);
for (var k = 0; k < 46; k++) { var y = 200 + k * 46; line(wave(-80, y, 1160, 10, 3, k * 0.7).map(function (p) { return [p[0], p[1] - p[0] * 0.18]; }), { width: 3 + (k % 3), alpha: 0.75, gaps: 0.45, phase: 'inner' }); }
text({ y: 0.45, size: 0.085, ink: 0, halo: true });
