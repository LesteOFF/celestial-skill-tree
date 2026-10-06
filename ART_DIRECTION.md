# Art Direction — The Astral Observatory

All art is original and produced from code in `tools/art/` with fixed seeds (pure Python 3 + Pillow; no external
service, nothing generated at runtime). Regenerate everything with:

```bash
cd tools/art && python3 -c "import background, sprites; background.generate(); sprites.generate()"
python3 tools/art/preview_scene.py out.png 1280 720 --graph   # offline composite + graph overlay
```

Running the generators twice produces identical files. The Python sources are the editable production sources:
shapes, palettes, anchors and seeds are plain data at the top of each generator.

## Concept

An ancient celestial observatory suspended above a fractured moon. The skill tree is a constellation chart projected
from the abandoned structure: the graph is the subject; the scene frames it from the edges. The centre stays dark and
calm (about the central 60% of the width and 65% of the height); detail concentrates at the perimeter and bottom.

## Layers (`assets/nostalgiaskills/textures/gui/skill_tree/background/`)

| File | Size | Authoring | Placement in game (`BackgroundRenderer`) | Parallax |
|---|---|---|---|---|
| `space_base.png` | 2048×1152 | 512×288 ×4, value noise, 11-step ramp, Bayer dither, cool glow upper right, soft vignette | cover-scaled + 6% overscan | 0 |
| `stars_far.png` | 2048×1152 | 1024×576 ×2, 1900 stars, density reduced in the calm centre, mostly dim, sparse bright crosses only outside it | cover | 2% |
| `nebula_far.png` | 2048×1152 | 512×288 ×4, teal ribbon from origin (0.82, 0.15) to the lower middle + violet side ribbon, faded over the centre | cover | 4% |
| `moon_crescent.png` | 512×512 | 128² ×4, sphere-lit crescent (light from upper right), 7-step stone ramp, craters, broken ridge with floating chunks, thin cool rim, earthshine dark side | centre (0.10 W, 0.91 H), size 440·(H/1152) ≈ 21% of a 16:9 width | 6% |
| `astrolabe_ghost.png` | 1024×1024 | 256² ×4, broken rings, degree ticks, partial spokes, rete pointer; bronze at alpha 18–34; no filled discs (cannot read as a node) | centre (0.50 W, 0.52 H), 760·(H/1152) | 6% |
| `observatory_ruins.png` | 512×512 atlas | 4 cells (arch, column, block, lintel), muted stone ramp, carved dots, bronze inlay, faint amber reflection | four corners/edges: (0.06,0.12), (0.93,0.10), (0.95,0.62), (0.87,0.80) | 6% |
| `observatory_platform.png` | 1024×160 | 256×40 ×4, horizontally tileable (noise period divides width), weathered blocks, broken top, glyph band, bronze inlay | bottom edge, tiled, height 118·(H/1152) | 8% |
| `astral_motes.png` | 64×16 | four 16×16 soft motes, stepped alpha | 14 deterministic motes drifting along the outer quarters | animated |

Aspect ratios: full-frame layers are cover-scaled (never stretched); peripheral objects are positioned by
normalised anchors and scaled by window height, so the moon is never an ellipse and 16:9, 16:10 and 21:9 all stay
covered (see `docs/screenshots/ultrawide_2560x1080_gui3.png`). Parallax follows camera pan only — graph zoom never
scales the backdrop — and offsets are clamped inside the overscan so no unpainted edge can appear.

Animation: only the motes drift (slow, frame-rate independent) and available nodes pulse gently. Reduced motion
(client config) disables parallax, drift, pulses and the rotating pending ring while keeping the full static scene.

## Palette

| Purpose | Colour |
|---|---|
| Deep space | `#050C10` (ramp `#030709` → `#153844`) |
| Nebula shadow / teal ribbon | `#12323F`, `#12404A`, `#1B5A60`, `#2A7272` |
| Violet astral accent | `#2E2150` → `#55397A` |
| Ruins stone | `#14181C` → `#56606A`; platform `#0C0F12` → `#31383E` |
| Bronze inlay / instruments | `#6E5226`, astrolabe `#88642C` |
| Amber reflection | `#A0702A` |
| Moon rim light | `#5FAFBA` |
| Inactive connections | `#35291D` (high contrast `#6A5338`) |
| Allocated route | `#D28C2E` |
| Pending route | `#7FD6E8` |
| Important gold | `#F3D15C` |
| Notable violet | `#A977DC` |
| Root stone-metal | `#7C8D88` |
| Enabled control fill / disabled | `#7C0D05` / `#2A2522` |
| Primary text | `#E9E3D8` |

The strongest gold and violet are reserved for interactive elements; scenery uses muted versions only.

## Node frames and icons (`textures/gui/skill_tree/frames/`, `textures/skill_icons/`)

| Frame | Node | Logical diameter at zoom 1 | Shape |
|---|---|---|---|
| `bronze_circle` | minor | 13 | bronze ring, dark interior |
| `violet_facet` | notable | 17 | faceted (octagonal) violet frame |
| `gold_ornate` | keystone | 24 | 8-point ornate gold frame |
| `stone_medallion` | root hub | 30 | notched stone-metal medallion with bronze inner ring |

State overlays (shape-coded, never colour-only): `ring_allocated` (gold halo behind the frame), `ring_available`
(bright rim, gentle pulse), `ring_pending` (dashed cyan ring, rotates unless reduced motion), `mark_conflict` (red X),
`mark_locked` (padlock), `focus` (white corner brackets, also used for keyboard focus), `ring_search` (dotted teal ring).
Locked and conflicting frames are additionally darkened.

Icons are 16×16 pixel art (upscaled ×2 to 32×32) drawn with a small vector DSL (`sprites.py`), auto-shaded with
light from the upper left and a 1-pixel dark outline: 27 category glyphs shared by minors and notables (an icon
always means the same kind of effect), 18 unique keystone emblems and 6 sector hub emblems. The Orb of
Reconfiguration item (`textures/item/orb_of_reconfiguration.png`) is a 16×16 violet orb with a gold band.

Typography: Minecraft's built-in pixel font with subtle shadow for all text.

## Sampling

All textures are nearest-neighbour sampled (Minecraft GUI default, no mipmaps); large layers are authored at a
quarter resolution so every source pixel is a crisp 4×4 block when shown. Textures are drawn with alpha blending
enabled and the blend state is restored after drawing.

## Verification

Captured from the real client (Xvfb + Mesa, `-Pvisualqa`), see `docs/screenshots/`:
1000×563 (GUI 2; GUI 3 is clamped to 2 by Minecraft at that height), 1280×720 and 1920×1080 at GUI 2 and 3,
2560×1080 ultrawide, plus close-ups of every node state, tooltips near all four edges, stats and reset panels,
reduced motion and high contrast.
