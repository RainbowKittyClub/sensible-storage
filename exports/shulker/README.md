# Shulker cargo model — editable export

The two-part shulker box a cart carries, exported so it can be opened in
Blockbench or Blender instead of read as JSON.

**This is a copy, not the source.** The models are produced by
`ShulkerElementModelProvider` at datagen time, so editing anything here changes
nothing in the mod, and the next `./gradlew generateAssets` overwrites the
generated originals regardless. See "Getting changes back in" at the bottom.

Nothing here is tracked by git, for the same reason
`src/datagen/resources/plankedchests_vanilla/` is not: the sheets are vanilla's
client art, and the mod references them at runtime rather than redistributing
them.

## Layout

```
assets/minecraft/textures/entity/shulker/*.png   vanilla's 17 sheets, 64x64
assets/plankedchests/models/block/
    shulker_box.json     base + lid together, closed - start here
    shulker_base.json    just the base, as the mod ships it
    shulker_lid.json     just the lid, as the mod ships it
blender/shulker_*.obj    the same three, as OBJ + MTL
preview/shulker_*.png    rendered previews (closed, open, base alone)
export_model.py          regenerates all of the above from src/main/generated
preview.py               re-renders the previews with Blender
```

## Blockbench

Open `assets/plankedchests/models/block/shulker_box.json`. Two things are set
up so it loads clean rather than as an untextured grey box:

- **The texture is bound to a real path.** The generated models bind only
  `particle` and leave `#shulker` for a per-colour child model to fill in, which
  is unresolvable on its own. Blockbench resolves `minecraft:entity/shulker/shulker`
  by walking up from the model's own path to the `assets/` root, which is why the
  file sits in a resource-pack directory layout rather than loose.
- **`texture_size` is `[64, 64]`.** Blockbench parses a Java model's `uv` as
  `uv * resolution / 16`, so with this set it shows UVs as real texels on the
  64×64 sheet while the file keeps vanilla's own 0–16 values. Don't remove it, and
  don't "fix" the 0–16 numbers to texels — the game only reads 0–16.

To check another dye, point the `shulker` texture at any of the other sixteen
PNGs; the geometry is shared across all seventeen and only the sheet differs.

The four cubes are named. `base` and `lid` are vanilla's own two cuboids;
`base_inside` and `lid_inside` are their inverted twins, which occupy exactly
the same space and carry the inward-facing surfaces. In Blockbench the twins
look like duplicates sitting on top of the originals — that is what they are,
and the "How the model maps to the sheet" section below says why.

## Blender

Import `blender/shulker_box.obj` with the default axes (Forward −Z, Up Y) — the
OBJ is written in Minecraft's frame, so the default conversion is the correct
one, and it lands Y-up as Blender Z-up. One block is one unit.

Two settings are not optional if you want it to look like the game:

- **Interpolation `Closest`** on the image texture node. A 64×64 sheet filtered
  smoothly is unreadable.
- **Alpha wired from the image.** Most of the base's side band is transparent on
  purpose, and the MTL declares `map_d` so the importer should do this for you.

`preview.py` does both, plus backface culling.

## Previews

```
blender -b -P preview.py -- blender/shulker_box.obj preview/shulker
```

Writes `_closed`, `_open` and `_base`. The open pose is `ShulkerCartDisplay` at
progress 1: the lid rises half a block and turns 270° about the vertical centre
line.

The preview **culls back faces**, which is the point of it. A block model culls
them and vanilla's shulker renderer does not, and that difference is the whole
reason each part carries an inverted twin. A preview that drew both sides would
say nothing about whether the model works.

It culls by *deleting* the polygons facing away from its fixed orthographic
camera, rather than by a Backfacing shader mix. That matters here: each twin is
exactly coincident with its original, so a mix leaves the renderer two surfaces
at one depth and it speckles, where deleting leaves exactly the one quad the
game would rasterise.

Its lights also cast no shadows, because Minecraft lights an item model with
plain directional terms and no occlusion — a ray-traced interior would go black
behind the lid and misreport a model that is fine.

One artefact survives and is not a defect: in the closed render, the narrow band
where the lid's skirt overlaps the base shows dithered speckle. Those two
surfaces really are coplanar, in vanilla too, and a ray tracer has no way to
order them. The game doesn't care, because the alpha masks interlock exactly —
the base's side is opaque across its middle eight columns for its top four rows
and the lid's skirt is opaque across the outer four each side, so no pixel is
ever drawn by both and the cutout pass never has a tie to break.

## How the model maps to the sheet

Vanilla's `ShulkerModel.createShellMesh` is two cubes: the lid 16×12×16 at
`texOffs (0,0)`, the base 16×8×16 at `texOffs (0,28)`. They overlap through the
middle four pixels, which is why a closed box reads as a full cube.

Both are transcribed one-to-one, and then emitted a second time with `from` and
`to` swapped. That inversion is what supplies the interior, and it is not a
trick of the file format but a consequence of how the baker reads it:
`FaceBakery`'s vertex picker takes `from` for every `MIN_*` extent and `to` for
every `MAX_*`, with no sorting, so an inverted cube's faces land on the opposite
wall still pointing the way their own names say — inward. Vanilla validates only
that each coordinate falls within −16..32, and `recalculateWinding` leaves the
quads alone because each already agrees with its own normal.

Each face moves to its opposite key and takes one mirrored axis, since a quad
seen from behind is a quad mirrored: the four sides swap their v pair, up and
down their u. `ElementModels.invertedTwin` does all of this, so the geometry
here needs no bespoke UV arithmetic at all — the interior is vanilla's own
pixels at vanilla's own depth, including the parts that look surprising:

- The base's top face is 32/256 non-transparent, because the lid covers it.
- The lid's bottom face is 28/256 — a thin rim and nothing else — which is why
  an open shulker lets you see straight up into the lid's inside.
- The base's bottom face is fully opaque, and serves as both the box's underside
  from outside and its interior floor from within.

## Getting changes back in

There is no importer. `ShulkerElementModelProvider` writes the geometry from
Java, so a change made here has to be made there too — `addGeometry`'s two
cubes, `ElementModels.box`'s unwrap, and `ElementModels.invertedTwin` for the
interior. Note that the twins are derived, not authored: edit an original and
its twin follows, but editing a twin by hand in Blockbench has nowhere to go.

If hand-authoring turns out to be the better workflow, the alternative is to
have the provider read a checked-in JSON out of `src/datagen/resources/` and
emit it, the way `ShulkerMinecartTextureProvider` already reads a hand-editable
overlay PNG. That's a change to the provider, not something this directory can
do on its own.

## Regenerating

After datagen changes the models:

```
python3 export_model.py --out . --name shulker_box \
    --texture assets/minecraft/textures/entity/shulker/shulker.png \
    --texture-ref minecraft:entity/shulker/shulker --texture-key shulker \
    --part ../../src/main/generated/assets/plankedchests/models/block/shulker_base.json \
    --part ../../src/main/generated/assets/plankedchests/models/block/shulker_lid.json \
    --cube-names base,base_inside,lid,lid_inside
```

`export_model.py` is not shulker-specific — the chest models take the same
treatment, with `--texture-key chest` and their own sheet.
