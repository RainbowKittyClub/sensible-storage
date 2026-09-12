# Planked Chests

A chest and a trapped chest for every wood type, crafted from that wood and textured to match,
built server-side with the visible chest rendered as a Polymer display entity.

Claims here were verified against the 26.2 deobf jar (`javap`, `libs/decompiledjava/minecraft-26.2/`)
and the `libs/polymer` checkout (`dev/26.2`). The per-face UV numbers in S4 and the
entity-vs-block-model Y-axis flip are stated from source but not yet observed in world — treat them
as unverified until S5 shows a correctly-textured chest.

Stage order: S1 (skeleton) → S2 (real blocks/BEs/items/recipes; full server-side gameplay, clients
see an invisible solid block) → S3 (texture datagen) → S4 (model + item-definition datagen) → S5
(Polymer display entity; feature becomes visually complete) → S6 (lang, creative tab, tags, lint).
S3 and S4 are independent of each other but both precede S5. **S5 is what makes the feature
complete.**

## Findings that reshape the work

**Placed-chest rendering is not data-driven, so the chest has to be a display entity.**
`ChestRenderer.getChestMaterial` (`net/minecraft/client/renderer/blockentity/ChestRenderer.java`)
selects the texture from a hardcoded `ChestRenderState.ChestMaterialType` switch on the block / block
-entity class. A new `ChestBlock` subclass renders with `normal.png` and nothing changes that
without a client mod. So the real block is made client-invisible (Polymer block state = barrier) and
a Polymer virtual display entity carries the look, bound to the block via `BlockWithElementHolder`.

**The supplied overlays are the vanilla chest entity unwrap.** `chest_overlay.png` /
`chest_overlay_trapped.png` are 64×64 RGBA — the layout of `ChestModel.createSingleBodyLayer`
(`LayerDefinition.create(mesh, 64, 64)`). The generated texture is each wood's 16×16 plank texture
tiled 4×4 to 64×64 with the overlay composited on top, no resize.

**Two block-entity types, not 24.** `BlockEntityType` carries a `Set<Block>` of valid blocks, so one
`plankedchests:chest` type serves all 12 chest blocks and one `plankedchests:trapped_chest` type all
12 trapped blocks.

## How these stages get tested

Dev server via `scripts/devserver.sh rkc/mods/planked-chests <cmd>` (start / restart / rcon / logs),
watched in world by the user on their client — brief them per leg before each test round. No test
suite. A stage is done only when its "Done when" is observed in world or in a file listing; a
compile is not an exit criterion, so a failure at stage N implicates stage N, not S1–S(N-1).

`./gradlew build` needs the sandbox bypassed (`~/.gradle` writes). Datagen output
(`src/main/generated/`) is gitignored and required for the jar; `./gradlew generateAssets`
regenerates it, and from S4 on a `jar` guard fails with a pointed message when it is missing
(modelled on `rkc/mods/companions/build.gradle`).

## S1 — Skeleton copy and project setup — **done**


**Build.** Copy `fabric_mod_skeleton/` into this directory, keeping the two overlay PNGs. Rename
every `CHANGEME`/`changeme` per the skeleton README: `gradle.properties`, `settings.gradle`, the
entrypoint class to `PlankedChests`, the datagen entrypoint to `PlankedChestsDataGenerator`, the
mixins json filename and package. Own dev-server ports in `gradle.properties`. Strip the Polymer
client-optional example (`content/`, `polymer/`, `example_block` assets). Drop bil: remove the
`bil_*`/`fabric_permissions_api` properties and dependencies, the Modrinth/Sponge repos and
`mavenLocal()`, and the `bil` depend; keep `polymer-bundled`. `fabric.mod.json` depends on
`polymer-core` / `polymer-virtual-entity` / `polymer-resource-pack` (like `critters-mod`). Keep the
`configureDataGeneration { client = true }` block. Write this doc and the mod README.

**Done when.** `./gradlew build` succeeds on the empty mod, jar at
`build/libs/plankedchests-0.1.0.jar`; `./gradlew runServer` boots to `For help, type`.

### As built

Went as planned. Quilt wiring (`quilt_*` properties, `runQuiltServer`) was left in place rather than
stripped — it is generic in `gradle/devserver.gradle` and carries no build cost. The placeholder
`assets/plankedchests/lang/en_us.json` was deleted, not kept empty, so the S6 datagen lang provider
owns that path without a jar-internal collision. The empty `plankedchests.mixins.json` and its
`fabric.mod.json` entry were removed — the mod has no mixins. Datagen runs `client = false`: no
provider touches a client-only registry. Dev-server ports: game `25604`, rcon `25581`.
`./gradlew build` green; server boots, logs `(plankedchests) Planked Chests loaded` and
`For help, type`.

## S2 — Real blocks, block entities, items, recipes — **done**

**Build.** Feature-first packages under `club.rainbowkitty.plankedchests`.

- `wood/WoodType` — the 12 woods (`oak … warped`), each with its plank item id
  (`minecraft:<wood>_planks`) and display name (`dark_oak` → "Dark Oak"). One source of truth.
- `block/PlankedChestBlock extends ChestBlock` — protected ctor
  `(Supplier<BlockEntityType<? extends ChestBlockEntity>>, SoundEvent, SoundEvent,
  BlockBehaviour.Properties)`. Implements `PolymerBlock` (state → `Blocks.BARRIER`,
  `forceLightInsideBlock` → true) and `BlockWithElementHolder` (returns `null` until S5).
- `block/PlankedTrappedChestBlock extends PlankedChestBlock` — copy `TrappedChestBlock`'s four
  protected overrides (`isSignalSource`, `getSignal`, `getDirectSignal`, `getOpenChestStat`); it
  cannot be subclassed because its `(Properties)` ctor fixes the BE type.
- `block/PlankedChestBlockEntity` / `PlankedTrappedChestBlockEntity` — pass the mod's BE type,
  override `getDefaultName()` to `block.plankedchests.<wood>_chest` (wood from the block state) so
  the menu title reads "Birch Chest". Confirm `TrappedChestBlockEntity`'s ctor visibility during
  build; if unusable, subclass `ChestBlockEntity` and copy its `signalOpenCount` override.
- Entrypoint loop over `WoodType`: 24 blocks (`Properties.ofFullCopy(Blocks.CHEST/TRAPPED_CHEST)`);
  2 `BlockEntityType`s each with its 12 blocks, each passed to
  `PolymerBlockUtils.registerBlockEntity`; 24 `BlockItem`s via a local
  `PolymerModelBlockItem extends BlockItem implements PolymerItem` (mirror `rkcore`'s
  `common/item/PolymerModelItem`; fallback `minecraft:chest` / `minecraft:trapped_chest`, model id
  `plankedchests:<wood>_chest`); one `PolymerBlockUtils.SERVER_SIDE_MINING_CHECK` listener returning
  true for our states so a barrier (hardness −1) is still mineable.
- Recipes: hand-written datapack JSON in `src/main/resources/data/plankedchests/recipe/` — per wood
  a shaped ring of 8 `<wood>_planks` and a shapeless `<wood>_chest` + `minecraft:tripwire_hook`.
- Loot tables in `src/main/resources/data/plankedchests/loot_table/blocks/` — drop-self.

**Done when.** On the dev server: `/give` a birch chest, place it — invisible but solid; right-click
opens a 27-slot menu titled **Birch Chest**; two adjacent give a 54-slot menu; a hopper beneath
pulls items; a placed birch trapped chest powers an adjacent lamp while open; 8 birch planks craft
one birch chest; breaking in survival drops the item. (Still a barrier visually until S5.)

### As built

`PlankedTrappedChestBlock extends PlankedChestBlock` (both take the BE-type supplier), reproducing
`TrappedChestBlock`'s overrides — the vanilla method is `ownSignal(state, level, pos)`, three args,
not `getSignal`. Block-entities extend `PlankedChestBlockEntity` (trapped extends that too, for the
`getDefaultName` reuse) rather than `TrappedChestBlockEntity`, which fixes the vanilla type.
`SERVER_SIDE_MINING_CHECK` turned out unnecessary — `PolymerBlock.handleMiningOnServer` defaults
true, so a barrier client state is already server-mined. In-world: container, double chest, hoppers,
trapped redstone and crafting all confirmed by the user; survival drop confirmed once the dev
world's `block_drops` rule was turned back on.

Recipes and loot were later moved from hand-written `src/main/resources` files to datagen, and
item registration switched to `rkcore`'s shaded `ItemRegistration` + `PolymerModelBlockItem` (the
local `PolymerModelBlockItem` was deleted) — see `docs/plan/shared-datagen-helpers.md`.

Menu titles (2026-09-08, not yet deployed): `getDefaultName()` now always uses the plain
`block.plankedchests.<wood>_chest` key, so an open **trapped** chest reads "Birch Chest" like the
plain one rather than announcing itself. `PlankedChestBlock.getMenuProvider` wraps vanilla's result
to swap the double-chest fallback `container.chestDouble` ("Large Chest") for
`container.plankedchests.large_chest` = "Large %s Chest" → "Large Birch Chest" (a player-renamed
half still wins; single chests and the locked-chest notification are untouched).

## S3 — Texture datagen — **done**

**Build.** `src/datagen/java/.../datagen/ChestTextureProvider implements DataProvider`, modelled on
`companions`' `SweaterTextureProvider` (same `CachedOutput` + `HashingOutputStream` PNG write,
`output.createPathProvider(RESOURCE_PACK, "textures/block")`). Per wood × {chest, trapped}: tile the
16×16 plank PNG 4×4 into a 64×64 `TYPE_INT_ARGB` image, alpha-composite the matching overlay from
`assets/plankedchests/textures/overlay/`, write to
`src/main/generated/assets/plankedchests/textures/block/<wood>_chest.png` /
`<wood>_trapped_chest.png` (the `block/` folder auto-joins the vanilla blocks atlas — its directory
source scans every namespace).

Plank source images: read from the Minecraft resource manager during the client datagen run
(`Minecraft.getInstance().getResourceManager().getResourceOrThrow(...)`). The vanilla plank PNGs are
client assets (asset-index objects, not in the jar), so a classpath lookup will not find them. If
the resource manager is not populated at provider-run time, fall back to vendoring the 12 PNGs into
`src/datagen/resources/plank_source/` and reading them off the classpath.

**Done when.** `./gradlew generateAssets` writes 24 PNGs under
`src/main/generated/assets/plankedchests/textures/block/`; `birch_chest.png` shows birch grain tiled
with the chest overlay, 64×64, transparency preserved.

### As built

Plank sources are vendored into `src/datagen/resources/plankedchests_planks/` (the resource-manager
route was not attempted — vendoring is what `companions` does and it is reliable). Provider
`ChestTextureProvider`, one `Graphics2D` tile-then-`drawImage` composite, 24 files written and
verified 64×64 RGBA with plank grain showing through the overlay's transparent regions. The overlay
art carries two ~10px solid-black squares over the bottom-box top face and lid underside — faces the
chest model never shows — so they are harmless and left as authored.

Output path changed from the plan's `textures/block/` to **`textures/entity/chest/`**, sprite names
`<wood>` / `<wood>_trapped` (no `_chest` suffix). S4 switched to vanilla's `minecraft:chest` special
model, which resolves its texture through the `minecraft:chests` atlas (`entity/chest/` directory
source, all namespaces) — so no hand UV work, and the block-atlas placement the plan assumed is
moot.

The classpath-read and hash-then-write PNG plumbing was later extracted to `rkcore`'s
`common/datagen/PngAssets` (see `docs/plan/shared-datagen-helpers.md`); `ChestTextureProvider`
keeps only the tile/composite step.

## S4 — Model and item-definition datagen — **done**

**Build.** `src/datagen/java/.../datagen/ChestModelProvider`. Generate into
`src/main/generated/assets/plankedchests/`:

- `models/block/<wood>_chest_base.json`, `_lid.json`, `_whole.json` and the four trapped
  equivalents — hand-built `elements` models. Vanilla item/block models ignore `texture_size`, so
  pre-divide every face `uv` to 0–16 space (×16/64 = 0.25).
- Geometry from `ChestModel.createSingleBodyLayer`: bottom (1,0,1) 14×10×14 texOffs (0,19); lid
  (1,0,0) 14×5×14 texOffs (0,0) pivot (0,9,1); lock (7,−2,14) 2×4×1 texOffs (0,0) pivot (0,9,1);
  lid rotation `-(open · π/2)` about X.
- Per-face UV from the vanilla box unwrap (`ModelPart$Cube` ctor, `ModelPart.java:243`): texOffs
  (u,v), size w×h×d → `u0=u, u1=u+d, u2=u+d+w, u22=u+2w+d, u3=u+2d+w, u4=u+2d+2w`; `v0=v, v1=v+d,
  v2=v+d+h`. DOWN `(u1,v0,u2,v1)`, UP `(u2,v1,u22,v0)`, WEST `(u0,v1,u1,v2)`, NORTH `(u1,v1,u2,v2)`,
  EAST `(u2,v1,u3,v2)`, SOUTH `(u3,v1,u4,v2)`. Trap: entity models are +Y-down, block models
  +Y-up — flip Y on `from`/`to` and the lid rotation `origin`. Verify in S5.
- `items/<wood>_chest.json` / `<wood>_trapped_chest.json` — item definitions pointing at
  `plankedchests:block/<wood>_chest_whole`.

Entrypoint: `PolymerResourcePackUtils.addModAssets("plankedchests")` and `markAsRequired()` (like
`critters-mod`). Add the `jar` datagen-output guard to `build.gradle`.

**Done when.** `generateAssets` produces the model + item JSONs; a `json.tool` sweep over
`src/main/generated` passes; `./gradlew build` bundles them.

### As built

Dropped the hand-authored `elements` models and the box-UV unwrap entirely. Each item definition is
a `minecraft:special` model of type `minecraft:chest` (`base` `minecraft:item/chest` or
`minecraft:item/trapped_chest`), which reuses vanilla's `ChestModel` geometry and unwrap and reads
the texture from the `minecraft:chests` atlas — so the plan's S4 UV maths and the entity-vs-block
Y-flip never came up. `ChestSpecialRenderer.Unbaked` bakes `openness`, so lid animation is stepped:
`ChestModelProvider.OPENNESS_STEPS = 5` definitions per chest (`<id>`, `<id>_open1..4`), the display
entity swaps between them (S5). 120 item defs + 24 textures. `PolymerResourcePackUtils.addModAssets`
+ `markAsRequired` moved into the entrypoint; `jar` guard added to `build.gradle`.

## S5 — Polymer display entity — **done**

**Build.** `display/ChestElementHolder extends ElementHolder`, returned by
`PlankedChestBlock.createElementHolder` (`tickElementHolder` → true), bound by Polymer via
`BlockBoundAttachment`.

- One `ItemDisplayElement` holding an `Items.CHEST` stack whose `minecraft:item_model` component
  points at the model for the bound block's `ChestType` and lid openness step
  (`ChestModels.itemModel`). Lid animation is the item swap between the 5 baked-openness models,
  driven each tick from `ChestBlockEntity#getOpenNess`.
- `ChestType` (SINGLE / LEFT / RIGHT) is read from the bound state; the LEFT/RIGHT models carry the
  vanilla half-chest geometry via `minecraft:special` `chest_type`, textured from the matching
  `_left` / `_right` overlays (supplied later, see the As built notes).
- Facing: left-rotation quaternion `Axis.YP.rotationDegrees(YAW_OFFSET_DEG - facing.toYRot())`,
  read from `BlockBoundAttachment#getBlockState` in `onTick` (not the level — see the deadlock note).
- **Deadlock trap (fixed):** the holder is constructed from inside `LevelChunk`'s constructor
  during chunk load (Polymer's `polymerBlocksInit` mixin); calling `level.getBlockState` /
  `getBlockEntity` there blocks on the chunk that is still loading and hangs the server thread at
  "Preparing spawn area". The constructor takes `initialBlockState` as a parameter and every level
  read is deferred to `onTick`.
- Client block state: `Blocks.BARRIER` — invisible, full collision, server-side mined. A
  right-click with a block in hand briefly predicts a placement against it before the server
  corrects; `polymer-blocks`' interaction-consuming states (note block / target / slab) all
  proved broken on 26.2 (see open decisions), so the flicker is accepted.
- Lid openness is tweened server-side from `ChestBlockEntity.getOpenCount` (the lid controller is
  client-only render state, so `getOpenNess` reads 0 server-side); `openStep` applies vanilla's
  ease-out curve and quantises to one of `OPENNESS_STEPS` (20) baked models, ramping at 0.1/tick.
- The display sits at `ItemDisplayContext.HEAD` with the display entity's identity scale and
  translation; it is rotated to `-facing.toYRot()` to match `ChestRenderer`.

**Done when.** In world: placing a birch chest shows a birch-textured chest facing the way the
block faces; right-click opens the lid and it closes with the menu; breaking removes the model and
drops the item; a spruce trapped chest looks spruce and distinct; a double chest is one continuous
long chest.

### As built

Went through several client-state and animation revisions against the live server (see open
decisions for the reasoning left standing):

- Client state settled on `Blocks.BARRIER`. `polymer-blocks` note-block / target / slab states
  were each tried and each rendered visibly on 26.2 — its `requestBlock`/`requestEmpty` blockstate
  generation is broken for `variants`-type borrowed blocks. The barrier's one-frame ghost-placement
  flicker is the accepted cost.
- Facing offset is 0 (`-facing.toYRot()`, matching `ChestRenderer`); the initial 180 guess was
  double-counting the special model's own transform.
- The item-model component had to be forwarded by a local `PolymerModelBlockItem` that overrides
  `getPolymerItemModel` directly — Polymer's built-in `PolymerBlockItem` model path did not carry
  it through for the GUI item.
- Every chest item model uses a mod-owned base model `plankedchests:item/chest_display` (parent
  `minecraft:item/template_chest`). With `minecraft:item/chest` as the shared base, the client
  leaked this mod's last-loaded chest texture onto the vanilla chest item.
- Held-item render and an intermittent "single chest doesn't animate" both turned out to be
  Polymer client flakiness that cleared on a resource reload, not code bugs.
- Lid animation is a 20-step model swap with vanilla's ease-out curve, tweened server-side from
  `getOpenCount` because the server never runs a chest's lid controller. Not truly interpolated.
- Double chests: `ChestType` is read from the bound state and the matching special-model
  `chest_type` half (left/right, textured from the supplied `_left`/`_right` overlays) is shown.
- A chunk-load deadlock (holder constructed inside `LevelChunk`'s constructor) was fixed by taking
  `initialState` as a constructor parameter and deferring all level reads to `onTick`.
- The chest properties set `isRedstoneConductor` to a constant `false`. Vanilla derives it from the
  collision shape (never a full cube for a chest), but Polymer's `BlockBehaviourMixin` returns the
  barrier client-state's full-cube shape for non-player collision queries — including the one that
  builds the `isCollisionShapeFullBlock` cache — so the default predicate made a planked chest a
  redstone conductor and any chest directly below one refused to open (`isBlockedChestByBlock`).
  This is a *class* of problem: any other property vanilla derives from a full collision cube
  (suffocation, mob spawning on top, `isCollisionShapeFullBlock` callers) will read wrong for the
  same reason and needs the same kind of explicit `Properties` override. Polymer offers no
  per-block opt-out for the collision substitution.

## S6 — Lang, creative tab, tags, lint — **done**

**Build.** Datagen `en_us.json` (`block.plankedchests.<wood>_chest` = "{Wood} Chest",
`…_trapped_chest` = "{Wood} Trapped Chest"). Append the 24 items to the vanilla functional/redstone
creative tabs via `ItemGroupEvents`, next to the vanilla chests. `mineable/axe` block tag for all
24. Wire `src/main/java` into `rkc/lint/build.gradle`'s `mods` map.

**Done when.** `cd rkc/lint && ./gradlew lintAll` passes for the new mod; the creative inventory
shows 24 chests named "Dark Oak Chest" / "Warped Trapped Chest" etc.; `./gradlew build` green.

### As built

Lang via `ChestLanguageProvider` (datagen `FabricLanguageProvider`). `mineable/axe` tag is a
hand-written datapack file — and it must live at **`data/minecraft/tags/block/mineable/axe.json`**
(the `minecraft` namespace, `replace:false`) to merge into the vanilla tag; a first try under
`data/plankedchests/tags/` created a dead tag and axe mining was no faster than by hand.

Creative entries: a **dedicated `plankedchests:chests` Polymer tab** via
`PolymerCreativeModeTabUtils.builder()` / `registerPolymerCreativeModeTab`. Adding entries to the
vanilla `functional_blocks` / `redstone_blocks` tabs (`CreativeModeTabEvents.modifyOutputEvent`, the
`minecart-tweaks` approach) did not sync to a Polymer client at all — Polymer reliably syncs only
tabs it owns. The dedicated tab shows in the creative menu for `polymer-core` clients and in
`/polymer creative`; a plain client still sees nothing there and crafts the chests.

**Recipe conflict.** The vanilla `minecraft:chest` recipe (`#minecraft:planks` ring) matches "8
matching planks" and, sorting before `plankedchests:...`, wins — so every wood's craft produced a
vanilla chest. Fixed by overriding `data/minecraft/recipe/chest.json` and `trapped_chest.json` with
an unsatisfiable `minecraft:barrier` shapeless recipe (user approved removing the vanilla recipes).
Per-wood recipes are now the only match for a uniform plank ring. Those two overrides stay
hand-written under `src/main/resources/data/minecraft/` — the per-wood recipes themselves are
datagen (see `docs/plan/shared-datagen-helpers.md`), but there is no clean builder for an
intentionally unsatisfiable recipe.

`planked-chests` added to `rkc/lint/build.gradle`; `checkstyleTarget` on it is clean. `lintAll`
still fails on pre-existing import-order debt in `rk-tweaks` and `critters-mod` — untouched here.

## Deployed

`plankedchests-0.1.0.jar` deployed to the live server on 2026-09-08. Built against the live
server's versions (fabric-api `0.159.0+26.2`, polymer `0.17.5+26.2` — bumped from the `0.17.3`
originally pinned). Boot verified: mod + datapack loaded, 0 mixin errors, block entity works,
Polymer pack regenerated with all 1514 chest assets. The `Pack declares support for version newer
than 64` warning on boot is pre-existing (a stale `resources.zip` with an old `pack.mcmeta`, ~116
prior occurrences) and unrelated. Not committed to the `minecraft-server-data` repo.

Redeployed 2026-09-08 with the `isRedstoneConductor` fix (see S5 "As built"): a chest under a
planked chest would not open. Same filename, straight swap; boot re-verified (mod + datapack
loaded, 0 mixin errors).

## Shared datagen helpers

The `rkcore`-shared PNG/item helpers and the recipe/loot datagen move (2026-09-08, not deployed)
are recorded in `docs/plan/shared-datagen-helpers.md` — it spans three checkouts, so it lives on
its own.

## Client-side rendering

`docs/plan/client-mod-support.md` — a client that installs the same jar (plus Polymer client-side)
now renders the real chest: exact hitbox, real `ChestBlockEntity`, vanilla-smooth lid, via a Fabric
`BlockEntityRenderer` (`rkcore` `common/client/PolymerChestRenderer`). Decided per connection from a
Polymer handshake (`rkcore` `common/net/PolymerClientHandshake`); `PlankedChestBlock` implements
`PolymerClientDecoded` and `getPolymerBlockState` returns the real state for such clients, which also
skip the display entity. Vanilla clients are unchanged. `markAsRequired()` was dropped.

## Chest minecarts

`docs/plan/chest-minecarts.md` — a chest minecart per wood, on a general "minecart carrying an
arbitrary block" mechanism added to `rkcore` as `common/vehicle` and relocated in like the other
shared packages. Server-side gameplay is built; the per-wood appearance is not. All 27 ride on one
item id with the cargo in a data component, and none of them goes in a creative tab — vanilla's
"Minecart with Chest" stays the only chest cart anyone can browse for, and the planked ones are
crafted, given or pick-blocked. That document also records why chest boats were assessed alongside
it and deferred.

## Open decisions

The *Client selection box*, *Lid animation*, and *Right-click ghost-placement flicker* items below
apply to **vanilla / Polymer-only clients**. A client that installs this jar plus Polymer renders
the real block (`docs/plan/client-mod-support.md`) and hits none of them — the barrier and display
entity are not used for it.

- **Held-item render.** The custom chest model renders correctly in the GUI and on a vanilla
  client, but a client render mod (not Fresh Animations / EMF — still unidentified) draws the
  vanilla chest for the *held* item. Not this mod's bug; left for the client-side mod to fix.
- **Creative menu.** The chests are in a dedicated `plankedchests:chests` Polymer tab — visible to
  `polymer-core` clients and in `/polymer creative`, but a plain client never sees server-only
  creative items regardless. Players craft the chests.
- **Dev-world `block_drops` default.** The skeleton's `zz_dev_defaults` datapack turns `block_drops`
  off, which hid that survival breaking works. Reconsider that default in
  `fabric_mod_skeleton/gradle/devserver.gradle` (the user flagged it).
- **Lid animation.** 20 baked-openness models swapped with vanilla's ease-out curve — reads as
  motion but is not truly interpolated (an item swap can't interpolate). Genuine interpolation
  needs the lid as its own display element with a rotating transform, which needs hand-built
  base/lid geometry and a blocks-atlas texture path (the reason S4 chose the special model).
- **Right-click ghost-placement flicker.** Barrier client state: a block-in-hand right-click
  predicts a placement for one frame before the server corrects — whether the block is placed
  against an existing chest or is a chest itself, since the client predicts from the item in hand
  and cannot know the server will answer with a barrier plus a display entity. Observed again on a
  vanilla client 2026-09-12; ping-bound, so it cannot be shortened from the server side. `polymer-blocks` states that
  would consume the interaction were all unusable on 26.2 — the plain `PolymerBlockModel` form
  blanks every unclaimed variant of the borrowed block, and the `MultiPolymerBlockModel` form
  emits a blockstate file with both `variants` and `multipart`, which the client resolves to the
  borrowed block's real model (note block stayed visible in world). Revisit when polymer-blocks
  fixes variant-block generation, or with a client mixin.
- **Client selection box.** Barrier gives a full-cube outline on hover, not a chest silhouette.
  Acceptable.

## Reference: verified APIs and numbers

- `ChestBlock` ctor `(Supplier<BlockEntityType<? extends ChestBlockEntity>>, SoundEvent, SoundEvent,
  BlockBehaviour.Properties)`; `TrappedChestBlock` ctor `(Properties)` only.
- `BlockEntityType` ctor `(BlockEntityType.BlockEntitySupplier<? extends T>, Set<Block>)`;
  `ChestBlockEntity` has a protected `(BlockEntityType<?>, BlockPos, BlockState)` ctor.
- Polymer: `eu.pb4.polymer.virtualentity.api.BlockWithElementHolder`,
  `…api.attachment.BlockBoundAttachment`, `…api.elements.ItemDisplayElement`;
  `eu.pb4.polymer.core.api.block.PolymerBlock`,
  `PolymerBlockUtils.registerBlockEntity` / `.SERVER_SIDE_MINING_CHECK`;
  `eu.pb4.polymer.core.api.item.PolymerItem`;
  `eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.addModAssets` / `markAsRequired`.
- Chest geometry: `ChestModel.createSingleBodyLayer`; box UV unwrap: `ModelPart.java:243`.
- All 12 `minecraft:textures/block/<wood>_planks.png` are 16×16.
- Vanilla recipes: `data/minecraft/recipe/chest.json`, `trapped_chest.json`.
