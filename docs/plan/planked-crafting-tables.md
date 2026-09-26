# Planked Crafting Tables

A crafting table per `WoodType` but oak, textured from that wood's planks the way this mod's
chests are — real block state for a handshake client, vanilla's own `minecraft:crafting_table` for
everyone else — with none of the chest's display-entity machinery, since vanilla's own block needs
none either. Every claim here is checked against `CraftingTableBlock`'s decompiled source and the
actual 26.2 client asset jar (`~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`); the three
`crafting_table_front/side/top.png` overlays in `sensiblestorage_overlay/block/` were checked pixel
by pixel against vanilla's own three face textures from that jar — see Findings. Stage order: S3
(art) is done, the overlays having been drawn before this plan was written. S1 (block and item
registration, disguised, undecorated) stands alone and is checkable without any recipe or texture;
S2 (datagen: textures, models, recipes) is what makes a table actually look and craft like its
wood, and is this feature's completion point.

## Findings that reshape the work

**The three overlay files are finished art, and their magenta means "vanilla's own pixel here",
not "hole".** `crafting_table_front.png`, `_side.png` and `_top.png`
(`sensiblestorage_overlay/block/`) are 16×16, matching a normal block face exactly (not the chest's
64×64 entity-unwrap sheet), and hold only fully transparent pixels and opaque `PngAssets.CUTOUT`
magenta. The magenta is the drawing: laid over vanilla's matching face texture, it covers exactly
the pixels that are not one of `oak_planks.png`'s seven colours — the dark frame, the central leg,
the tools, the worktop and its grid — and leaves every oak-plank pixel transparent. The side matches
that rule with no exceptions. The front departed from it in four pixels, the saw blade's white
(255, 255, 255) highlights at (11,7), (11,8), (12,10) and (13,12), left transparent by oversight and
since painted magenta. The top is magenta but for its 12 tan (188, 152, 98) corner pixels. So
`PngAssets.composite(planks, overlay)` alone, which erases magenta to full transparency, would punch
holes through a full cube; the magenta has to be filled from vanilla's own face texture instead,
which S2 has to vendor alongside the chest sheet it already vendors.

**Refusing every uniform grid left planks with no wood of their own unable to craft anything.**
`MixedPlankChestRecipe` matched only a grid of two or more distinct items, on the premise that
every uniform ring had a planked chest to go to. The live server's `#minecraft:planks` holds
`traverse:fir_planks`, which has no `WoodType` (checked against every jar in
`/mnt/WORKSPACE/minecraft-server-data/data/mods/`), so a uniform fir ring already made no chest at
all, and a `crafting_table.json` override built the same way would have stopped fir making a
crafting table. Dropping oak's table (see S1's As built) made the same hole for oak. So the
override instead refuses only a uniform grid of a plank in a `claimed` item set named by the
recipe JSON, which datagen fills with exactly the woods that have the block, and any other grid
still makes vanilla's.

**Crafting tables need none of the chest's display-entity machinery.** `PlankedChestBlock`
(`block/PlankedChestBlock.java:40-146`) hides behind `Blocks.BARRIER` for a non-handshake client and
draws itself with a Polymer display entity (`ChestElementHolder`) because a chest has state a
display entity can animate (a lid) and a shape (non-full-cube) that would otherwise make it a bogus
redstone conductor. `CraftingTableBlock` (decompiled) has neither: no block entity, no
`StateDefinition` properties (`blockstates/crafting_table.json` is a bare
`{"variants": {"": {"model": ...}}}`), and it is already a genuine full cube. So a handshake
client's real state can carry a real block model with real geometry directly — the same
`PolymerBlock` interface `PlankedChestBlock` implements, minus `BlockWithElementHolder`, minus the
barrier substitute, minus an `isRedstoneConductor` override.

**Vanilla's own recipe has the exact collision problem `MixedPlankChestRecipe` was written to
close, and that class looks generic enough to reuse rather than duplicate.**
`data/minecraft/recipe/crafting_table.json` (verified in the client jar) is a 2×2 `#minecraft:planks`
ring → `minecraft:crafting_table`, unconditioned on wood — the same shape
`data/minecraft/recipe/chest.json` had before `MixedPlankChestRecipe`
(`recipe/MixedPlankChestRecipe.java:32-45`) replaced it: a uniform ring of one wood's planks matches
both vanilla's recipe and this mod's wood-specific one, and the tie-break (`Identifier`'s natural
sort, path before namespace) hands most woods vanilla's plain result instead. `MixedPlankChestRecipe`
never touches chest-specific state — its codec carries a generic `ShapedRecipePattern` and
`ItemStackTemplate` result, and its only chest-specific surface is its name, Javadoc, and the
registered serializer id `sensiblestorage:mixed_plank_chest`. Renaming it to something shape-agnostic
(one serializer id, one class) and pointing both `chest.json` and a new `crafting_table.json`
override at it needs no new recipe class.

**`PackJson` has one of the two model helpers this needs, not both.**
`singleVariantBlockState(Identifier)` (`kalic0re` `common/datagen/PackJson.java:60-68`) is exactly
vanilla's `blockstates/crafting_table.json` shape and drops in unchanged. `model(Identifier parent,
String textureKey, Identifier sprite)` (`:30-37`) only binds one texture key, but vanilla's
`block/crafting_table.json` model needs six (`down`, `east`, `north`, `particle`, `south`, `up`,
`west` — verified in the client jar) over a `minecraft:block/cube` parent. A `cubeFaces`-style
helper belongs beside `model` in `PackJson`, on the same "shared code, one caller is still worth it"
logic the recent `PngAssets.rotate45` addition used, not hand-rolled per mod.

## How these stages get tested

Needs `./gradlew clean generateAssets` for the texture/model/recipe output (byte-comparison per
`docs/TESTING.md`) and the dev server for the rest: a wood's table has to be placed, opened and
crafted to confirm the disguise, the menu and the recipe actually work. S1 alone is checkable with
`/give`/`/setblock` and a handshake client before any texture or recipe exists. Recipes need no
client at all: a crafter placed over RCON with its grid filled by NBT and powered by a redstone
block resolves through the same `RecipeManager` the crafting menu does, and drops its result as an
item entity that `data get entity` can read.

## S1 — Block and item registration — **in progress**

**Build.** `PlankedCraftingTableBlock extends CraftingTableBlock implements PolymerBlock`, one per
`WoodType`, registered the way `ChestBlocks.init()` registers a chest: gated on
`wood.requiredModId()` under `ChestBlocks#DATAGEN`'s same reasoning,
`BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).setId(...)`. `getPolymerBlockState`
returns `state` for a handshake client (`SensibleStorage.HANDSHAKE.supportsAll(context)`) and
`Blocks.CRAFTING_TABLE.defaultBlockState()` otherwise — no barrier, no `ElementHolder` overrides, no
`isRedstoneConductor` override (already a full cube). Item registration mirrors
`ChestBlocks#registerItem`: `PolymerModelBlockItem` with `Items.CRAFTING_TABLE` as fallback. The
exact `Identifier` its `model` parameter expects (an item-model-definition id under `items/`, or a
raw model id under `models/`) needs confirming against Polymer's `PolymerItem#getPolymerItemModel`
contract before writing that line — `ChestBlocks#registerItem` passes `ChestModels.wholeModel(path)`,
itself an `items/`-facing product, but it names a display-entity model rather than a block model, so
it is not a clean template here.

**Done when.** A handshake client can place, see and right-click-open a menu titled "Crafting" on
every registered wood's table (art notwithstanding); a vanilla client sees a plain
`minecraft:crafting_table`.

### As built

`block/PlankedCraftingTableBlock.java` and `block/CraftingTableBlocks.java`, the latter called from
`SensibleStorage#onInitialize` straight after `ChestBlocks.init()`. Four departures from the Build
text:

- **The menu had to be replaced, which the plan missed.** `CraftingMenu#stillValid`
  (`CraftingMenu.java:104-106`) passes `Blocks.CRAFTING_TABLE` itself to
  `AbstractContainerMenu.stillValid` (`:94-96`), which checks `getBlockState(pos).is(block)`. No
  planked table is that block, so the inherited menu would close the tick after it opened.
  `getMenuProvider` now builds a private `CraftingMenu` subclass whose `stillValid` names the table
  that opened it; the title stays vanilla's `container.crafting` and the client is still sent
  `MenuType.CRAFTING`.
- **It also implements `PolymerClientDecoded`**, as `PlankedChestBlock` does. Polymer's client-side
  decoder hands back the real block state only for a block carrying it
  (`InternalClientRegistry.java:163-173`).
- **Registration walks `ChestBlocks.chests().keySet()`** rather than re-deriving the mod-presence
  and datagen gate, so a table exists for exactly the woods whose handshake keys are advertised.
- **The item's `model` is the item's own id**, `sensiblestorage:<wood>_crafting_table`.
  `PolymerItem#getPolymerItemModel`'s default returns the stack's `item_model` component
  (`PolymerItem.java:63-65`), which `setId` sets to the item id, so a pack-only client and the real
  item resolve the same `items/` definition. S2 writes it there.
- **No table for oak**, decided after the first build. Vanilla's own crafting table is already oak,
  so an oak one here only duplicated it; `CraftingTableBlocks.init` skips `WoodType.OAK`, and
  `tables()` is what every datagen provider walks, so oak falls out of those too. A uniform oak
  square makes vanilla's table instead (Findings).

Also added: the tables in the creative tab after the trapped chests. The menu title stays vanilla's
`container.crafting`, decided rather than left open.

Observed on the dev server, which has no client: boot with no mixin failures;
`setblock … sensiblestorage:spruce_crafting_table` places it and
`execute if block … #minecraft:mineable/axe` passes; `sensiblestorage:oak_crafting_table` is an
unknown block, and so is `sensiblestorage:redwood_crafting_table`, terrestria being absent, so the
gate holds. The Done-when itself — a handshake
client placing, seeing and opening a table with the menu staying open, and a vanilla client seeing
vanilla's table — still needs the dev client.

## S2 — Datagen: textures, block/item models, recipes — **done**

**Build.** A `CraftingTableTextureProvider`, structurally `ChestTextureProvider`'s twin minus the
4×4 tiling `ChestTextureProvider.tile()` does — a crafting table face is already 16×16, one-to-one
with a wood's plank texture and the three overlays. For `front`/`side`/`top`, the wood's planks cut
by the overlay (`PngAssets.composite(planks, overlay)`), then laid over vanilla's own matching face
texture (`PngAssets.composite(vanillaFace, cut)`) so the magenta the first pass erased is filled
from vanilla — its three faces vendored into `sensiblestorage_vanilla/` beside `chest.png`, for the
same reason that sheet is (edited after S3; see its As built). The raw plank texture for the
implicit `down` face, matching vanilla's own model. A block model per wood via the new `PackJson`
cube-faces helper (Findings), parented on `minecraft:block/cube`, six texture keys pointed at this
wood's generated sprites (`east`/`south` ← side, `north`/`west` ← front, `up` ← top, `down` ← raw
planks, `particle` ← front) — exactly vanilla's own key layout.
`PackJson.singleVariantBlockState` for the blockstate. An item definition per wood, reusing
whatever S1's `PolymerModelBlockItem.model` resolution turns out to need. Recipes:
`ResourceConditions.allModsLoaded`-gated per wood (`ChestRecipeProvider`'s loop is the template),
2×2 same-wood-planks → that wood's table, plus the renamed mixed-ring recipe (Findings) overriding
`data/minecraft/recipe/crafting_table.json` so a uniform ring doesn't lose the tie to vanilla's.

**Done when.** `./gradlew clean generateAssets` produces one texture set, block model, blockstate,
item model and recipe per `WoodType` with no manual step; a same-wood 2×2 ring crafts that wood's
table and a mixed ring still crafts vanilla's.

### As built

`CraftingTableTextureProvider` and `CraftingTableModelProvider` are new; the recipes went into
`ChestRecipeProvider`'s existing loop, the chest ring and the table square sharing one new
`withPlanks` helper for the vanilla-item-or-modded-tag key. Departures from the Build text:

- **Lang and loot, which the Build text left out.** A block needs a name and a drop:
  `ChestLanguageProvider` adds "<Wood> Crafting Table" and `ChestLootProvider` a plain `dropSelf`,
  vanilla's own table for its crafting table, conditioned like the chests.
- **`down` is the raw planks copied through**, as `block/crafting_table/<wood>_bottom`, rather than
  a reference to `<mod>:block/<wood>_planks`. A pack-only client can see a dropped table's bottom
  and need not have the wood mod's textures.
- **The `PackJson` helper is a general `model(Identifier, Map<String, Identifier>)` overload**
  rather than a cube-specific one; the single-key `model` now delegates to it. Released as
  kalic0re 0.17.0 to `mavenLocal()`, and `kalic0re_version` bumped to match.
- **The recipe was renamed, then changed, rather than only renamed.** `MixedPlankChestRecipe` is
  now `PlankFallbackRecipe`, serializer `sensiblestorage:plank_fallback` instead of
  `sensiblestorage:mixed_plank_chest`; the old id appeared nowhere but this mod's `chest.json` —
  checked against `/mnt/WORKSPACE/minecraft-server-data` too. It also takes a `claimed` item set
  and refuses only a uniform grid of one of those (Findings). `ClaimedPlanksProvider` writes the
  two sets, `sensiblestorage:planks_with_chest` (all 27 woods) and
  `sensiblestorage:planks_with_crafting_table` (all but oak), each entry optional, and
  `chest.json` names the first. The new `data/minecraft/recipe/crafting_table.json` is vanilla's
  own file with the serializer swapped and the second set added, `show_notification: false` kept.
- **`minecraft:mineable/axe` is generated now**, by `AxeMineableProvider`, instead of hand-written.
  The hand-written file listed the 12 vanilla woods' chests only; it had never learnt the 15 modded
  ones, which `ChestCartCargoProvider`'s Javadoc had already flagged as drift. The generated tag
  lists every wood's chest and trapped chest and every table, each optional.

Tested against a baseline generated from `HEAD` in a throwaway worktree, both runs from an empty
`src/main/generated`: all 1669 existing files byte-identical except `lang/en_us.json`, which gains
26 keys and changes none; 263 new files, ten per table wood plus the three tags. Composited
through oak's own planks, as a check, the three faces equal vanilla's but for the top's 12 corner
pixels; every output pixel is opaque. Through a crafter on the dev server: uniform birch and dark
oak 2×2 grids each give that wood's table, and uniform oak gives `minecraft:crafting_table`, as
does oak with birch; uniform oak and spruce rings still give their own chests, and oak with spruce
`minecraft:chest`. `loot spawn … mine` on a table drops one of it (tested on oak's, before oak's
was dropped). How a table looks in-world needs a handshake client, and is S1's to observe rather
than this stage's.

## S3 — Overlay art — **done**

**Build.** Replace the three placeholder overlays' all-magenta/transparent content with real art:
`crafting_table_top.png` needs a grid pattern, `crafting_table_front.png` a tool icon,
`crafting_table_side.png` whatever edge detail distinguishes a side from a bare plank face.
Vanilla's own three files (same client jar checked for Findings) are the reference for what needs
conveying, not something to trace pixel-for-pixel.

**Done when.** A composited table reads as a crafting table at a glance, not a plank cube, for every
wood.

### As built

Nothing to build: the art was finished before this plan was written, and the Findings entry that
first called it empty had misread its magenta as `PngAssets.CUTOUT`'s usual "nothing here".
Rather than drawing the grid and tools, the overlays keep vanilla's own and swap only its plank —
magenta marks every pixel to take from vanilla's face texture, transparency every pixel to take
from the wood (Findings has the pixel-level check). The plan's assumption that the overlay carries
the art itself was wrong, and that changes how S2 composites: its Build text now layers the cut
planks over vanilla's face rather than stopping at the cut. Four white saw-blade pixels on the
front had been missed, so took plank; they were painted magenta afterwards, and the front now
follows the rule exactly.
