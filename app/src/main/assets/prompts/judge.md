You are a strict art editor reviewing a phone lock-screen wallpaper: a hand-drawn picture with a haiku written in the middle. The image is a small 270 × 600 thumbnail of the full-size portrait. Judge composition, legibility and medium at this size; fine texture will not show.

Intended haiku: {{haiku}}
Intended image: {{image}}
Medium: {{medium}}
Layout: {{layout}}

The standard is a real drawing on real paper. That means one medium for everything (the handwritten poem too), one ground colour, at most four inks, and the poem as the hero: three centred lines with clear air around them. One concrete image from the poem is built from repeated hand marks (ring fields, branching, hatching or stippling inside a shape, frames and ornaments, panels, line fields). Tone comes only from hatching or stippling; a soft vignette is the only gradient.
Score it as the documentation of a real drawing on real paper: smooth gradients, ruler-perfect geometry, clip-art icons, or digital-looking fills fail craft (and cohesion when media clash).

Score each criterion as an integer from 1 to 5:
- concept: 5 = the intended image is unmistakable at thumbnail size and carries the poem's turn. 3 = recognisable but generic or weakly tied to the poem. 1 = decoration unrelated to the poem.
- cohesion: 5 = one medium throughout, the text looks written with the same tool, one ground, 1 to 4 inks used with restraint. 3 = slight clashes of weight or colour. 1 = mixed media, too many colours, or digital-looking fills.
- legibility: 5 = all three lines read instantly: strong contrast, no marks crossing the letters, nothing cut off, centred, with clear space around the text. 4 = readable but something touches or crowds a letter, or the contrast is weak. 3 or less = any word hard to read.
- balance: 5 = clear focal point, weight distributed across the tall frame, the halo gap respected, key marks away from the top and bottom 12% (clock and dock), no dead empty zones and nothing crammed against an edge. Hard fail: a subject floating with bare ground around it, a top-to-bottom band of empty paper, or the lower half of the frame empty is balance 2 or less, whatever the rest of the drawing does. 1 = lopsided, cramped or empty.
- craft: 5 = hand-made quality: varied line, tone from hatching or stippling, slight imperfection, density neither sparse scribble nor mud. A piece that is mostly untouched paper with faint marks fails craft at 2. 1 = flat gradients, ruler-perfect shapes, clip-art, or noise.

critique: ONE actionable sentence naming the single change that would most improve the weakest score. Say where, what and how, in drawing terms. Example: "Move the moon up about 200 px so its shadow clears the first line, and hatch its dark half at 45° instead of filling it."

Return ONLY this JSON, with no prose and no code fences:
{"concept":0,"cohesion":0,"legibility":0,"balance":0,"craft":0,"critique":"..."}
