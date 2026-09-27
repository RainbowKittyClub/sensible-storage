# Crafting Table Grid

Every crafting table, vanilla's and each planked one, keeps what is in its 3×3 grid when its menu
closes, as a chest keeps its contents. It shows each slot's item on the matching square of its
top, gives a comparator signal, and keeps its name when broken. Two game rules gate the feature.
The generic parts go into kalic0re so that later blocks can reuse them: a persistent crafting
grid, a display of a container's contents that a glass chest could use, an empty-block piston
rule, and a claim-exemption probe (see the kalic0re split in the Reference section).

Claims here were checked against:
- the decompiled 26.2 source (`libs/decompiledjava/minecraft-26.2/`);
- Polymer's source at `9a3e8150` (`libs/polymer/`);
- the 26.2 client jar's models, and Polymer's cached 26.2-rc-2 client jar;
- the GOML (`goml-1.21.0+26.2`) and Ledger (`ledger-1.3.23`) jars in the live server's mod folder,
  decompiled with `scripts/decompile.sh`;
- source checkouts in `libs/`: Flan (`26.2`, `7be47db`), Open Parties and Claims (`26.2`,
  `e603c7e`) and FTB Chunks (`main`, `cae1dc9`). FTB Chunks has no 26.2 branch yet; `main`
  targets 26.1.2.

Unverified, and flagged where each comes up:
- how the displays look (S6 exists to judge that);
- how Fabric's transfer API treats a sided container;
- whether FTB Chunks' 26.1.2 API still works against its future 26.2 build, which doesn't exist yet.

Stage order:
- S1 (the block entity, and the menu that keeps it, on the planked tables) comes first and
  unblocks S2–S4, S6 and S7.
- S2 (vanilla's crafting table) needs S1.
- S3 (game rules) needs S1.
- S4 (comparator and names) needs S1, and S2 for vanilla's table.
- S5 (item model extents) touches only kalic0re and can be built at any point.
- S6 (displays) needs S1 and S5, and S2 for vanilla's table.
- S7 (pistons) needs only S1.
- S8 (the claim-exemption fallback) needs S3's plain menu.
  - It probes GOML, Flan, Open Parties and Claims, FTB Chunks and the Common Protection API.
  - On live it only acts if GOML is ever set to exempt crafting tables. Under Open Parties and
    Claims and FTB Chunks it acts by default.
- S9 (planked tables follow vanilla's claim exemptions) needs S8.

The feature is complete at S9. S1–S4 work without S6 but are not worth deploying without it:
items would disappear into a table with no sign that they are there.

## Findings that reshape the work

**The crafter can't be pushed, and neither can any block with a block entity, so letting an empty
table move is this mod's own rule.** Why the crafter can't be pushed:
- `CrafterBlock` extends `BaseEntityBlock` (`CrafterBlock.java:43`) and is registered with no push
  reaction (`Blocks.java:5601-5603`).
- So `PistonBaseBlock#isPushable` falls through to its last line, `!state.hasBlockEntity()`
  (`PistonBaseBlock.java:260`), and refuses it. It refuses chests, furnaces and hoppers the same
  way.

A block entity would make the table immovable in the same way, where today it moves.

Moving only an empty table is necessary, not just cautious:
- A piston overwrites the square a pushed block leaves with a moving piston, using flag 256,
  `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS`. That applies to the next block's
  (`PistonBaseBlock.java:309`, flags 324) and the head's (`:322`).
- `LevelChunk` only calls `preRemoveSideEffects`, which is what drops a container's items, when
  that flag is absent (`LevelChunk.java:305-315`).
- So a full table pushed directly by a piston would have its items deleted, not dropped.

**The grid has to be one container that every open menu's slots share, and it has to be the block
entity.** Vanilla makes a fresh grid per menu. A per-menu copy that is written back on each change
breaks with two viewers:
- A stack merged into a grid slot grows in place (`AbstractContainerMenu#moveItemStackTo`,
  `:650-664`). The only report is `Slot#setChanged`, which calls `Container#setChanged`
  (`Slot.java:69-71`).
- `TransientCraftingContainer` leaves `setChanged` empty (`:70`). A copy would miss the change, and
  the other viewer's stale copy would later overwrite it, duplicating or deleting items.

Making the block entity the container is also what Ledger needs to log it:
- Ledger's `SlotMixin` logs a slot's changes only when the slot's container is a
  `LocationalInventory`.
- Ledger's `BaseContainerBlockEntityMixin` makes every `BaseContainerBlockEntity` one.

**The shared grid has to be in place before `CraftingMenu` builds its slots.** Where vanilla makes
the grid:
- `AbstractCraftingMenu`'s constructor assigns a new `TransientCraftingContainer` to its `final`
  `craftSlots` (`AbstractCraftingMenu.java:23`).
- `CraftingMenu`'s constructor then builds its result slot and grid slots on it.

Replacing slots afterwards would crash under Ledger:
- Ledger's `AbstractContainerMenuMixin` gives each slot its menu from an inject at the head of
  `addSlot`.
- `SlotMixin#ledgerLogChanges` uses that menu without a null check whenever the slot's container is
  a `LocationalInventory`.
- A slot placed into `slots` directly would therefore throw a `NullPointerException` on its first
  change.

So a constructor mixin swaps the container where it is created, and everything is still built
through `addSlot` (S1). Mixins are the one thing that can't be shared between mods, so kalic0re
holds the menu and a hand-off, and this mod owns the mixin.

**Two of the four claim mods make vanilla's crafting table public by default, which would expose
whatever it holds.**

| Mod | Checked | What it does with a crafting table | Needs |
|---|---|---|---|
| GOML (live) | `goml-1.21.0+26.2` jar | Refuses every block use in a claim to non-members, unless the block is exempted. | S8's probe. It acts only if a config or tag entry ever exempts tables. |
| Flan | `libs/flan` | Uses a block entity to decide the permission. | S8's probe. It acts only if Flan's blacklist or a claim's allow list exempts tables. |
| Open Parties and Claims | `libs/open-parties-and-claims` | Exempts `minecraft:crafting_table` by default. | S8's probe. It acts by default. |
| FTB Chunks | `libs/ftb-chunks` | Exempts `minecraft:crafting_table` by default. | S8's probe. It acts by default. |

GOML (`EventHandlers.java:140-167`):
- Its exemptions are the `allowedBlockInteraction` config, empty on live, and the
  `goml:allowed_interactions` tag, which has no crafting table.
- That tag does include `#ftbchunks:interact_whitelist` as an optional entry, so with FTB Chunks
  installed, GOML would exempt vanilla's table as well.

Flan:
- It sends any block with a block entity to its `open_container` permission, and every other block
  to `interact_block` (`BlockInteractEvents.java:144-172`, `lenientBlockEntityCheck` off by
  default).
- So the table moves from `interact_block` to `open_container`, the chest's permission. That is
  what we want.
- It also serialises the block entity on every use inside a claim, to check its default tag
  blacklist (`:181-188`). That is cheap for nine stacks.

Open Parties and Claims:
- Its `forcedBlockProtectionExceptionList` defaults to `["minecraft:crafting_table"]`
  (`ServerConfig.java:527-537`). Anyone can use one in anyone's claim.

FTB Chunks:
- Its `ftbchunks:interact_whitelist` tag lists `minecraft:crafting_table`
  (`data/ftbchunks/tags/block/interact_whitelist.json`).
- `Protection.EDIT_AND_INTERACT_BLOCK` allows anything in that tag before looking at the claim
  (`Protection.java:112-124`).

Planked tables are in neither exemption, so under those two mods they would be private while
vanilla's is public. They should behave as vanilla's does (S9).

A tag can't be subtracted from. Instead, each mod has an API that can ask whether a player would
be refused if the block weren't exempt:
- GOML: `Claim#hasPermission` over `ClaimUtils#getClaimsAt`, plus `ClaimUtils#isInAdminMode`.
  That repeats its own `testPermission` (`EventHandlers.java:211-230`) without the exemption
  check in front of it.
- Flan: `ClaimHandler#canInteract(player, pos, open_container)` (`ClaimHandler.java:27-29`). Its
  blacklist and a claim's allow list are only checked in its use-block handler
  (`BlockInteractEvents.java:117-122`).
- OPAC: `IChunkProtectionAPI#onBlockInteraction` with `targetExceptions = false`
  (`IChunkProtectionAPI.java:94`).
- FTB Chunks: `ClaimedChunkManager#shouldPreventInteraction` with a caller-supplied `Protection`
  (`ClaimedChunkManager.java:91`).

Patbox's Common Protection API, which GOML bundles and Flan implements, can't stand in for the
per-mod calls:
- GOML's provider mixes its config exemption into `canInteractBlock`
  (`GomlProtectionProvider#canInteractBlock`).
- Flan registers its provider only when the API mod is present (`FlanFabric.java:88`, `:114`).

It still serves as a best-effort catch-all for any other claim mod that implements it.

OPAC's chunk-access overrider can't do this job either: it grants whole-claim access and, by its
own documentation, leaves block exceptions alone (`IChunkAccessOverriderAPI.java`). S8 builds on
the four per-mod APIs.

Whatever the claim mod, making the table a real container gives it what vanilla gives a chest:
- a lock (the `lock` component, checked in `canOpen`, `BaseContainerBlockEntity.java:68-81`,
  before `createMenu`, `:137-146`);
- a custom name as the menu title (`:151`);
- contents carried by the `container` component (`:153`, `:164`);
- contents dropped when broken (`BlockEntity#preRemoveSideEffects`, `BlockEntity.java:248-252`),
  which Ledger logs through its `ContainersMixin`.

**The server can read every item's model, vanilla's included, so items can be sized from their real
geometry.** The dedicated server jar carries no `assets/`, but:
- Polymer already downloads the vanilla client jar and exposes its root
  (`PolymerCommonUtils#getClientJarRoot`, `PolymerCommonUtils.java:47-80`).
- It is cached on both servers (`polymer/cached_client_jars/`). It is 26.2-rc-2, which Polymer pins
  (`:39`).
- Every other mod's item models are in the jar the server loads.

So S5 resolves an item's model to a box and a flat-or-not flag, rather than guessing from the
item's class. Guessing from the class would go wrong for doors, torches, saplings and flowers:
they are block items drawn as flat sprites.

Every item is drawn at one scale, 3/16 per block (16 model units):
- A flat sprite's 16 px canvas spans 3/16.
- A full block is a 3/16 cube.
- Other 3D models keep their proportions at that scale.

The box therefore only positions an item and decides whether it lies flat. It never sets the
scale.

The gap is vanilla's 13 `minecraft:special` model types (`SpecialModelRenderers.java:16-28`),
whose geometry is code, not JSON. Nothing is measured by hand. Instead:
- An item that places a block uses that block's shape, which the server knows. A chest's is
  1–15 × 0–14 × 1–15 (`ChestBlock.java:77`).
- Anything else counts as a full block.

Neither is exact, and it doesn't need to be.

**Polymer builds a chunk's display holders before that chunk has any block entities.**
`WorldChunkMixin` creates every `BlockWithElementHolder`'s holder at the end of `LevelChunk`'s
constructor (`WorldChunkMixin.java:54-85`), and a chunk's block entities are added later. A holder
therefore can't read the grid when it is created. It has to be filled once the block entity
arrives (S6).

**A display's view range is relative, so 0.25 means a quarter of the default distance, not a fixed
16 blocks.**
- A client draws a display within `view_range × 64 × Entity#getViewScale` blocks
  (`Display.java:403`).
- The view scale is the render distance in chunks ÷ 8, clamped to between 1 and 2.5, multiplied by
  the Entity Distance slider (`LevelExtractor.java:231`).
- So 0.25 draws within 16 blocks at 8 chunks or fewer and 100% entity distance, and within 40
  blocks at 20 chunks or more.

That meets the goal, which is only to be shorter than the default. No hard limit is planned.

## How these stages get tested

On the dev server (`scripts/devserver.sh rkc/mods/sensible-storage …`, after `rcon "list"` confirms
nobody is on), with three clients: modded (handshake), vanilla with the pack, and vanilla without
it.

A grid can be filled and read with no client:
- `item replace block <pos> container.<n> with <item>` reaches any block entity that is a
  `Container` (`ItemCommands.java:376`).
- `data get block <pos> Items` reads the saved grid.

Game rules flip over RCON.

S2 needs a vanilla crafting table placed *before* its build is installed, to show that existing
tables need no migration.

kalic0re changes go through `./gradlew publishToMavenLocal` and a `kalic0re_version` bump before
sensible-storage builds against them. `runServer` runs kalic0re's classes without relocating them,
so a relocated jar is only exercised by installing it (CLAUDE.md, "Sharing code between mods").

Protection mods go in `run/mods-server/`, which only the dev server loads (`0cf9776`):
- For S1: GOML and Ledger from the live mod folder, plus fabric-language-kotlin, which Ledger
  depends on.
- For S8: Flan (`26.2-1.12.8-fabric`) and OPAC (`fabric-26.2-0.31.6`) from Modrinth, alongside
  GOML. FTB Chunks has no 26.2 build yet, on Modrinth, CurseForge or `maven.ftb.dev`.

A claim needs a second player who is not its member.

A stage is not finished until its "Done when" has been observed, so a failure at one stage points
at that stage and not the ones before it.

## S1 — Grid block entity and a menu that keeps it — **done**

**Build.** A new kalic0re package, `common/craft/`, released as 0.20.0 and added to this mod's
`kalic0re_shared_packages`. It holds four classes.

`CraftingGridBlockEntity`, abstract, `extends BaseContainerBlockEntity implements
CraftingContainer, WorldlyContainer`:
- It holds nine stacks, 3×3, saved as `Items` with `ContainerHelper`. Its default name is
  `container.crafting`.
- It records every menu open on it, from `startOpen` and `stopOpen` (`Container.java:47-51`).
- From `setItem`, `removeItem` and `setChanged` it tells each open menu that the grid changed, as
  `TransientCraftingContainer` tells its one menu (`:57`, `:66`). `setChanged` is the one that
  catches a stack grown in place.
- It keeps a horizontal facing, re-taken from the player's facing whenever someone opens it while
  the grid is empty, and saved with the grid. S6 turns the displays by it.
- Whether it holds items and whether hoppers reach it are abstract hooks (S3). Until S3,
  `getSlotsForFace` returns no slots.

`PersistentCraftingMenu extends CraftingMenu`, built with `ContainerLevelAccess.NULL`:
- `CraftingMenu` uses its access only in `slotsChanged` (`:76`), `removed` (`:98`) and
  `stillValid` (`:104`). With `NULL`, closing no longer hands the grid back, without the menu
  having to re-implement `removed`.
- It overrides `stillValid` with the grid's own check, `Container#stillValidBlockEntity`. That
  closes the menu when the table is broken, and the server checks it before every click
  (`ServerGamePacketListenerImpl.java:1942`).
- It overrides `slotsChanged` to recompute the output with `CraftingMenu#slotChangedCraftingGrid`
  (`:47`, protected static). It skips that while a recipe is being placed, tracked through the
  public `beginPlacingRecipe` and `finishPlacingRecipe` because `CraftingMenu#placingRecipe` is
  private.
- It calls `startOpen` from its constructor and `stopOpen` from `removed`, as `ChestMenu` does
  (`ChestMenu.java:54`, `:102`).
- It computes the output as it opens.

`CraftingGridHandoff` is a thread-local that the menu's factory sets around its constructor call.
The consumer's mixin reads it, because the subclass's own fields are not assigned until the
superclass constructor returns.

`BlockCraftingMenu` is a plain, per-menu `CraftingMenu` that stays valid at a given block. It
replaces `PlankedCraftingTableBlock`'s private `Menu`, and S3 and S8 use it.

In this mod:
- `block/CraftingTableBlockEntity extends CraftingGridBlockEntity`.
  - Its type is valid for every planked table.
  - It is registered with `PolymerBlockUtils.registerBlockEntity(type)`, which strips it from every
    client's chunk data (`PolymerBlockUtils.java:88-91`).
- `PlankedCraftingTableBlock` implements `EntityBlock`, and `getMenuProvider` returns the block
  entity. The block entity's `createMenu` checks the lock (`BaseContainerBlockEntity.java:137-146`)
  before building a `PersistentCraftingMenu`.
- A `$`-prefixed `AbstractCraftingMenuMixin` hands the grid in at the
  `new TransientCraftingContainer` (`AbstractCraftingMenu.java:23`). Which injector to use, and
  whether a `NEW` target has `this` in time, is to be settled by building it.

**Done when.** On a planked table, on both the modded and the vanilla client:
- Items put in the grid are still there after closing and reopening, and after a restart, and
  `data get block` lists them.
- Reopening a table with a recipe laid out shows the result.
- Two players on one table see each other's changes as they happen, and each one's output follows.
- Crafting consumes the ingredients. Shift-clicking the output crafts repeatedly. A cake leaves its
  buckets in the grid. The recipe book fills the grid from the inventory.
- Breaking the table drops the grid and closes the menu for anyone still in it.
- A table placed from a renamed item shows that name as the menu title. One placed with a `lock`
  component refuses a player without the key.
- With GOML, a non-member can't open a table inside a claim.
- With Ledger, `/lg search` lists grid insertions and removals at the table's position.

Also record the answer to this question: `ResultSlot#onTake` consumes through
`craftSlots.removeItem` and `setItem` (`ResultSlot.java:91-112`), not through a slot's
`setChanged`, and Ledger's per-slot snapshot only updates on `setChanged`. Does Ledger log the
consumed ingredients as removals, late, or not at all?

### As built

Built as planned, with these departures:
- **The hand-off is a tail inject, not a `NEW` target.** A handler replacing the `new` would
  have to return a `TransientCraftingContainer`, which the grid is not. Instead
  `AbstractCraftingMenuMixin` shadows `craftSlots` `@Mutable` and assigns
  `CraftingGridHandoff.take()` at the tail of `AbstractCraftingMenu`'s constructor, which runs
  before `CraftingMenu` builds any slot. The unused transient grid is still built.
- **The grid tracks its menus through `attach` and `detach`**, beside `startOpen` and
  `stopOpen`, and `clearContent` notifies them too. `PersistentCraftingMenu` is final, as
  Checkstyle requires.
- **`PlankedCraftingTableBlock` no longer overrides `getMenuProvider`.** It inherits S2's mixin,
  and its private `Menu` became kalic0re's `BlockCraftingMenu`.

Observed over RCON, opening menus as a Fabric `FakePlayer` through a temporary command, since
removed:
- The menu is a `PersistentCraftingMenu` whose grid slots sit on the block entity.
- A 2×2 of planks shows a crafting table in the output as it opens.
- Closing leaves all four stacks in place, and `data get block` lists them before and after a
  restart.
- A table with a `lock` component returns no menu to a player without the key.
- Breaking a filled table drops the table and its grid.

Still to observe in-game: two players on one table, crafting and shift-crafting, the cake's
buckets, the recipe book, the name as the menu title, and GOML and Ledger (both installed in
`run/mods-server/`).

Later the same day:
- **The facing is also re-taken whenever a change leaves the grid empty.** It comes from the
  player whose click or recipe-book placement emptied it (`PersistentCraftingMenu` names them
  through `beginAction`), or else from the first non-spectator with the grid open. A spectator's
  facing is never taken.
- **The grid tells its menus only of real changes.** Vanilla's click path calls `setChanged` up
  to five times for one pickup. Each call recomputed and resent every viewer's output under a new
  state id, where a vanilla table sends one or two. The grid now keeps copies of what it last
  reported and notifies only when the contents differ, and the display refreshes only on a
  content or facing change.
  - The recompute stays immediate, because shift-crafting needs the output current after each
    take.
  - This was suspected of causing a ghost item on the modded client, a sensiblestorage item left on
    the cursor after being put down. It was not the cause; see below.
- **`PlankFallbackRecipe` sorts out the crafting recipes once per recipe list**, rather than
  scanning every recipe on each check.

The ghost item, traced with logging on both sides that is kept on the `debug/ghost-diagnostics`
branch:
- **Every hash check fails for a sensiblestorage item on a handshake client.** The server sends
  the real item (`PolymerModelBlockItem#getPolymerItem` returns it when `HANDSHAKE.supportsAll`),
  and the client shows it. The client's click packets report the stack under the vanilla
  fallback's id, though, with all four of Polymer's added components matching. Polymer's check
  (`RemoteSlotSynchronizedMixin`) re-translates the server's stack to the real item. So after
  every pickup the server corrects the cursor, and after every put-down it resends the slot.
  The id turns into the fallback's on the client: Polymer's `ByteBufCodecsRegistryMixin`, which
  swaps a Polymer item for its replacement as an item id is encoded, runs on both sides. On the
  client it asks our `getPolymerItem`, and `HANDSHAKE.supportsAll` reads false there, since the
  markers are registered clientbound. A vanilla client is unaffected: it is sent the fallback, so
  both sides agree. It is a Polymer bug; a draft report is on the `debug/ghost-diagnostics` branch,
  in `docs/debug/`.
- **A drag-place turns those corrections into a ghost.** It is three packets sent in the same
  millisecond. The server answers each, and its cursor corrections after the first two
  (`ClientboundSetCursorItemPacket`, which carries no state id) land after the client has finished
  the drag. The third packet's reported cursor is empty, which matches, so nothing clears them.
- **Fixed by `AbstractContainerMenuMixin`**, which bumps the state id after every cursor
  correction. A click already in flight then arrives stale and is answered with a full resync,
  cursor included. Confirmed in-game: no ghost. The needless corrections remain, harmless.

## S2 — Vanilla's crafting table — **done**

**Build.** A `$`-prefixed `CraftingTableBlockMixin` makes `CraftingTableBlock` implement
`EntityBlock`, and S1's block entity type becomes valid for `Blocks.CRAFTING_TABLE` as well:
- `newBlockEntity` returns the grid only when the type accepts the state, and null otherwise.
  `BlockEntity`'s constructor throws for a state its type doesn't list
  (`BlockEntity.java:55-62`). This keeps vanilla behaviour for any other mod's block that extends
  `CraftingTableBlock`.
- `getMenuProvider` returns the grid when there is one, and vanilla's menu otherwise. The planked
  table then inherits this rather than overriding it.
- S4 and S6 add their own parts to this same mixin.

Existing tables need no migration: `Level#getBlockEntity` creates a missing block entity on demand
(`Level.java:696-701`, `LevelChunk.java:386-387`).

The mixin also applies on a handshake client, which runs the same jar. There it only creates empty
client-side block entities that the server never fills. That is harmless, but check it.

**Done when.**
- `minecraft:crafting_table` does everything S1's Done-when lists.
- A table placed before this build was installed opens and keeps its items.
- A handshake client shows no errors near crafting tables.

### As built

Built as planned. The mixin also carries S4's comparator methods and S6's
`BlockWithElementHolder`, and `getMenuProvider` is an inject at its head that returns the grid.

Observed: `setblock … minecraft:crafting_table` gets a `sensiblestorage:crafting_table` block
entity, which keeps items as S1 describes. Still to observe: a table placed before this build,
and a handshake client near crafting tables.

## S3 — Game rules — **done**

**Build.** Two rules, both registered with Fabric's
`GameRuleBuilder.forBoolean(…).buildAndRegister(…)`, with `gamerule.sensiblestorage.<path>` names
in `ChestLanguageProvider`. Both are read on every use, so flipping one takes effect at once.

`sensiblestorage:crafting_tables_hold_items`, default `true`:
- Off, the block entity's `createMenu` builds a `BlockCraftingMenu` instead, so the table behaves
  as vanilla's does and hands the grid back on close. The lock and the custom name still apply,
  since both are checked before `createMenu` is reached.
- If the grid still holds anything when the table is opened with the rule off, it is dropped at
  the table first (`Containers#dropContents`), and then the menu opens.
- Until that happens, the leftovers are still in the table. The displays and the comparator keep
  showing them.

`sensiblestorage:hoppers_can_access_crafting_tables`, default `false`:
- `getSlotsForFace` returns all nine slots only when both rules are on, and none otherwise.
- Hoppers read a container's slots through it both to fill it and to drain it
  (`HopperBlockEntity.java:174-177`, `:283-284`).
- Droppers and crafters insert through the same `HopperBlockEntity#addItem`
  (`DropperBlock.java:66`, `CrafterBlock.java:202`).
- Copper golems are unaffected either way, because they only deliver to vanilla's chest and
  trapped chest (`CopperGolemAi.java:44`).
- Fabric's transfer API (8.0.6) was not checked for how it treats a sided container. No pipe mod is
  on live.

Ledger will log leftovers dropped on opening as a break (`ContainersMixin` reports `"broke"`). That
is the closest label it has, and is left as it is.

**Done when.** With `hold_items` off:
- A table that held items drops them the next time it is opened, and then hands its grid back on
  close.
- Its comparator falls to 0 once the leftovers are gone.

With `hold_items` back on, new items stay in the table.

With both rules on:
- A hopper above fills the grid slot by slot (from slot 0, stacking before moving on), and one
  below drains it.
- A dropper facing the table fills it.

With either rule off, none of those three reach the grid.

### As built

Built as planned, with one addition: a player who gets a grid of their own through S8 skips the
leftover drop, so a table never spills its owner's items on a stranger.

Observed:
- With `hold_items` off, opening a table that held three sticks gives vanilla's menu, empties the
  grid and drops the sticks at the table.
- Hoppers above and below a table move nothing with `hoppers_can_access` off, and fill and drain
  it once it is on. Each flip took effect at once.
- With `hold_items` off, a hopper above does nothing even with `hoppers_can_access` on.

Still to observe: a dropper.

Later: a spectator opening a table with `hold_items` off gets vanilla's menu without spilling
the leftovers.

After an in-game look: **leftovers go to the player instead of the ground.** A table opened with
`hold_items` off no longer drops what it kept. `BlockCraftingMenu#fillGrid` moves the stacks into
the opener's own grid, in the same slots, and works out the output once. The grid empties, and
the menu hands them back to that player's inventory on close like anything else in it, dropping
only what doesn't fit, as vanilla's does. A spectator still takes nothing, and a player given a
grid of their own through S8 still leaves the table alone. The Ledger note above no longer
applies: nothing is dropped.

## S4 — Comparator output and kept names — **done**

**Build.** The comparator signal is the number of filled slots, 0–9, as the crafter's is
(`CrafterBlockEntity.java:258-269`, `CrafterBlock.java:64-71`):
- `PlankedCraftingTableBlock` overrides `hasAnalogOutputSignal` and `getAnalogOutputSignal`.
  Vanilla's table gets the same through S2's mixin.
- Updates come for free: `BlockEntity#setChanged` calls `updateNeighbourForOutputSignal`
  (`BlockEntity.java:213-217`).

Names are kept the way vanilla keeps a chest's. Its loot table copies `minecraft:custom_name` from
the block entity (`loot_table/blocks/chest.json`, built by `createNameableBlockEntityTable`,
`VanillaBlockLoot.java:802`).
- `ChestLootProvider` switches the tables from `dropSelf` to `createNameableBlockEntityTable`.
- A generated override of `minecraft:blocks/crafting_table` does the same for vanilla's table. It
  keeps that table's `survives_explosion` condition and `random_sequence`.
- A datapack that also overrides that loot table would win or lose against this one by pack order.
  None is known.

Placing a named table already applies the name (`BaseContainerBlockEntity.java:151`).

**Done when.**
- A comparator reads 0 to 9 as the grid fills, from both kinds of table.
- A renamed table, broken, drops an item with that name, and placed again shows it as the menu
  title.
- An unnamed table drops the same item it does today.

### As built

Built as planned. The generated override of `minecraft:blocks/crafting_table` has no
`random_sequence`: Fabric's loot provider omits it, as it does for every table this mod
generates, and it only matters to a table with random rolls. A byte comparison of the
regenerated `src/main/generated` against the previous run shows exactly the expected changes:
- the 27 planked tables' loot tables gain `copy_components`;
- `data/minecraft/loot_table/blocks/crafting_table.json` is new;
- the language file gains the two game rule names.

The other 3,000-odd files are identical.

Observed over RCON:
- Comparators read 5, 2 and then 4 as the grids changed.
- `loot spawn … mine` drops "Bench" from a renamed spruce table and "Oak Bench" from a renamed
  vanilla one, and a plain crafting table from an unnamed one.

Still to observe: a renamed table's name as the menu title.

## S5 — Item model extents — **done**

**Build.** kalic0re `common/display/ItemModelExtents` resolves a stack to its model's box in model
units (0–16), plus whether it is a flat sprite. The box is for placing the item, not for sizing it
(Findings). Resolved models are cached by id for the life of the server, since assets can't change
without a restart.

Where it reads models from:
- The model id is the stack's `minecraft:item_model` component, which defaults to the item's id.
- Vanilla's files come from the client jar through `PolymerCommonUtils#getClientJarRoot`.
- Every other namespace comes from the loaded mods' own `assets/`, through their Fabric mod
  containers. That covers modded items and Polymer items alike.

How it handles each type of item definition (`items/<id>.json`):
- `model`: resolved as below.
- `composite`: the union of its parts.
- `condition`, `select` and `range_dispatch`: the default branch (`on_false`, `fallback`, or the
  first entry).
- `special`: if the item is a `BlockItem`, the bounds of its block's default-state shape
  (`BlockState#getShape` against an empty getter at the origin). Anything else counts as a full
  block. Nothing is measured by hand.
- `empty`: nothing.

How it resolves a model: follow the `parent` chain. `elements` give the union of their boxes, with
each element's rotation applied to its corners. A chain that reaches `builtin/generated`
(`item/generated.json`) is a flat 16×16 sprite.

Known gap: the box is always that of the model a modded or pack-equipped client draws. A pack-less
vanilla client may be drawing a Polymer item's vanilla fallback instead.

**Done when.** A scratch call on the dev server (not committed) logs these:
- stone, a 16³ box;
- a stone slab, 16×8×16;
- a stick, a torch and an oak door, each flat;
- a chest, its block's shape, 1–15 × 0–14 × 1–15;
- a trident, the full-block fallback;
- a Terrestria log, a cube read from Terrestria's own jar;
- an id with no model file, the fallback, without throwing.

### As built

Built as planned. A temporary startup log, since removed, gave:
- stone 16³, a stone slab 16×8×16;
- a stick, a torch, an oak door, a cake and a bell each flat;
- a chest 1–15 × 0–14 × 1–15, from its block's shape;
- a player head 4–12 × 0–8 × 4–12, from its block's shape;
- a trident and a shield the full-block fallback;
- an oak fence −2 to 18 along z, its inventory model's rails reaching past the block;
- a Terrestria redwood log a cube, read from Terrestria's own jar.

Still to observe: an id with no model file.

Later the same day, after an in-game look:
- **Each box carries the model's front.** That is the y of its `display.fixed` rotation,
  inherited through parents; a special model takes it from its `base`. Vanilla's chest
  (`item/template_chest`, fixed y 180) and the planked chest built on it are authored facing
  south.
- **A mod can name the model its items really draw with.** A planked chest's own `item_model`
  names no definition, because Polymer gives it `<wood>_chest_whole` on the way out.
  `registerModelSource` takes such a mapping, and sensible-storage supplies kalic0re's
  `PolymerModelBlockItem#model` and `PolymerModelItem#model`.
- **The client jar is fetched in the background.** Polymer downloads it when it isn't cached, so
  `prepare()` fetches it off-thread as a server starts. Nothing is cached until that has settled.

After a second in-game look:
- **Transformations are applied.** A trident and a shield sat centred on their square's corner. In
  26.2 every item model node may carry a `transformation`, composed down the tree and applied
  after the model is centred (`ItemStackRenderState#applyTransform`). A special item with no
  block is an entity model built around its own origin, which a trident's and a shield's only
  flip (`scale [1, -1, -1]`). Such an item now stands in with a block-sized box centred on that
  origin, run through the transformations, so it centres on its square. A block item's special
  model still takes its block's shape, which is where its transformations put its geometry.
- **Special models are measured at build time.** The box above still left a trident's pole, which
  reaches about 1.7 blocks below its origin, inside the table. kalic0re's
  `SpecialModelExtentsProvider` now bakes every special renderer vanilla's item definitions use,
  from vanilla's own entity models, and records the points each reports through `getExtents`,
  the call a client sizes items by. The result is 68 boxes in
  `kalic0re/special_model_extents.json`, keyed by the renderer's sorted JSON. A measured box is
  used first, under the node's transformations; then a block's shape; then the box around the
  origin. That needs a client datagen run, so sensible-storage now has `datagen_client=true`. Its
  other 3,185 generated files came out byte-identical. The measured boxes for chests, heads,
  conduits and shulker boxes match the block shapes they replace. Banners and decorated pots
  measure taller than their blocks, as drawn. A planked chest's renderer names its own texture,
  so it is not in the table and keeps its block shape.

## S6 — Container contents display — **done**

**Build.** In kalic0re, `common/display/ContainerDisplay`: a generic `ElementHolder` that shows a
container's slots as items. It is configured with:
- a slot layout: each slot's anchor point on the block, turned by a horizontal facing. The first
  preset is a grid on a block face; a glass chest would add a floating 3×3×3 one.
- the item scale, as the size of one block;
- a rest mode: on a surface, or floating centred;
- a view range;
- who may watch.

How it behaves:
- It has one `ItemDisplayElement` for each non-empty slot, added and removed as slots fill and
  empty, so an empty container sends no entities. Each shows a one-item copy of its slot's stack.
- It draws in `ItemDisplayContext.NONE`, so S5's boxes apply without a per-model display transform.
- Every item gets the configured scale: 16 model units become one block's size. S5's box then
  centres it horizontally on its anchor. In surface mode its bottom rests on the surface, lifted
  1/512 like `ChiseledBooksHolder.LIFT`.
- Flat sprites lie face-up with their top toward the facing. Everything else stands upright,
  keeping its proportions, with its front toward the opener.
- The owner pushes changes to it and calls `tick()` itself, as `ChiseledBooksHolder` does.

In this mod, the crafting grid's configuration:
- The layout is 3×3 on the top face. Vanilla's `crafting_table_top.png`, which every planked top
  copies, has grid lines at pixels 3, 6, 9 and 12, so the squares are centred at 5/16, 8/16 and
  11/16. That grid is symmetric, so a quarter turn keeps every centre on a square.
- Slot `x + 3y` (`AbstractCraftingMenu.java:33`) sits in row `y` counted from the far side, and
  column `x` counted from the opener's left.
- One block is 3/16 wide, in surface mode, with a view range of 0.25 (Findings). The displays are
  shown to every client tier.
- The planked table gets the holder from `BlockWithElementHolder`, and vanilla's table from the
  same interface added through S2's mixin. Polymer checks for the interface with `instanceof` in
  `Block`'s constructor (`BlockMixin.java:21`), which a mixin-added interface passes.
- It is filled once the block entity loads, not when it is created (Findings). Use Fabric's
  `ServerBlockEntityEvents.BLOCK_ENTITY_LOAD` or the block entity's own `setLevel`, whichever turns
  out to run after both exist.

**Done when.**
- A grid filled over RCON shows each item on its own square, with the far row toward whoever last
  opened the table while it was empty.
- On their squares, each centred:
  - a stone block is a 3/16 cube;
  - a slab is 3/16 across and 3/32 tall;
  - a stick and a door each span 3/16, lying flat;
  - a chest stands upright at about 14/16 of the stone block's size.
- The displays follow edits live and disappear when the table is emptied or broken.
- They are visible on all three clients and fade out at the distance Findings predicts for that
  client's settings.
- The actual requirement is the look. Judge it in-game.

### As built

Built as two classes rather than one `ContainerDisplay`, following STYLE.md's split between
geometry and holder:
- `ContainerContentsVisual` holds the slot layout, the one scale and the pose maths.
- `ContainerContentsHolder` owns the elements.

The holder pulls its contents from a supplier, filling itself the first time a player starts
watching and on each `refresh()` after that. That made the block-entity load event unnecessary.
Its elements draw in `NONE` with no interpolation, so a changed slot snaps rather than morphs.

The logged poses put slot 0 on the far-left square for both facings tried, a cube's bottom on the
top face, and a flat item face-up, 1/512 above it.

Nothing about the look has been observed yet. A showcase stands at (41, −54, 42) and
(44, −54, 42), nine mixed items each, with an empty spruce table at (47, −54, 42) for the facing.

Later:
- **Planked chests stood 180° off**, backs to the opener, and vanilla's would have too. Each
  upright item now turns by its model's front (S5).
- **The holder's source now finds the grid through the holder's current attachment**, rather
  than a position remembered at creation. Polymer carries the holder to the table's new place
  when a piston moves it, so before this a moved table's display read the empty spot it left.
  This came out of the code review.

After an in-game look at tridents and shields:
- **An item whose inventory icon is a flat sprite lies flat as that icon.** A trident standing
  upright, nearly two blocks of model tall, looked bad. The display now resolves each item in
  both contexts (`ItemModelExtents#of` takes the context, and a `select` on `display_context`
  picks its case as a client does). When the inventory look is flat, it draws that in
  `ItemDisplayContext.GUI`. Only the trident, the spyglass and the seven spears change model,
  from what a hand holds to their icon. Every other flat item draws the same as before, and an
  amethyst bud also takes the inventory's 2 px centring nudge, its model's only `gui` transform.
  In vanilla, 722 items lie flat as icons, 814 block items stand, and the shield is the only
  other item left standing.
- **Standing models are smaller: 5/32 to the block**, where an icon's canvas stays 3/16 wide, the
  spacing between squares. `ContainerContentsVisual` takes the two sizes separately.

After another look, the blocks drowned out the flat icons:
- **Icons stand upright too**, facing the player the grid faces, like every other item. An icon's
  front is its face (`item/generated`'s `fixed` rotation, 180, as a chest's), so the same turn
  serves both. Its box is the whole canvas, so a sprite with empty rows at the bottom stands a
  pixel or two above the table. The pose no longer has a lying branch.
- **`sensiblestorage:crafting_tables_show_inventory`**, default `true`, hides every table's display
  when off. The display's source reads it and shows every square empty. A display is only
  reread on a change, so `CraftingTableGrids` keeps a weak set of the displays it made and
  refreshes them all from Fabric's `GameRuleEvents.changeCallback`, and a flip shows at once. The
  language file gains the rule's name and nothing else changes in the generated output.

Then, to lift the items off the table and toward the player:
- **Everything hovers 1/16 above the top** (`SlotLayout.topGrid` takes a height).
- **Items billboard.** Icons are `CENTER`, facing the viewer outright. Models are `VERTICAL`,
  turning about the upright only, because a cube billboarded `CENTER` only ever shows its front
  face and reads as a flat square. A billboard's frame turns its +z toward the viewer, so a
  billboarded item takes the turn it would for a player facing north. It pivots about its own
  centre, placed where it would be standing upright. Tilted toward a viewer above, a cube's
  lower back corners dip by up to about a thirtieth of a block, which the 1/16 covers. The
  table's facing now only orders the squares.

And after a look at that: **the hover is 1/32, and models don't billboard.** They stand facing
the player the grid faces again, their bottoms 1/32 above the top. Icons still face the viewer
`CENTER`. Turned about its centre, an icon's lowest edge never drops below where it would stand
upright, so the smaller hover is enough.

Then: **the hover is 1/64, a block of model is 2/16, and an icon's canvas is 5/32**, a little
narrower than the 3/16 spacing between squares.

**A full hopper below made the display flicker.** `HopperBlockEntity#tryTakeInItemFromSlot` takes
one item out before it knows it has room, and puts it back when it has none, every 8 ticks. On a
slot holding one item, that emptied it and filled it again within the tick, and each change
refreshed the display at once, removing the item's element and adding it back. A display now
refreshes as the tick ends (`ContainerContentsHolder#refreshAtTickEnd`, flushed from
`ServerTickEvents.END_SERVER_TICK`), once however many changes the tick made, so a change
undone within it sends nothing. Menus are still told at once, which shift-crafting needs.

## S7 — Pistons move an empty table — **done**

**Build.** kalic0re `common/piston/PistonMovable` is a registry of rules that say "this
block-entity block may be moved now". This mod registers one: a `CraftingTableBlock` whose grid is
empty or absent.

This mod's `$`-prefixed `PistonBaseBlockMixin` wraps `state.hasBlockEntity()` in `isPushable`
(`PistonBaseBlock.java:260`) and asks the registry.

Neither of the other `isPushable` mixins clashes with it:
- GOML's cancels at the head for claim anchors only.
- Flan's cancels at the head for claim borders (`PistonMixin`, `WorldEvents.java:40-60`).

A moved table gets a fresh block entity, and nothing is lost because its grid was empty. Polymer
carries the display holder through the move (`BlockWithElementHolder#createMovingElementHolder`),
and an empty table has no displays anyway.

**Done when.**
- A piston pushes, and a sticky piston pulls, an empty vanilla table and an empty planked table.
- A table with one item in its grid stays put, as a chest does.
- A moved table opens and keeps items in its new place.
- The modded client shows the move smoothly.

### As built

Built as planned. Observed:
- A piston pushes an empty vanilla table, and the moved table has a fresh, empty block entity.
- A sticky piston pushes and pulls back an empty spruce table, which then keeps an item put in
  it.
- A spruce table holding one stick does not move.

Still to observe: the move on the modded client.

The display after a move was broken until S6's attachment fix, and is still to be observed
in-game.

## S8 — Claim-exemption fallback — **done**

**Build.** kalic0re `common/claim/ClaimExemptions` is a registry of probes. Each asks whether a
player would be refused at a block if the claim mod didn't exempt that block. The table asks
before building its menu. When any probe says yes, `createMenu` builds a `BlockCraftingMenu`: that
player crafts with their own items, as the exemption intends, and never sees or touches the stored
grid.

The probes (Findings has the APIs):

| Probe | Question it asks | Compiled against |
|---|---|---|
| GOML | Does any claim at the position deny the player, who isn't in admin mode? | `maven.modrinth:goml-reserved:1.21.0+26.2` |
| Flan | Does the player lack `open_container` there? | `maven.modrinth:flan:26.2-1.12.8-fabric` |
| OPAC | Would `onBlockInteraction(…, targetExceptions = false)` protect it? | `maven.modrinth:open-parties-and-claims:fabric-26.2-0.31.6` |
| FTB Chunks | Would `shouldPreventInteraction` prevent it under `EDIT_AND_INTERACT_BLOCK` without its tag check? | `dev.ftb.mods:ftb-chunks-fabric:26.1.2.8` from `maven.ftb.dev` |
| Common Protection API | Does `canInteractBlock` refuse it, for any other registered provider? | `eu.pb4:common-protection-api:2.0.0` from `maven.nucleoid.xyz` |

How the probes are built:
- The shared build already has the Modrinth and nucleoid mavens (`gradle/build.gradle:29-35`).
  `maven.ftb.dev` and the five `compileOnly` dependencies go in a new `build.mod.gradle` in
  kalic0re.
- Each probe is its own class, touched only behind `FabricLoader#isModLoaded`, so a server without
  that mod never loads it. Nothing is shaded in, since the dependencies are `compileOnly`.
- FTB Chunks has no 26.2 build anywhere, so its probe is compiled against 26.1.2. It catches
  `LinkageError`, logs once and disables itself if the API has moved by the time a 26.2 build
  appears. FTB Chunks' API may pull FTB Library types into signatures, which would need their own
  `compileOnly` entries. Settle that by building it.
- The Common Protection catch-all skips no provider. GOML's provider answers with GOML's config
  exemption included, so it never adds a false yes, and Flan's matches Flan's own probe.

**Done when.** A non-member at a claimed vanilla table gets an empty grid of their own, while the
owner's items stay put and stay displayed, in each of these setups:
- GOML, with `minecraft:crafting_table` added to `allowedBlockInteraction`;
- Flan, with the table in its `interactBlockBlacklist`;
- OPAC with its defaults.

In each of those setups, a member gets the stored grid.

With GOML and Flan left at their defaults, the non-member is refused outright, as before.

Record FTB Chunks as untested until a 26.2 build exists.

### As built

Built as planned, with three departures:
- **GOML's probe calls `ClaimUtils#canModify`.** Its signature has only Minecraft types, whereas
  `getClaimsAt` returns types from a library nested in GOML's jar. It is the same membership
  test GOML's own Common Protection provider uses.
- **A probe that stops linking answers strictly rather than disabling itself.** It refuses
  without an exemption and never allows a stand-in, logged once, so a broken probe can hand out
  a private grid but never a stored one.
- **FTB Chunks' probe also needs `ftb-teams-fabric:26.1.2.4`** on the compile classpath, for
  `PrivacyProperty`.

Observed: with GOML, Flan and Open Parties and Claims installed together, every probe linked and
answered for a fake player where there is no claim: not refused, and the stand-in allowed.

Still to observe: each setup in the Done-when, which needs a claim and a second player. Flan,
OPAC and Forge Config API Port, which OPAC needs, are parked in `run/mods/disabled/claims/`,
which the loader does not scan. Move one mod at a time into `run/mods-server/` to test it.

Later: a probe whose mod throws an ordinary exception answers strictly for that call, instead of
breaking the table's use.

## S9 — Planked tables follow vanilla's claim exemptions — **done**

**Build.** A planked table should be usable wherever vanilla's would be. The claim mods decide by
block id, so a planked table misses every exemption written for `minecraft:crafting_table`.

kalic0re `common/claim` gains a second question alongside S8's: would every loaded claim mod let
this player use a stand-in block, here `minecraft:crafting_table`, at this position? How each mod
is asked:
- **GOML:** its exemption test run on the stand-in (the `allowedBlockInteraction` config or the
  `goml:allowed_interactions` tag, skipped when a `BlockItem` is in hand,
  `EventHandlers.java:148-153`). Failing that, its membership test at the hit position and at the
  one beside it (`:155-166`).
- **Flan:** the steps of its use-block handler that read the block run on the stand-in's state and
  id (`BlockInteractEvents.java:101-172`). Those are the claim's allow list, the id blacklist and
  the interaction override (`:117`, `:121`, `:123`). Every other step falls to `open_container`,
  as it does for both tables, since both carry the same container block entity.
- **OPAC:** the `ChunkProtection#onBlockInteraction` overload that takes a `BlockState`
  (`ChunkProtection.java:832`), given the stand-in. It is internal rather than API, so it is
  guarded like S8's FTB Chunks probe: catch `LinkageError`, log once, disable.
- **FTB Chunks:** `shouldPreventInteraction` with `EDIT_AND_INTERACT_BLOCK`'s rule applied to the
  stand-in's state.
- **The Common Protection API** can only be asked about the block actually in the world. So an
  unknown claim mod still judges a planked table by its own id.

A `UseBlockCallback` listener runs in a `sensiblestorage:claims` phase. The phase is ordered ahead
of every claim mod's own:
- `goml:protection`;
- `flan:events` (`FlanFabric.java:52`, `:119-122`);
- `openpartiesandclaims:protection` (`CommonEventsFabric.java:80-90`);
- the default phase, where FTB Chunks listens (`FabricEventListeners.java:64`).

What the listener does at a planked table:
- If at least one claim mod is loaded and every loaded one would let the player use the stand-in,
  it opens the table itself and returns success, so no claim mod's listener refuses the planked id.
  "Opens" means the stored grid, or S8's private one when S8's probes say so.
- Otherwise it passes, and the claim mods decide as they would anyway.

Only use is mirrored. Breaking a planked table follows each mod's own rules.

**Done when.** A planked table and a vanilla table beside it, in the same claim, behave the same
for a non-member in each setup:
- OPAC at its defaults: both give a private grid.
- OPAC with its exception list emptied: both refuse.
- GOML at its defaults: both refuse.
- GOML with `minecraft:crafting_table` in `allowedBlockInteraction`: both give a private grid.
- Flan with `minecraft:crafting_table` in its `interactBlockBlacklist`: both give a private grid.

A member gets the stored grid at both, in every setup. Record FTB Chunks as untested, as in S8.

### As built

Built as planned, with two refinements to the listener:
- **It takes over only when needed.** It asks the probes about the real block too, and does
  nothing when every claim mod would allow the planked table as it is. Other mods' use-block
  listeners are skipped only when the stand-in rule is what lets the player in.
- **It uses the table as vanilla does with an empty hand**, through
  `BlockState#useWithoutItem`, in the main hand only. It passes when the player sneaks holding
  anything.

Nothing in-game has been observed yet.

Documented: a protection mod with neither a probe nor the Common Protection API is skipped for a
use the listener takes over.

## Settled decisions

- **Comparators through a crafting table: kept.** A comparator reads a block's own signal, instead
  of reading through it, whenever the block has one (`ComparatorBlock.java:102-104`), and every
  crafting table now has one. A build where a comparator reads a container behind a crafting
  table now reads the table's filled-slot count instead. That is accepted as the cost of the
  signal.

## Reference: what lives where

| Piece | kalic0re | sensible-storage |
|---|---|---|
| Persistent grid | `common/craft`: `CraftingGridBlockEntity`, `PersistentCraftingMenu`, `CraftingGridHandoff`, `BlockCraftingMenu` | `CraftingTableBlockEntity` and its type; `AbstractCraftingMenuMixin` |
| Vanilla's table | — | `CraftingTableBlockMixin` (entity block, menu, comparator, holder) |
| Game rules | — | both rules, their lang, the hooks' implementations |
| Names and loot | — | `ChestLootProvider`, the `blocks/crafting_table` override |
| Item placement | `common/display`: `ItemModelExtents` | — |
| Displays | `common/display`: `ContainerDisplay`, the slot-layout presets | the table-top layout and its wiring |
| Pistons | `common/piston`: `PistonMovable` | `PistonBaseBlockMixin`, the table's rule |
| Claims | `common/claim`: `ClaimExemptions`, the probes, the stand-in query, the early listener, their `compileOnly` dependencies | the table's calls to it |

`kalic0re_shared_packages` gains `craft`, `piston` and `claim`. `display` is already shared.
