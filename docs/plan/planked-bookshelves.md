# Planked Bookshelves

A bookshelf and a chiseled bookshelf per `WoodType`, built the way `planked-crafting-tables.md`
built the tables — real block state for a client running the mod, vanilla's own block for everyone
else, per-wood textures and a fallback recipe override — except that the frame is drawn from
grayscale overlays tinted with the wood's median plank colour rather than cut from the planks
themselves. The chiseled bookshelf also gets a book per slot drawn at random from 18 book sprites,
instead of vanilla's fixed six — and so does vanilla's own chiseled bookshelf, for every client that
runs the mod or has the pack. There is no oak variant of either: vanilla's are oak already. Fuel
values were a separate change, for every planked block at once, and not part of this plan; it
landed afterwards as `Flammability`, which also took over the bookshelves' fire odds.

Every claim here was checked against the 26.2 decompile (`libs/decompiledjava/minecraft-26.2/`),
the merged deobf jar's own assets and data, the Fabric API 0.159.0 bundle, the Polymer checkout,
the overlay files in `src/datagen/resources/sensiblestorage_overlay/block/`, and the live server's
generated Polymer pack. The tint formula and the random-book weighting were measured, not
reasoned: the numbers are in the Reference sections. Unverified: anything about how a finished
shelf looks in-world, which needs the dev client.

Stage order: S1 (colour helpers in kalic0re) and S2 (the fallback recipe defers to any other match)
are prerequisites, independent of each other and of everything else. The plain bookshelf is S3 then
S4, and is complete at S4. The chiseled bookshelf is S5, S6, S7, and needs S1 and S2 but nothing
from S3–S4. S6 draws vanilla's own six books in their vanilla slots, so geometry can be checked
against a vanilla shelf before S7 makes the books random, on the planked shelves and vanilla's own
alike. S8 gives a vanilla client with the pack the planked chiseled frame on every shelf, S7's
books drawn over it by a display entity, and completes the feature.

## Findings that reshape the work

**The overlays are a luminance palette of vanilla's own colours, so `PngAssets.tint` is the wrong
operation and a reference-normalised one is the right one.** Every gray level in `bookshelf.png`,
`chiseled_bookshelf_top.png`, `_side.png` and `_empty.png` maps to exactly one colour in vanilla's
matching texture (spread 0), and the gray is that colour's Rec. 709 luma, truncated — true of all
896 gray pixels across the four files, where Rec. 601 matches fewer than half. Gray 145 is
`(175, 143, 85)`, which is also the per-channel median of `oak_planks.png`. `PngAssets.tint`
multiplies by `gray / 255` and comes out far too dark — mean error 29–60 per channel against vanilla
on oak. Scaling the median by `gray / 145` instead reproduces vanilla's oak textures to a mean of
2.2–2.8 per channel; an HSL colourise lands at 7–14. So S1 adds a colourise that takes the reference
gray explicitly. For any other wood, gray 145 becomes that wood's median and every other gray scales
from it, so a dark wood's shelf is dark. Four pale woods clip at the brightest gray (173, a ×1.19
scale): cherry, pale oak, pine, yucca palm (Reference).

**The bookshelf overlay uses the tables' convention; the chiseled overlays do not.** `bookshelf.png`
is 128 gray pixels (the frame) and 128 `PngAssets.CUTOUT` magenta pixels, which sit exactly on
vanilla's books: magenta means "vanilla's pixel here", as in `planked-crafting-tables.md`. The three
chiseled overlays are gray end to end, with no magenta, so they are tinted whole. The 18 books in
`chiseled_bookshelf/` are 4×6 full-colour sprites, not overlays. Books 1–6 are vanilla's own six,
cut pixel for pixel from `chiseled_bookshelf_occupied.png`; books 7–18 are new.

**Both vanilla recipes mix planks with a second ingredient, and the chiseled shelf's pairs each
wood's planks with its own slab, so the fallback has to defer to the wood recipes themselves rather
than to a tag.** `PlankFallbackRecipe` refuses a grid only when every filled slot holds the same
`claimed` item (`PlankFallbackRecipe#isClaimed`), which works for the chest's ring and the table's
square because those are planks alone. `bookshelf.json` is `###/XXX/###` over planks and
`minecraft:book`, and `chiseled_bookshelf.json` the same shape over planks and
`#minecraft:wooden_slabs`: never uniform, so never claimed, and a wood's own shelf would lose the
sort-order tie to vanilla's exactly as the chest did. Claiming over the plank slots alone would fix
the bookshelf but not the chiseled shelf, whose recipe takes the wood's own slab: birch planks over
spruce slabs must fall through to vanilla while birch over birch must not, and saying that in JSON
means a planks-and-slab pair per wood. The rule the claimed tags were approximating is simpler:
step aside whenever any other crafting recipe matches the same grid. Each wood's own recipe then
claims exactly the grids it covers; oak, fir before it had a recipe, mixed planks and mismatched
slabs all fall through because nothing else matches. Every one of the 28 woods has a slab named
`<namespace>:<wood>_slab` in `#minecraft:wooden_slabs` (all five modded jars checked), so the
per-wood recipe has one to name.

**A pack client can have the planked chiseled frame on every shelf, but only if the books leave the
block state.** The shelf's state is `facing` × six occupancy flags: 256 states per wood, 6,912
across 27 woods, against a `FULL_BLOCK` budget of 1149 note block states, of which the live server's
pack already uses 427 (`note_block.json` in the live `polymer/resource_pack.zip` under
`/mnt/WORKSPACE/minecraft-server-data/data/`) and the tables and plain bookshelves take 27 each. The
plain bookshelf is stateless, like the table. Reserving only the all-empty and all-full patterns
(216 states) was the first plan and was dropped: a partly filled shelf would fall back to vanilla's
oak frame, so a shelf's frame would change as books went in and out. What the frame needs from the
state is only its facing, 4 states per wood and 108 in all, bringing the pool to 589 of 1149. The
occupancy and the books then go to one item display entity per shelf, which the server keeps in
step with the block:

- **One entity carries all six slots.** 26.2 item definitions can be a `composite` of `select`
  models keyed on the stack's `custom_model_data` strings by index (`CompositeModel.java`,
  `CustomModelDataProperty.java`), so a stack whose string *n* is slot *n*'s book, or empty,
  draws exactly the filled slots. A `select` with no `fallback` draws the missing model
  (`SelectItemModel.java:90`), so each gets an `empty` one. Block models, and block-atlas
  sprites, are fine as item models (`CuboidItemModelWrapper#validateAtlasUsage`), so S7's book
  models are reused as they are.
- **The display draws books and nothing else.** Terrain draws the whole frame, filled slots
  included, as their empty slots; the display adds each book and the shadow it casts, the one
  part of an occupied slot's background that differs from its empty slot (Findings: vanilla's
  occupied sheet), from a per-wood sheet holding only the shadow. Drawing the occupied background
  on the entity instead would reproduce the same pixels, but light a filled slot's frame as an
  entity and an empty one's as terrain.
- **The server picks the books the client would.** Terrain draws a slot's book from a random source
  seeded by position (Reference: how a slot's book is chosen), all of it common code: the server
  can run the same `SingleThreadedRandomSource` through the same steps and get the same book, so a
  display over a planked shelf matches what a client running the mod draws there, and what any
  pack client draws on vanilla's shelf at that position.
- **Placing it.** An item model is centred on the entity (`ItemTransform.java:25,45`) and turned a
  further 180° about Y by the renderer (`DisplayRenderer.java:197`, kalic0re's
  `ItemDisplayPose`). An entity is lit by the block its eye is in (`EntityRenderer.java:52`,
  `Entity.java:2001`), and an item display is sized 0×0 (`EntityTypes.java:567-570`), so its eye is
  its position: placed inside the shelf it would be lit as solid, which is black. The entity sits
  in the block in front, whose light is what terrain lights the front face with, and its
  transformation carries the model back onto the face, just in front of it.
- **What differs from terrain, accepted for now.** Items are shaded by vanilla's two directional
  lights (`light.glsl`, `Lighting.java:20-21`) whatever a face's `shade` says
  (`VertexConsumer#putBakedQuad` ignores it), where terrain darkens a face by a fixed amount: a book
  on a north- or south-facing shelf comes out at 0.74 of full brightness against terrain's 0.8, on
  an east- or west-facing one 0.50 against 0.6. An entity takes one light value, so the books miss
  smooth lighting's gradient across the face. A display stops drawing beyond 64 blocks × its view
  range × the entity-distance setting (`Display.java:403`), past which a pack client sees empty
  fronts. And a pack client hears the note block's wood sounds, not the chiseled shelf's, stepping
  on, hitting or breaking one. The lighting and the entity count were deferred on review.

**The chiseled bookshelf's block entity hard-codes vanilla's type.**
`ChiseledBookShelfBlockEntity`'s only constructor passes `BlockEntityTypes.CHISELED_BOOKSHELF`
(`ChiseledBookShelfBlockEntity.java:31-33`), whose valid blocks are vanilla's shelf alone
(`BlockEntityTypes.java:172`), and `ChiseledBookShelfBlock#newBlockEntity` constructs one
(`ChiseledBookShelfBlock.java:151`). Rather than a second type and a subclassed entity, add each
planked shelf to vanilla's type with `FabricBlockEntityType#addValidBlock` (fabric-object-builder
24.1.0). It is mixed into `BlockEntityType` by `BlockEntityTypeMixin`, not interface-injected, so
the call needs a cast. Keeping vanilla's entity keeps `ShelfLabels`' hover labels working
unchanged: it tests `instanceof ChiseledBookShelfBlock` and
`instanceof ChiseledBookShelfBlockEntity` (`ShelfLabels.java:50,62`), both of which a planked shelf
passes.

**Random books are feasible as blockstate data alone, but only with per-slot weights that are
coprime.** Vanilla draws a chiseled shelf as 52 multipart parts — per facing, a body and one quad
per slot, occupied or empty — and each occupied quad shows a fixed region of
`chiseled_bookshelf_occupied.png`, so slot *n* always shows the same book. A multipart `apply` may
instead be a weighted list of models, but `MultiPartModel#collectParts` draws one seed per block and
resets the random source to it before *every* part (`MultiPartModel.java:47-57`), so six identical
weighted lists pick the same index — every slot the same book. Selection is
`nextInt(totalWeight)` walked through cumulative weight (`WeightedList.java:85-91`,
`BitRandomSource.java:17-29`), so each slot's choice is a function of one shared 31-bit sample;
what decorrelates the slots is giving each a different, pairwise-coprime total. Simulated over
200,000 samples against that exact arithmetic (Reference): identical lists give 18 distinct
shelves, weights of slot-index + 1 give 1,080 and strongly correlated neighbours, and totals of six
primes near 1000 give slots indistinguishable from independent. Three consequences follow from the
seed being `BlockState#getSeed(pos)` (`SectionCompiler.java:107`), which is `Mth.getSeed(pos)` for
any block that does not override it (`BlockBehaviour.java:283-285`; the chiseled shelf does not) —
position only: a slot's book
never changes when a neighbouring slot fills or empties; a slot refilled shows the same book it did
before; and the book drawn says nothing about the book stored — an enchanted book looks like any
other, as in vanilla.

**Books go on top of the slot as a second quad, which keeps the book textures shared.** Rather than
baking 18 occupied sheets per wood, an occupied slot is drawn as the wood's slot background plus a
book quad in the same plane. Vanilla already layers coplanar quads — the grass block's side overlay
is a second element with the same bounds — and 26.2 picks each quad's chunk layer from its own
sprite's transparency (`SectionCompiler.java:68`, `ChunkSectionLayer.byTransparency`), so the book
goes to the cutout layer, drawn after the solid background. What makes that work is the *same
bounds*: terrain's depth test passes on equal (`DepthStencilState.DEFAULT` is
`GREATER_THAN_OR_EQUAL`), so a quad with identical corners lands exactly on the one beneath and the
later one wins, where a smaller quad in the same plane rounds its depth differently and z-fights —
which the first build, cut to the book's own 4×6, did (S6's As built). So each book is a 16×16
sheet, transparent but for the book at every slot's position, drawn on vanilla's own slot template:
18 sheets and 108 per-slot models, all wood-independent; only the background is per wood.

**Vanilla's occupied sheet also darkens a column beside each left and middle book.** Outside the
books' own footprints, 20 pixels differ between `chiseled_bookshelf_occupied.png` and `_empty.png`:
columns 5 and 10, rows 2–6 and 10–14 — the column right of each left and middle book, a shadow
(the right slot's book sits against the frame). The overlay art has no occupied background, so S6
derives one from vanilla's two sheets.

**Two vanilla consumers name the vanilla blocks exactly.** The lectern recipe takes
`minecraft:bookshelf`, not a tag (`VanillaRecipeProvider.java:1140-1146`), so a planked bookshelf
could not make a lectern — the hopper's problem with chests, solved the same way through Fabric's
`c:bookshelves` tag (fabric-convention-tags-v2 4.7.1, which lists only `minecraft:bookshelf`). The
"Power of Books" advancement names `minecraft:chiseled_bookshelf` in a block-state predicate
(`data/minecraft/advancement/adventure/read_power_of_chiseled_bookshelf.json`), so a planked shelf
read by a comparator would not count; S6 overrides it to read a tag of every chiseled shelf. Nothing
in gameplay code tests either block by identity — no repeat of the crafting menu's `stillValid`
trap.

## How these stages get tested

Datagen changes are checked by byte comparison against a baseline generated from `HEAD`, per
`docs/TESTING.md`: nothing outside the new files and the shared tags and lang should move. The dev
server does the rest over RCON, as it did for the tables: `setblock` and `execute if block … #tag`
for placement and tags; `loot spawn … mine` with a plain and a silk-touch tool for drops; a crafter
filled by NBT and powered by a redstone block for every recipe, since it resolves through the same
`RecipeManager` as the crafting menu; `item replace block … container.<n>` and
`execute if block …[slot_<n>_occupied=true]` for the chiseled shelf's slots. The five wood mods are
in `run/mods`, so all 28 woods register. How a shelf looks — tint, book placement, randomness —
needs the dev client, and both pack tiers a vanilla client with the pack. A stage is not finished
until its Done when is observed.

## S1 — Median colour and colourise in kalic0re — **done**

**Build.** Two helpers beside `tint` in kalic0re's `PngAssets`. `medianColour(BufferedImage)`: the
per-channel median over opaque pixels, which for every one of the 28 plank textures is within 6 per
channel of the median pixel by luma (Reference), so the simpler definition is enough.
`colourise(BufferedImage gray, int colour, int referenceGray)`: each opaque gray pixel becomes
`colour × gray / referenceGray` per channel, clamped to 255 — which costs cherry, pale oak, pine
and yucca palm a little highlight at the brightest gray, accepted rather than compressing every
wood's range; `CUTOUT` magenta and transparent
pixels pass through untouched, so a following `composite` still reads the chroma key. Release as
0.19.0, `publishToMavenLocal`, bump `kalic0re_version`.

**Done when.** `colourise(overlay, medianColour(oak_planks), 145)` over each of the four gray
overlays matches vanilla's oak texture to a mean of 3 or less per channel, measured in a datagen
run; and kalic0re's build and lint pass. The reference is worth computing rather than typing: it
is the Rec. 709 luma of oak's median, truncated, so a datagen assertion that it still comes to 145
catches a change to the vendored oak planks.

### As built

`PngAssets.medianColour` and `PngAssets.colourise` as planned, released as kalic0re 0.19.0.
`colourise` also refuses a pixel that is not gray, naming it, so a stray colour in an overlay fails
the datagen run rather than tinting oddly. Measured against the published jar itself, with a
single-file Java program rather than a datagen run: oak's median comes out `#AF8F55`, and the four
overlays land at 2.84, 2.36, 2.60 and 2.19 per channel against vanilla's oak textures, magenta
passing through untouched. The reference gray is computed in datagen, not typed: `WoodTints`
takes oak's median's Rec. 709 luma and fails the run if it is not 145.

## S2 — The fallback recipe defers to any other match — **done**

**Build.** After `pattern.matches`, `PlankFallbackRecipe#matches` also requires that no other
crafting recipe matches the same input. On a `ServerLevel`, `recipeAccess()` is the
`RecipeManager` itself (`ServerLevel.java:1461`); walk its `getRecipes()` (`RecipeManager.java:175`)
for recipes of type `RecipeType.CRAFTING` that are not themselves a `PlankFallbackRecipe` — which
also rules out recursing back into this check — and refuse the grid if any matches. The lookup that
called `matches` already walks every crafting recipe the same way (`RecipeMap.java:48-50`), so this
is one more pass, and only for a grid the fallback's own pattern already matched. The `claimed`
field and `isClaimed` go; `chest.json` and `crafting_table.json` drop `claimed`; and
`ClaimedPlanksProvider` retires with its two tags, since a wood's recipe existing is now what claims
its grids.

**Done when.** `generateAssets` is byte-identical to its baseline except the two claimed-planks
tags disappearing, and on the dev server the chest and table crafter checks from
`planked-crafting-tables.md` pass unchanged: each wood's uniform ring and square give its own,
oak's square and every mixed grid give vanilla's.

### As built

As planned. On a level that is not a `ServerLevel` the recipe is its pattern alone; nothing else
evaluates recipes in 26.2. Against a `HEAD` baseline the datagen output was byte-identical but for
`planks_with_chest.json` and `planks_with_crafting_table.json` disappearing, and every chest and
table crafter check passed: birch, spruce, oak, fir and redwood rings to their own chests, oak with
spruce to vanilla's; birch, spruce and fir squares to their own tables, oak's and oak with birch to
vanilla's.

## S3 — Bookshelf blocks, registration and disguise — **done**

**Build.** `PlankedBookshelfBlock`, a `Block` implementing `PolymerTexturedBlock` and
`PolymerClientDecoded`, takes the table's three tiers exactly: the real state for a client that
`HANDSHAKE.supportsAll`; for a client with the pack, a `FULL_BLOCK` state from
`PolymerBlockResourceUtils.requestBlock` bound to the wood's bookshelf model; vanilla's
`minecraft:bookshelf` for everyone else. Vanilla's bookshelf is a plain `Block` (`Blocks.java:1057`)
with no class of its own, so there is nothing to subclass beyond `Block`. `BookshelfBlocks`
registers one per wood that `ChestBlocks` registered, `Properties.ofFullCopy(Blocks.BOOKSHELF)`,
block item a `PolymerModelBlockItem` falling back to `Items.BOOKSHELF`, into the creative tab after
the tables.
Vanilla's behaviour that is not in the block itself, each wired here:

| Vanilla wiring | Where | Planked equivalent |
|---|---|---|
| Enchanting power | `enchantment_power_provider` tag, read at `EnchantingTableBlock.java:48-49` | Generated tag entries, S4 |
| Flammable 30/20 | `FireBlock.java:412` | `FlammableBlockRegistry`, flammable woods only (`Flammability`, later) |
| Axe | `mineable/axe` | `AxeMineableProvider`, S4 |
| Lectern ingredient | `lectern.json` names `minecraft:bookshelf` | `c:bookshelves` item and block tags, S4; `lectern.json` override naming `#c:bookshelves` |

No oak bookshelf: vanilla's is oak, and the tinted oak frame reproduces it to within 3 per channel.
A uniform oak grid crafts vanilla's through S2.

**Done when.** A client running the mod places, sees and breaks each wood's bookshelf (untextured
until S4); a vanilla client without the pack sees `minecraft:bookshelf`; and fifteen planked
bookshelves around an enchanting table raise it to level-30 offers.

### As built

`PlankedBookshelfBlock` and `BookshelfBlocks`, which S5 extends to the chiseled shelf. Flammability
is `FlammableBlockRegistry` (30, 20), vanilla's own entry being by block. The enchanting-power tag
is datagen, so it arrived with S4's tags, and the enchanting check moved with it. Observed on the
dev server: every wood but oak places, vanilla and modded alike, and oak is an unknown block. From a
client running the mod the shelves look right, and fifteen of them give an enchanting table its
level-30 offers. A vanilla client without the pack sees vanilla's bookshelf in its place, and one
with the pack the wood's own (S4).

## S4 — Bookshelf datagen — **done**

**Build.**
- **Textures:** each wood's side is `colourise(bookshelf.png, median, 145)` cut to the frame and
  laid over vanilla's `bookshelf.png`, vendored into `sensiblestorage_vanilla/`, so the magenta
  fills with vanilla's books — the table's two-composite pattern. The end is the wood's planks,
  which shows on the inventory icon's top face, so it has to be in the pack. Rather than copying the
  planks a second time, move the table's `block/crafting_table/<wood>_bottom` to a shared
  `block/planks/<wood>` and point both models at it.
- **Models and state:** a `minecraft:block/cube_column` model per wood (`end`, `side`) through
  `PackJson.model`; blockstate and item definition as for tables.
- **Recipes:** per wood, `###/XXX/###` over the wood's planks and `minecraft:book`, and a
  `data/minecraft/recipe/bookshelf.json` override that is vanilla's file naming
  `PlankFallbackRecipe` (S2).
- **Loot, lang and tags:** loot is vanilla's own shape, silk touch for the block and otherwise
  three books, decaying in explosions (`VanillaBlockLoot.java:608`). Lang is "<Wood> Bookshelf".
  Tags are `mineable/axe`, `minecraft:enchantment_power_provider`, and `c:bookshelves` (item and
  block), all optional entries.

**Done when.** `generateAssets` is byte-identical to its baseline except the new files, the shared
tags and lang, and the table's bottom sprite moving path; on the dev server a uniform plank grid
with books crafts that wood's bookshelf, oak's crafts vanilla's, a mixed grid crafts vanilla's, and
a planked bookshelf crafts a lectern; a plain axe drops three books and a silk-touch one the shelf;
in-game, a client running the mod and a vanilla client with the pack both see the tinted frame with
vanilla's books.

### As built

The shared plank sprite is `PlankSpriteProvider`'s, written once per wood with a table or bookshelf;
the table's model points there and its `_bottom` sprites are gone. Tints go through `WoodTints`.
Block and item tags are `BookshelfTagsProvider` and `BookshelfItemTagsProvider`; `bookshelf.json`
and `lectern.json` are vanilla's own files with the serializer, or the ingredient, swapped. Against
S2's output: lang and `mineable/axe` changed, the 27 table models changed only their bottom sprite,
the 27 `_bottom` sprites went, and 219 files were added — per wood a side, planks sprite, model,
blockstate, item, recipe, advancement and loot table, plus the three tags. On the dev server:
birch, spruce and fir planks around books give their own shelves, oak's and birch with spruce give
vanilla's; a planked shelf makes a lectern, as vanilla's still does; a plain axe drops three books
and a silk-touch one the shelf; the axe, enchanting-power and `c:bookshelves` tags all hold; and
the pack carries 27 bookshelf states beside the tables' 27. In-game, a client running the mod and a
vanilla client with the pack both see each wood's tinted frame, and planked shelves power an
enchanting table.

## S5 — Chiseled bookshelf blocks, registration and disguise — **done**

**Build.** `PlankedChiseledBookshelfBlock`, extending `ChiseledBookShelfBlock` and implementing
`PolymerBlock` and `PolymerClientDecoded` (public constructor, `ChiseledBookShelfBlock.java:65`).
Two tiers: the real state for a client running the mod, and for everyone else
`Blocks.CHISELED_BOOKSHELF.withPropertiesOf(state)`, so facing and every occupied slot come across.
`BookshelfBlocks` registers one per wood but oak — vanilla's is oak, and S7 gives it the random
books — with `Properties.ofFullCopy(Blocks.CHISELED_BOOKSHELF)`,
adds each to `BlockEntityTypes.CHISELED_BOOKSHELF` through `FabricBlockEntityType#addValidBlock`
before any can be placed, and registers the item falling back to `Items.CHISELED_BOOKSHELF`, with
vanilla's `DataComponents.CONTAINER` default (`Items.java:400-401`). Vanilla's chiseled shelf is
not flammable and is not an enchanting power provider; neither is this. `mineable/axe` comes with
S6.

**Done when.** On the dev server a planked chiseled shelf takes and gives books by hand, hopper and
`item replace`, with `slot_<n>_occupied` following and a comparator reading the last slot touched; a
client running the mod sees the real block (untextured until S6); a vanilla client sees vanilla's
chiseled shelf with the same slots filled; and the hover label names the book under the crosshair.

### As built

As planned. The cast to `FabricBlockEntityType` compiles directly, `BlockEntityType` not being
final. On the dev server a planked shelf's block entity is vanilla's `minecraft:chiseled_bookshelf`;
`item replace` and a hopper above both fill slots, `slot_<n>_occupied` following; and a comparator
reads 3 after a book goes into slot 2, exactly as it does beside a vanilla shelf. In-game, from a
client running the mod, the shelf and its redstone behave, and a vanilla client, with the pack or
without, sees vanilla's shelf in its place with the same slots filled, and the hover label names
the book under the crosshair on a planked shelf as on vanilla's.

## S6 — Chiseled bookshelf datagen, vanilla's books — **done**

**Build.**
- **Textures, per wood:** `colourise` over the three gray overlays gives top, side and the empty
  front. An occupied background is the empty front with the shadow column applied — derived at
  datagen from vanilla's `chiseled_bookshelf_empty.png` and `_occupied.png`, both vendored, by
  mapping each of the 20 shadow pixels' overlay gray to the luma of vanilla's occupied colour there
  (30 → 19 and 76 → 60) before colourising, so no fourth overlay needs drawing. The 18 book sprites
  are copied through untinted to `block/chiseled_bookshelf/book/<n>`, once, not per wood.
- **Models:**
  - Per wood, a body (vanilla's `chiseled_bookshelf.json` shape: no north face), an inventory model
    (north face the empty front), six empty-slot quads and six occupied-background quads (vanilla's
    slot templates over the wood's sheets; the right-hand slots have no shadow and can reuse the
    empty ones).
  - Shared: six book templates, one per slot, each a single north-facing 4×6 quad at the book's
    position with `cullface: north` and a `#book` texture (Reference has the geometry), and 108
    children binding book *n* to slot *s*.
- **Blockstate:** a multipart per wood. Per facing (`y` 0/90/180/270, as vanilla), the body with
  `uvlock`, then per slot the empty quad when unoccupied, or the occupied background plus that
  slot's book when occupied. In this stage each slot's book is fixed to vanilla's own: book 2
  top-left, 1 top-mid, 5 top-right, 6 bottom-left, 3 bottom-mid, 4 bottom-right.
- **Recipes:** per wood, `###/XXX/###` over the wood's planks and the wood's own slab — the item
  for a vanilla wood, and for a modded one a one-entry optional tag `sensiblestorage:<wood>_slabs`,
  as the planks have, generated from `WoodType` rather than hand-written. A
  `chiseled_bookshelf.json` override is vanilla's file naming `PlankFallbackRecipe` (S2), so one
  wood's planks over another's slabs, or oak's over anything, make vanilla's shelf.
- **Loot, lang and tags:** loot is silk touch for the block only (`VanillaBlockLoot.java:493`), the
  contents spilling from the block entity as vanilla. Lang is "<Wood> Chiseled Bookshelf".
  `mineable/axe`, and a block tag `sensiblestorage:chiseled_bookshelves` holding vanilla's shelf
  and every planked one, each planked entry optional.
- **Advancement:** `data/minecraft/advancement/adventure/read_power_of_chiseled_bookshelf.json`,
  overriding vanilla's with the same parent, display and requirements, reads the tag instead of the
  block. Its `chiseled_bookshelf` criterion checks the placed block with `block_state_property`,
  which takes one block and no tag (`LootItemBlockStatePropertyCondition.java:17`), so that check
  becomes a `location_check` at offset 0 whose block predicate names the tag. The `comparator`
  criterion's four `location_check`s already take a block predicate, and name the tag in place of
  the block.

**Done when.** `generateAssets` is byte-identical to the S4 baseline except the new files and shared
tags and lang; on the dev server a wood's planks over its own slabs craft its chiseled shelf, while
its planks over another wood's slabs, oak's over oak's, and a mixed ring all craft vanilla's; and
in-game, a client running the mod sees a planked chiseled shelf whose six filled slots match a
vanilla chiseled shelf beside it book for book and pixel for pixel, shadow included, in all four
facings, and a comparator placed against a planked shelf grants "The Power of Books".

### As built

`ChiseledSlot` holds the slot geometry; `ChiseledBookshelfTextureProvider` and
`ChiseledBookshelfModelProvider` write the sheets, models and blockstates. The body and inventory
models are vanilla's with their textures overridden, and the slot quads are vanilla's slot templates
over the wood's sheets. The shadow is found at datagen as planned: 20 pixels, columns 5 and 10, rows
2–6 and 10–14, the wood's median at gray 19 and 60, checked on birch, dark oak and fir. The modded
slab tags are generated (`sensiblestorage:<wood>_slabs`); the advancement override was written from
vanilla's own file, its four comparator predicates and its placed-block check moved onto the tag.
Against S4's output: lang and `mineable/axe` changed and 770 files were added. An offline rasterizer
reading the generated blockstate and models through vanilla's parents confirmed the geometry: with
vanilla's sheets in place of the wood's, a full planked shelf reproduces vanilla's occupied sheet,
an empty one its empty sheet, and alternating slots each slot's own region, all to the pixel. On the
dev server: each wood's planks over its own slabs — vanilla, terrestria, traverse and bamboo — give
its chiseled shelf; another wood's slabs, oak's, mixed planks and mixed slabs give vanilla's; the
tags hold for planked and vanilla shelves; the loot is silk-touch only; and the server loads its
advancements with no errors.

In-game, from a client running the mod, the shelves looked right but the books z-fought the slot
behind them: each book was its own 4×6 quad, which depth-rounds differently from the slot quad it
shares a plane with. The books became 16×16 sheets drawn on vanilla's own slot templates instead,
the same quad as the background, which terrain's equal-passing depth test resolves in the later,
cut-out layer's favour (Findings). Against the build before: the 18 book textures and 108 book
models changed and the six custom book templates went; every book quad now has its background's
exact corners and UVs, and both geometry renders above still come out pixel-identical to vanilla.
The advancement fires for a planked shelf read by a comparator. A vanilla client with the pack draws
vanilla's shelf with these same book quads, and they render as expected there. Signed off in-game
with the rest of the feature on 2026-09-26.

## S7 — Random books — **done**

**Build.** Replace each slot's single book with a weighted list of all 18, the same list in every
facing and every wood, with slot *s*'s 18 weights summing to the *s*-th of six distinct primes near
1000 — 1009, 1013, 1019, 1021, 1031, 1033 — each weight 56 to 58, so every book is within 1.7% of
equally likely in every slot.

The same six lists also go into `assets/minecraft/blockstates/chiseled_bookshelf.json`, a
multipart replacing vanilla's own, which draws vanilla's shelf the way S6 draws a planked one: from
vanilla's four sheets vendored into `sensiblestorage_vanilla/` and written untinted under this mod's
namespace, with an occupied background built the same way, so vanilla's shelf keeps the shadow too.
The cost, accepted: a texture pack's chiseled bookshelf no longer applies for anyone with the mod or
the pack, since nothing in the override names vanilla's own sprites any more. A client running the
mod loads the file from its jar, and a pack client from the Polymer pack, since `addModAssets`
carries this mod's `minecraft` namespace too (its `assets/minecraft/lang/en_us.json` is in the pack
today); a multipart file in a higher pack replaces vanilla's outright for that block
(`BlockStateModelLoader.java:38-88`). So vanilla's shelf, and a planked one as disguised for a pack
client, get random books too; a client with neither sees vanilla's fixed six. Beyond the
blockstates, this stage adds only vanilla's shelf under this mod's namespace: its copied textures
and the body, inventory and slot models over them, as S6 writes for each wood.

Every per-slot list, in every blockstate this plan writes, is generated from one definition — the
same entries in the same order with the same weights — which is what makes every client agree on
the books (Reference: how a slot's book is chosen).

**Done when.** In-game, with a client running the mod: a wall of filled planked chiseled shelves
shows the books varying slot to slot and shelf to shelf, repeats within a shelf at about the rate
chance predicts (six distinct books in roughly 40% of full shelves); emptying a slot and refilling
it brings back the same book; filling or emptying one slot leaves every other slot's book as it
was; and a vanilla chiseled shelf shows random books to that client and to a vanilla client with
the pack, and vanilla's fixed six to a vanilla client without it.

### As built

The weights live in `ChiseledBooks`, in the mod's own code rather than datagen, so S8 can reach the
same definition at runtime. The vanilla override's textures and models sit under this mod's
namespace as a pseudo-wood named `vanilla`, and vanilla's item keeps its own definition. Against
S6's output: the 27 planked blockstates changed and 18 files were added — vanilla's four sheets, its
13 models and `minecraft:blockstates/chiseled_bookshelf.json`. All 28 blockstates carry identical
lists, totals 1009 to 1033. Rendered from its own generated textures with vanilla's book chosen per
slot, the override reproduces vanilla's occupied and empty sheets to the pixel, shadow included.
Minecraft's own arithmetic, reimplemented (`Mth.getSeed`, the 48-bit LCG, the per-part reset,
`nextInt` and the cumulative walk) over 4096 full shelves: six distinct books in 40.7% of shelves
(independent: 39.3%), slot 0 matching slot 1 in 5.5% (5.6%), every book between 4.7% and 6.5% in
every slot, no two neighbouring shelves alike, and at 256 positions every slot's book the same
whether the other five are full or empty. The dev server's pack carries the override, the 18 book
sprites and vanilla's copied sheets. In-game, a vanilla client with the pack sees random books,
placed consistently, on vanilla's shelf and on planked ones disguised as it, and a vanilla client
without the pack sees vanilla's fixed six. Signed off in-game with the rest of the feature on
2026-09-26.

## S8 — Pack tier: the frame by reserved state, the books by display entity — **done**

**Build.**
- **Model ids in main code.** The chiseled shelf's model ids — body, empty and occupied slots, the
  108 books, and the new per-wood book item definitions — move from `ChiseledBookshelfModelProvider`
  into a main-code `ChiseledBookshelfModels`, which datagen writes from and the runtime names, so
  the two cannot drift. Datagen output is unchanged by the move.
- **Frame.** `PlankedChiseledBookshelfBlock` swaps `PolymerBlock` for `PolymerTexturedBlock`,
  without which polymer-blocks remaps a reserved state back to a plain note block (see
  `planked-crafting-tables.md`). `BookshelfBlocks` requests four `FULL_BLOCK` states per wood, one
  per facing, each a `MultiPolymerBlockModel` of the parts S7's multipart draws for an empty shelf
  in that facing: the wood's body, `y`-rotated with `uvlock`, and its six empty slots, `y`-rotated.
  A wood any of whose four comes back null takes none, so no shelf's frame depends on which way it
  faces. `getPolymerBlockState` sends the real state to a client running the mod; the facing's
  reserved state to one with the pack, when the wood has them; vanilla's chiseled shelf with the
  same properties to everyone else.
- **Books.** The block implements `BlockWithElementHolder`, and for a wood with reserved states each
  placed shelf gets a `ChiseledBooksHolder` with one item display. It is watched only by clients
  that have the pack and do not run the mod (`ChestElementHolder`'s check, plus the pack). Its stack
  is `minecraft:book` with `item_model` `sensiblestorage:<wood>_chiseled_bookshelf_books` and
  `custom_model_data` strings, one per slot: the slot's book number when occupied, empty otherwise;
  a shelf with no books shows no stack. `ChiseledBooks#book(pos, slot)` gives the number. The
  holder works out the six books once, from the position, and restacks and reposes on
  `BLOCK_STATE_UPDATE`; it never ticks, and reads nothing from the level, being built during chunk
  load.
- **Pose.** The element sits one block out along the facing. Its left rotation is `Axis.YP` by
  `-facing.toYRot()`, which with the renderer's 180° turns the north-authored front to the facing,
  and its translation is one block back less 1/512, so the quads land 1/512 in front of the face.
- **Shadows.** Per wood, a `shadow` sheet: the occupied background's 20 shadow pixels, colourised
  as there, transparent elsewhere; and six shadow models, vanilla's slot templates over it. The
  right-hand slots' books cast no shadow, so their two draw nothing, kept so every slot is alike.
- **Item definitions.** Per wood, `assets/sensiblestorage/items/<wood>_chiseled_bookshelf_books.json`:
  a `composite` holding, per slot, a `select` on that slot's string whose one case, any of the 18
  numbers, draws the slot's shadow, then a `select` with one case per book drawing that book.
  Everything else is transparent, so terrain's frame shows through.

**Done when.** `generateAssets` is byte-identical to S7's output but for the 27 item definitions,
27 shadow sheets and 162 shadow models, and each wood's empty sheet under its shadow sheet is its
occupied sheet;
the dev server's pack has 108 more reserved `note_block` states; `ChiseledBooks#book`, run on
Minecraft's own classes, agrees with S7's reimplementation at every position sampled; and in-game a
vanilla client with the pack sees each wood's planked frame in all four facings, empty, partly
filled and full, with the books a client running the mod draws there. The check for the books:
fill a vanilla shelf, note its books, and set a planked one with the same state in its place — the
books stay. Taking a book out and putting it back leaves the others as they were; a vanilla client
without the pack still sees vanilla's shelf with vanilla's books; a client running the mod sees no
display over its shelves.

### As built

The first build drew each occupied slot's whole background on the display, which reviewing it
showed would light a filled slot's frame as an entity; it now draws books and shadows only, as the
Build says. `ChiseledBookshelfModels` holds the ids; `ChiseledSlot` takes its slot names from it,
and the provider's own name helpers delegate to it. The holder is `ChiseledBooksHolder`, in
`display/` beside the chest's. Polymer ticks a block-bound holder only when the block asks
(`BlockBoundAttachment`'s `autoTick`, from `tickElementHolder`), and an element sends its changed
data only when ticked, so the holder ticks itself once after each state change instead. A pack
client is also sent vanilla's chiseled bookshelf block entity over a note block, which is harmless:
Polymer drops only its own types from chunk packets (`ClientboundLevelChunkPacketDataMixin`), and
the client loads chunk block-entity data only into a block entity the block has already made
(`LevelChunk.java:541-545`), which a note block never does.

Against S7's output: only the 27 item definitions, 27 shadow sheets and 162 shadow models were
added. For all 27 woods, the empty sheet with the shadow sheet laid over it is the occupied sheet to
the pixel, so terrain's empty slot plus the display's shadow is terrain's occupied background.
`ChiseledBooks#book`, run on Minecraft's own classes from the mod's runtime classpath, matched the
Python reimplementation of the client's pick (`MultiPartModel`'s reset, `nextInt`, the weight walk,
over a full shelf's multipart) at 2,000 positions, eight near the origin and the rest across the
world border's range. On the dev server the pack's `note_block.json` has 108 multipart states, each
the wood's body and six empty slots in one facing, and none claimed by any of its variant keys,
which would win; the item definitions, shadow sheets and shadow models are in the pack. Placing
shelves by command and filling and emptying a slot with `item replace` runs the holder's
construction and update with no errors. A test courtyard at (20, -60, 20) has five woods per side,
facing inward in all four facings, with empty, partial and full shelves, and a full vanilla shelf at
(20, -58, 16) for the swap check. Signed off in-game with the books-only display on 2026-09-26,
which completes the feature.

## Reference: chiseled bookshelf slot geometry

From vanilla's `template_chiseled_bookshelf_slot_*.json` and `blockstates/chiseled_bookshelf.json`.
Model coordinates are for the north-facing model; a book at texture pixel `(u, v)` sits at model
`x` from `16 − (u + 4)` to `16 − u` and `y` from `16 − (v + 6)` to `16 − v`, on `z = 0`. The book is
not a quad of its own there: it is drawn on the slot's quad, from a 16×16 sheet holding it at those
texels, so the last column is where its pixels land rather than any element's bounds.

| Slot | Position | Slot quad from → to | Slot UV | Vanilla book | Book at (u, v) | Book texels, model space |
|---|---|---|---|---|---|---|
| 0 | top left | 10,8,0 → 16,16,0 | 0,0 → 6,8 | 2 | 1,1 | 11,9,0 → 15,15,0 |
| 1 | top mid | 5,8,0 → 10,16,0 | 6,0 → 11,8 | 1 | 6,1 | 6,9,0 → 10,15,0 |
| 2 | top right | 0,8,0 → 5,16,0 | 11,0 → 16,8 | 5 | 11,1 | 1,9,0 → 5,15,0 |
| 3 | bottom left | 10,0,0 → 16,8,0 | 0,8 → 6,16 | 6 | 1,9 | 11,1,0 → 15,7,0 |
| 4 | bottom mid | 5,0,0 → 10,8,0 | 6,8 → 11,16 | 3 | 6,9 | 6,1,0 → 10,7,0 |
| 5 | bottom right | 0,0,0 → 5,8,0 | 11,8 → 16,16 | 4 | 11,9 | 1,1,0 → 5,7,0 |

Books 2, 3, 10, 11 and 12 have a transparent top row, which shows the slot background behind.

## Reference: tint fit on oak

Mean absolute error per channel of each candidate against vanilla's oak texture, with the median
`(175, 143, 85)`:

| Overlay | `tint` (× gray/255) | HSL colourise | × gray/145 |
|---|---|---|---|
| `bookshelf.png` frame | 44.1 | 10.9 | 2.84 |
| `chiseled_bookshelf_top.png` | 59.6 | 13.8 | 2.36 |
| `chiseled_bookshelf_side.png` | 58.4 | 13.7 | 2.60 |
| `chiseled_bookshelf_empty.png` | 29.5 | 6.9 | 2.19 |

## Reference: median plank colours

Per-channel median of each vendored plank texture; "brightest" is the overlays' top gray (173) at
× 173/145.

| Wood | Median | Brightest |
|---|---|---|
| acacia | 173, 93, 50 | 206, 111, 60 |
| bamboo | 200, 177, 77 | 239, 211, 92 |
| birch | 200, 183, 122 | 239, 218, 146 |
| celestial | 204, 129, 57 | 243, 154, 68 |
| cherry | 231, 186, 180 | clips |
| crimson | 106, 52, 75 | 126, 62, 89 |
| cypress | 158, 147, 129 | 189, 175, 154 |
| dark_oak | 73, 47, 23 | 87, 56, 27 |
| fir | 140, 93, 60 | 167, 111, 72 |
| hemlock | 141, 122, 89 | 168, 146, 106 |
| japanese_maple | 151, 93, 91 | 180, 111, 109 |
| jungle | 170, 121, 84 | 203, 144, 100 |
| mangrove | 119, 57, 52 | 142, 68, 62 |
| murublight | 134, 132, 178 | 160, 157, 212 |
| oak | 175, 143, 85 | 209, 171, 101 |
| pale_oak | 231, 227, 225 | clips |
| pine | 218, 177, 129 | clips |
| rainbow_eucalyptus | 62, 83, 135 | 74, 99, 161 |
| redwood | 99, 52, 35 | 118, 62, 42 |
| rubber | 165, 149, 109 | 197, 178, 130 |
| sakura | 68, 41, 39 | 81, 49, 47 |
| scorched | 74, 69, 71 | 88, 82, 85 |
| spruce | 122, 90, 52 | 146, 107, 62 |
| umbral | 157, 172, 190 | 187, 205, 227 |
| veiled | 109, 78, 115 | 130, 93, 137 |
| warped | 46, 112, 103 | 55, 134, 123 |
| willow | 103, 83, 46 | 123, 99, 55 |
| yucca_palm | 225, 181, 101 | clips |

## Reference: how a slot's book is chosen

Nothing about the books crosses the network. The server sends a block state; each client picks the
books itself while it meshes the chunk, from a random number it derives from the block's position.

| Step | Where |
|---|---|
| The section compiler passes `blockState.getSeed(pos)` for each block | `SectionCompiler.java:107` |
| `getSeed` is `Mth.getSeed(pos)` unless a block overrides it; the chiseled shelf, the note block and this mod's blocks do not | `BlockBehaviour.java:283-285` |
| `Mth.getSeed` is a pure hash of x, y and z | `Mth.java:332-336` |
| The renderer seeds its random source with it before collecting parts | `ModelBlockRenderer.java:63` |
| A multipart draws one `nextLong()` and resets to it before every part | `MultiPartModel.java:52-56` |
| A weighted part draws `nextInt(totalWeight)` and walks the cumulative weights | `WeightedList.java:85-91` |

So two clients agree on a slot's book when three things hold: the same position, which they always
share; the same seed function for the block each believes is there, which the planked shelf, the
reserved note block state and vanilla's shelf all share; and the same weighted list for that slot,
entry for entry, which S7 guarantees by generating every list from one definition. Because the seed
is reset before every part, which other parts a state has — body, backgrounds, other slots — makes
no difference. A client that runs neither the mod nor the pack draws vanilla's own fixed six.

Every step above is common code, so the server can take them itself: seed a
`SingleThreadedRandomSource` with `Mth.getSeed(pos)` (the renderer's source is one,
`RandomSource.createThreadLocalInstance`, reseeded per block), draw one `nextLong()` and reseed with
it, then `nextInt` the slot's total and walk its weights. That is `ChiseledBooks#book`, which S8's
display entities show, so a pack client's books over a planked shelf are terrain's books.

## Reference: random-book weighting

200,000 uniform 31-bit samples through `nextInt(total)` and a cumulative-weight walk, six slots
reading the same sample, as `MultiPartModel` makes them:

| Per-slot weights | Mutual information, slot 0 vs 1 | P(slot 0 = slot 1) | P(six distinct) | Distinct shelves |
|---|---|---|---|---|
| Identical lists | 4.17 bits (identical) | 1.000 | 0.000 | 18 |
| Weight = slot + 1 | 3.17 bits | 0.056 | 0.493 | 1,080 |
| Totals 1009 … 1033 | 0.001 bits | 0.055 | 0.393 | 198,807 |
| Independent, for comparison | 0 | 0.056 | 0.393 | — |
