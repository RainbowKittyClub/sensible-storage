# Vanilla chest stand-in

Send a vanilla client `minecraft:chest` (and `minecraft:trapped_chest` for trapped ones) instead of
today's barrier, so it gets the true chest outline, hitbox and collision, while the existing display
entity keeps drawing the planked wood over it. Since S9 the server pack draws vanilla's own chest
sheets transparent, so the server draws every vanilla chest a pack client sees as well (S10, S11).

Claims were checked against `libs/decompiledjava/minecraft-26.2/`, the 26.2 deobf jar's assets and
the 26.3 client jar, `libs/polymer` (`dev/26.2`, `9a3e8150`) and the RKC Polymer fork at
`rkc/forks/polymer` (`rkc/26.2`). They were also checked against Vineflower output of jars:
- live's Lootr, LootrPolymer, polymer-patch-bundle and universal_shops;
- Velocity 4.2.0 build 30, plus `javap` on its API jar;
- the user's client mods: Iris 1.11.4, Sodium 0.9.2, Sodium Extra 0.9.4, EMF, ETF, Punchy.

S1–S4, S8–S11 and S13–S16 are built and released in sensible-storage 0.6.0, and S14 also in
rkcore-proxy 0.1.2 (each stage's "As built"). Their bot legs are observed. The user saw the in-world
legs of S9–S11, S15 and S16 on 2026-10-17, on a vanilla 26.2 client with the pack; S8's handshake
leg and S14's live leg are not yet seen. S5 and S6 were built, tried in world and skipped. The
lid-timing, depth and outline figures are worked out from the client code, not measured. Unverified:
anything a client mod not listed above does to chests.

Stage order:
1. S1 (harness and stand-in state), S2 (interaction, mining, break effects), S3 (covering
   geometry), S4 (lid event routing).
2. S5, then S6, both skipped.
3. S8 rips out S3, S4 and S6.
4. S9 (transparent sheets in the pack), then S10 (vanilla chests drawn by the server) and S11
   (Lootr).
5. S13 (lid ticking and display cost), S15 (chests on vanilla chest minecarts), S14 (the lobby) and
   S16 (chests over block displays), each needing S10.
6. S7 (docs).

S12 (other sources) records decisions and can close any time. S8 alone leaves a vanilla client
watching the stand-in's oak lid swing inside ours again, so nothing deploys before S9 and S10.
**S10 makes the feature complete.** S11 and S15 make it complete on live, where Lootr's chests and
carts are almost every loot container, and S14 extends it to the lobby. The client-item plan built
alongside gives a pack client a non-placing firework star, so only a client without the pack still
predicts a chest.

---

## Findings that reshape the work

**Polymer already gives the stand-in's shapes to the server, for every caller.**
`BlockBehaviourMixin` (`libs/polymer/polymer-core/.../mixin/block/BlockBehaviourMixin.java:23-46`)
replaces both `getShape` and `getCollisionShape` of any `PolymerBlock` with the client state's. A
player's query uses that player's context; every other query uses a null context. The player path
is `BlockStateBaseMixin.java:30-40`. `PlankedChestBlock.getPolymerBlockState` answers null with the
barrier (`PlankedChestBlock.java:125-128`), so today the *server* treats a planked chest as a full
cube as well. Items rest on it at y+1, mobs collide with a cube, and `player.pick` in kalic0re
`common/aim/Crosshair.java:38` (the hover-label ray) stops at the cube. The cube is also why
`ChestBlocks.java:110-114` has to force `isRedstoneConductor` off. Returning
`Blocks.CHEST.withPropertiesOf(state)` gives all of them vanilla's chest shapes, the double-chest
half shapes included (`ChestBlock.java:77-78`, `:198-203`). Polymer's Javadoc asks for exactly this
(`PolymerBlock.java:24`).

**A vanilla client draws the vanilla chest only where it holds a client block entity. It gets one
from almost everything except a chunk load.** The chunk mesher draws nothing for a chest, because
its block model is particle-only (`assets/minecraft/models/block/chest.json`, particle
`minecraft:block/oak_planks`). `ChestRenderer` runs only for a client `BlockEntity`
(`BlockEntityRenderDispatcher.java:72-90`). On a chunk load the client creates block entities only
at the positions the packet lists (`LevelChunk.java:528-546`), and Polymer strips this mod's type
for a vanilla client (`ClientboundLevelChunkPacketDataMixin.java:18-28`, gated by
`ChestBlocks.java:101-104`). `LevelChunk.setBlockState` does create one for any change *into* a
chest state (`:326-348`). That covers a placement, a pairing or unpairing, waterlogging, a piston
landing, `/setblock`, and the client's own placement prediction. `Level.getBlockEntity` also
creates one on demand (`Level.java:694-703`), and a block event does that on arrival
(`BaseEntityBlock.java:23-27`). So a chest that loaded from disk shows only our display until
someone opens it. One that was placed or opened in view also has the oak chest under it on that
screen, until the chunk reloads there.

**The vanilla chest renderer cannot be hidden or retextured per position.** `getChestMaterial`
(`ChestRenderer.java:101-116`) picks the sheet from three things only: the block-entity class, the
copper weathering state and the date (Christmas). `ChestRenderState` carries nothing else
(`ChestRenderState.java:9-25`), and the renderer reads no block-entity NBT. A resource pack can
retexture `entity/chest/normal`, but only for every chest at once. That leaves two per-position
levers: cover the model, or keep the client from having a block entity there.

**A pack that draws vanilla's chest sheets transparent hides every client-side chest, and the
server can then draw all of them.** Every vanilla path that draws a chest draws `ChestModel`, whose
render type is `entityCutoutCull` (`ChestModel.java:26`); its pipeline discards texels under alpha
0.1 (`RenderPipelines.java:241-247`). That covers the block entity (`ChestRenderer.java:97`), the
chest item's special model (`ChestSpecialRenderer.java:61`), and the chest block model drawn outside
a block entity, in a minecart, a block display, a falling block or a moving piston
(`BuiltInBlockModels.java:87-88`). The sheets are `entity/chest/{normal,trapped,christmas}` with
`_left`/`_right` (`ChestSpecialRenderer.java:24-49`), Christmas on the client's 24 to 26 December
(`ChestRenderer.java:101-114`, `SpecialDates.java:10-12`); copper and ender chests have sheets of
their own and stay vanilla-drawn. A discarded fragment writes no depth, so the display entities
have nothing to cover and nothing to fight. On the user's client: Iris 1.11.4 maps
`entity_cutout_cull` to programs whose alpha test is one tenth, appended as a discard to the shader
pack's own fragment `main`, shadow pass included (Iris `IrisPipelines`, `ShaderKey`,
`CommonTransformer`); Sodium's cuboid fast path keeps the render type; the server pack sits above
every client pack (`DownloadedPackSource.java:57`, `Pack.Position.TOP`), so Fresh Animations' chest
sheets and texture-less `.jem` models fall under it. 26.3 has the same sheet paths, item
definitions and render type. One client mod bypasses the item model, Punchy; see Reference: client
compatibility. This is the "pack-wide retexture" the reference
table first rejected; it is chosen because the server can draw the real chests too (S9, S10).

**LootrPolymer sends every client vanilla's chest for each Lootr chest, with a client block entity
and a working lid, and draws nothing of its own.** Lootr turns every loot-table chest in the world
into `lootr:chest` or `lootr:trapped_chest` (`data/lootr/tags/block/convert/*.json`), always single
(`LootrChestBlock` placement and `updateShape`). LootrPolymer maps them to `minecraft:chest` and
`minecraft:trapped_chest` with their properties (`LootrPolymer.java:16-28`,
`ModBlocksMixin.java:25`, Vineflower lines) and swaps their block entity for a barrel's in chunk
data, so the client builds a plain chest block entity from the state. Its only display,
`LootrInventoryRenderer`, is an indicator overlay that `lootr_indicator: false` on live turns off.
`LootrChestBlockEntity extends ChestBlockEntity` but keeps a private opener counter, so the static
`ChestBlockEntity.getOpenCount` reads 0 there; that counter calls vanilla's `signalOpenCount`
(`LootrChestBlockEntity.java:75-76`), which is the one place every chest's count passes. So once
the sheets go transparent, every Lootr chest disappears unless the server draws it (S11).

**sensible-storage replaces the chest minecart a player makes, not the ones already in the
world.** `chest-minecarts.md` gives the chest + minecart recipe and the dispenser to this mod's
cart, which a vanilla client sees as a plain minecart under a display. It never converts or
re-presents a `minecraft:chest_minecart` entity. On live, Lootr swaps every loot-table chest
minecart for its own entity and LootrPolymer presents that as `minecraft:chest_minecart`; 11,341
of them are in the world. A vanilla chest minecart draws its cargo through the chest block model,
so on a pack client all of them would be empty minecarts; S15 draws their chests.

**A block event can be withheld, not remapped away.** Polymer rewrites the event's block id
(`ClientboundBlockEventPacketMixin.java:16-20`, via `PolymerBlockUtils.getPolymerBlock`,
`:145-147`). With the stand-in, that id becomes `minecraft:chest`. The client never looks at the id,
though: `Level.blockEvent` calls `getBlockState(pos).triggerEvent` (`Level.java:905-907`), which
creates the block entity and sets its lid target (`ChestBlockEntity.java:126-133`). The server
queues the event from `signalOpenCount` (`ChestBlockEntity.java:196-199`). That method is protected
and `PlankedTrappedChestBlockEntity` already overrides it, so it is the seam S4 uses.

**Right-click stops predicting a placement.** The client runs `ChestBlock.useWithoutItem`, which
returns `SUCCESS` without a server (`ChestBlock.java:261-274`), so it never predicts the held block
onto a planked chest. That removes the barrier-era ghost flicker (`planked-chests.md`, open
decisions). Polymer's default `isPolymerBlockInteraction` (`PolymerBlock.java:77-79`) then turns
the result into a server-sourced swing (`ServerGamePacketListenerImpMixin.java:162-170`). A client
that has already swung on its own probably shows that swing twice, so S2 turns it off.

**Depth needs almost no inflation, and the hover outline allows only a hair of it.** 26.2 renders
reversed-Z into a 32-bit float depth buffer in zero-to-one clip space (`MainTarget.java:87`,
`Projection.java:69-73`, `GlDevice.java:93-96`). One depth step at 64 blocks, the chest renderer's
range (`BlockEntityRenderer.java:34-40`), is about 1e-4 px. The block outline is drawn as lines
pulled toward the camera by distance/4096 (`ProjectionType.java:10`, `LayeringTransform.java:15-17`,
`RenderTypes.java:331-337`, `LevelRenderer.java:725-733`). That is 0.004 px at one block and
0.018 px at reach. A face that sits further than that outside the outline's edges hides them. A 1%
enlargement (about 0.14 px) is a thousand times what depth needs, and it hides the outline. The
body therefore gets 1/512 px (S3).

**A display lid cannot stay in step with a vanilla lid it does not drive.** The two curves are the
same. `LidTween` (kalic0re `common/display/LidTween.java:21`, `:32-35`) steps 0.1 a tick and eases
by `1-(1-x)^3`, exactly as `ChestLidController.java:10-22` and `ChestRenderer.java:92-94` do. But
once the event arrives, the vanilla lid runs open-loop on the client's clock. The display is a
server stream of one keyframe a tick, and each keyframe starts from wherever the *last rendered
frame* left the previous one (`Display.java:173-182`, `:367-377`). Four things each open a gap of
degrees: the frame rate, network jitter, a server tick that runs late, and the first frame being
sent at a different point in the tick from the event. One degree moves the latch top 0.26 px. The
numbers are in "Reference: lid timing". In short, 1% covers only the 0.6° chord error that is
always there. Everything else needs S5's measures or S6's withholding.

## How these stages get tested

On the dev server, through `scripts/devserver.sh rkc/mods/sensible-storage <cmd>` (port 25604,
rcon 25581), run with `difficulty peaceful`, `allow-flight=true` and
`pause-when-empty-seconds=0`. Bots join as 26.1 through ViaFabric and ViaBackwards in `run/mods`;
copy them from `rkc/mods/artifacts/run/mods` if they are missing. The rig goes at least 100 blocks
from spawn and is cleared to air first. This branch's runs use a worktree dev server on 25784,
rcon 25786 (password `dev`), with two standing jitter proxies on 25785 (20 ± 15 ms) and 25787
(150 ± 75 ms); S11 needs live's Lootr and LootrPolymer jars in `run/mods-server`, the server-only
mods directory `build.mod.gradle` adds to `runServer`.

The machine legs go in a new `scripts/loadtest/sensible-storage-chests.js`, written in S1 and grown
by each stage, on the pattern of `player-portals.js`:

- `lib/flyer.js` for `joinBot`, `fixResourcePackReplies`, `endClientTicks` and `echoSwings`;
- the sound capture and `calibrateSounds`/`soundName` (`player-portals.js:2146-2160`, `:2199-2214`);
- the `world_event` listener (`:2331`);
- `lib/devserver.js` for `countFailedPackets` and `countWrongMoves`, over the whole run.

Each leg is one "Done when" line, picked with `--stages` by stage number (`1,2,8,9,10,11,13`).
Raw packets come from `bot._client.on('packet')`. The packet names (`block_action`,
`entity_metadata`, `spawn_entity`, `bundle_delimiter`, `sound_effect`, `world_event`, `map_chunk`,
`add_resource_pack`) are minecraft-data's for 26.1; check each against a probe that is known to
fire before trusting a zero. Bots accept the pack unless declining is the point (`sscP`).

The visual legs are the user's, on a **vanilla** 26.2 client with the server pack accepted. The
dev `runClient` runs this jar, passes the handshake and gets the real block
(`client-mod-support.md`), so it never sees the stand-in; with the pack it does see S10's vanilla
chests. Brief the user on each leg before a
round, and leave the server up afterwards. The lid legs (S5, S6) also need:

- a capped frame rate (60 fps, then 144, in Video Settings);
- a slow server (`/tick rate 15`);
- jitter. Localhost has none, so a lid that matches there proves nothing about live. S5 builds the
  proxy for it.

A stage is done only when its "Done when" is observed, so a failure at stage N implicates stage N
and not the ones before it.

## S1 — Bot harness and the stand-in state — **done**

**Build.** `PlankedChestBlock.getPolymerBlockState` returns `Blocks.CHEST.withPropertiesOf(state)`
for a client without the handshake. `PlankedTrappedChestBlock` overrides it with
`Blocks.TRAPPED_CHEST`, so the client's pairing predictions split plain from trapped the way the
server's `is(this)` does (`ChestBlock.java:193-195`). Facing, type and waterlogged carry over one
for one, because both sides declare exactly `FACING, TYPE, WATERLOGGED` (`ChestBlock.java:381`). A
waterlogged planked chest now shows its water, which the constant barrier state hid. The mod-client
branch (`supportsAll`) is untouched, and so is the display.

Write `sensible-storage-chests.js` with the shape and state legs. The rig holds:

- a single oak chest and a same-wood double;
- a birch chest beside an oak one;
- a vanilla chest beside a planked one;
- a trapped oak chest beside a plain oak one;
- a waterlogged single.

**Done when.**
- A vanilla bot reads each rig block as `chest` or `trapped_chest` with the server's facing, type
  and waterlogged values, and the same-wood double as left/right. The mixed pairs read single on
  both sides.
- A bot's ray aimed over the middle of a single chest at y+0.95 misses it (`blockAtCursor`), and
  one at y+0.8 hits it.
- A bot walked onto a planked chest stands at y+0.875 on the server (`data get entity`), with
  `countWrongMoves` at 0.
- An item summoned above a planked chest settles at y+0.875. That is the server-side shape.
- In world, the outline hugs the chest, a double chest's outline is one box, and footsteps on top
  sound like wood (the barrier's are stone). The vanilla lid poking through ours on open is
  expected at this stage and belongs to S5 or S6.

### As built

`PlankedChestBlock.standIn()` (protected, `Blocks.CHEST`) names the stand-in and
`PlankedTrappedChestBlock` overrides it with `Blocks.TRAPPED_CHEST`; `getPolymerBlockState` returns
`standIn().withPropertiesOf(state)` for a client without the handshake, and the barrier constant is
gone. The item a client holds is still `Items.CHEST` (the `PolymerModelBlockItem` fallback), so its
placement prediction is the stand-in's own: right facing, same-wood pairs right, a mixed pair
predicted double until the ack. The hybrid client-item plan replaces that later.

`scripts/loadtest/sensible-storage-chests.js`: three 26.1 bots (`sscA` acts, `sscB` looks on,
`sscC` stands 40 blocks off, later watches packets) on a stone platform at y=100, x 985–1100,
z 985–1050, the rig along z=1000 at x 1000 (oak single), 1010/1011 (oak left/right), 1020/1021
(birch beside oak), 1030/1031 (vanilla beside oak), 1040/1041 (oak trapped beside oak), 1060 (oak
set into the floor, a wall beyond it), 1070 (waterlogged oak set into the floor, so its water
stays put), 1080 (warped, for the S3 look). `--stages 1,2,4` picks legs, `--lid-events in_step|none`
names the server's S4 routing. Built and run from a worktree dev server on 25784/25786 (rcon
password `dev`), `difficulty=peaceful`, `allow-flight=true`, `pause-when-empty-seconds=0`, Via jars
copied from `rkc/mods/artifacts/run/mods`:

```bash
scripts/devserver.sh rkc/mods/sensible-storage/.claude/worktrees/vanilla-chest-stand-in start
cd scripts/loadtest && node sensible-storage-chests.js \
    --mod-dir ../../rkc/mods/sensible-storage/.claude/worktrees/vanilla-chest-stand-in --stages 1,2,4
```

Observed (2026-10-17, 16/16 legs):

- state: every rig block read as set, `single: chest[waterlogged=false,type=single,facing=north]`,
  `doubleLeft … type=left`, `doubleRight … type=right`, `trapped: trapped_chest[…]`,
  `water: chest[waterlogged=true,…]`, the mixed pairs `type=single` both sides.
- ray: `y+0.95 hit nothing; y+0.8 hit chest at 1000 101 1000 (face 3)`. The rays are horizontal,
  cast with `bot.world.raycast` (the raycast `blockAtCursor` runs) from an explicit origin: an eye
  ray sloping down over the middle at y+0.95 would still land on the lid top behind the middle.
- stand: `server Pos 1060.500 100.875 1000.300; client 1060.500 100.875 1000.300` after walking
  down 0.125 onto the chest set into the floor; `moved wrongly` 0 over the run.
- item: `item Pos 1000.500 101.875 1000.500`.

The in-world leg (outline, one box for a double, wood footsteps) is the user's and not yet seen.

## S2 — Interaction, mining and break effects — **done**

**Build.** Four `PlankedChestBlock` overrides and one comment:

- `isPolymerBlockInteraction` returns false. Every client now predicts the chest's `SUCCESS`
  itself: the vanilla client through the stand-in, the mod client through the real block.
- `handleMiningOnServer` returns false. Client and server agree on the hardness (2.5,
  `Blocks.java:1254-1257`, copied by `ChestBlocks.java:107-114`) and on the tool (the axe tag from
  `AxeMineableProvider`), so the client mines natively in either game mode. Vanilla already leaves
  the breaker out of its own break event, so creative is heard once. Polymer's creative-break
  handling, which commit `6267a83` went back to, is then never involved.
- `getPolymerBreakEventBlockState` returns the wood's planks: `<planksNamespace>:<id>_planks`
  (`WoodType.java:142-144`). Polymer feeds it into the break event
  (`ClientboundLevelEventPacketMixin`), so onlookers see that wood's particles. The breaker's own particles, and the crack particles
  while digging, are client-predicted from the stand-in and stay oak.
- `forceLightUpdates` (`PlankedChestBlock.java:130-134`): try false, since the stand-in's light
  properties now match the real block's. Keep true if the display comes out darker.
- Reword the `isRedstoneConductor` comment (`ChestBlocks.java:110-113`). The full cube it describes
  is gone, but the explicit false is still what vanilla computes, so it stays.

**Done when.**
- A bot right-clicking a planked chest with cobblestone in hand gets a 27-slot window and places
  nothing; check positively that every neighbouring face is still air. The bot is sent at most one
  swing of itself (`echoSwings` count).
- A bot digging with an iron axe breaks the chest in the vanilla chest's time ±1 tick, the chest
  drops, and no mining-fatigue effect packet arrives.
- An onlooker gets one break event whose state decodes to `birch_planks` for a birch chest; the
  breaker gets none.
- Sounds per player, with the S1 rig:
  - the opener and an onlooker each hear one `block.chest.open` and one `block.chest.close` per
    open and close; a double chest also gives exactly one of each, from its right half
    (`ChestBlockEntity.java:109-122`);
  - a bot 40 blocks away hears none;
  - the placer hears no server `block.wood.place`, and an onlooker hears one.
- In world, right-click with a block in hand on a planked chest plays no place sound and shows no
  ghost block (the local prediction's sound would be audible even at zero ping); a creative break
  is heard once; an onlooker sees wood-coloured break particles.
- `countFailedPackets` is 0.

### As built

The four overrides on `PlankedChestBlock` as planned; `getPolymerBreakEventBlockState` resolves
`<planksNamespace>:<id>_planks` from the block registry on first use (a modded wood's planks may
register after the chest) and falls back to oak planks if absent. The `forceLightUpdates` override
is removed (Polymer's default false): the stand-in's light properties are the real block's. Whether
the display now reads darker is the user's in-world check; put the override back if so. The
`isRedstoneConductor` comment is reworded.

Observed (2026-10-17):

- use: `window minecraft:generic_9x3 with 27 container slots; neighbours air true,true,true,true;
  cobblestone left 8; self swings 0`.
- dig: `planked: 652 ms (client predicts 650 ms), broken true, drop "sensiblestorage:oak_chest";
  vanilla: 651 ms (predicts 650 ms), broken true; effect packets to the breaker: 0;
  block_break_speed -9999 (mining fatigue) updates: 0`. Both digs also carry one
  `block_break_speed` update at the player's real value: Polymer's `ServerPlayerGameModeMixin`
  re-sends it from the head of every `destroyAndAck`, vanilla chest included. The leg counts only
  the -9999 fatigue value.
- break event: `to the breaker: 0 (-); to the onlooker: 1 (birch_planks)`.
- sounds: `sscA: open 1, close 1; sscB: open 1, close 1; sscC: open 0, close 0`; double chest
  `window 54 container slots; sscA: open 1, close 1 at 1011.00 101.50 1000.50 … sscB: open 1,
  close 1 at 1011.00 101.50 1000.50` (the right half shifted to the pair's middle).
- place: `placed true (client reads chest[waterlogged=false,type=single,facing=south]);
  block.wood.place to the placer 0, to the onlooker 1`.
- `Failed to handle packet` 0, no ERROR line.

The in-world legs (no place sound or ghost block, creative break heard once, wood-coloured
particles) are the user's and not yet seen.

## S3 — Covering geometry — **done**

**Build.** A placed chest's base and lid, enlarged by ε = 1/512 px on every face, pivoting on the
same hinge. Enlarge the geometry; don't scale the display. A scale about the hinge leaves the
lid's back and bottom faces exactly on vanilla's, because both pass through the hinge line, and it
needs about three times the margin at the front for a given angle.

`ChestElementModelProvider` gains a placed-only geometry set. Its per-wood children still only bind
the texture, and `ChestModels` names the set for `ChestElementHolder`. Cart cargo keeps the exact
models, since a vanilla client sees a plain minecart with nothing under the cargo
(`chest-minecarts.md` S3). A double chest's halves enlarge only on their outer faces; the seam
face is already left out (`addGeometry`'s `hidden`). Any lid margin beyond ε belongs to S5.

**Done when.** Two checks in world, on a vanilla client:
- On a warped chest opened once so the client holds a block entity, with the lid shut, no oak
  shimmers through at 2, 16, 32 or 60 blocks, under both Fancy and Fabulous graphics.
- The outline shows all twelve edges at 1 to 4.5 blocks.

And one in a file listing: diffing `src/main/generated` before and after shows the cart models
byte-identical.

### As built

`ChestModels.Geometry { EXACT, PLACED }` names the two sets, `PLACED` with the model-id suffix
`_placed`; `baseModel`/`lidModel` take the geometry, `ChestVisual` is built with one,
`ChestElementHolder` asks for `PLACED` and `ChestCartDisplay` for `EXACT`.
`ChestElementModelProvider.COVERING_MARGIN = 1/512f`; `grow(element, hidden)` moves every face of
the base, lid and latch out by it except a half's seam face, and leaves the UVs alone, so the sheet
stretches by the margin. The placed set is generated for all three `ChestType`s and the exact set
for `SINGLE` only, since only cart cargo wants it; the exact left/right models nothing used are
gone.

Observed (2026-10-17), `sha1sum` over `src/main/generated` without `.cache/`: 3186 files before,
3412 after; 0 files changed, 452 removed (the exact `*_left_*`/`*_right_*` item definitions and
thin models), 678 added (`*_placed`). The 659 cart-model files (exact `SINGLE` item definitions,
thin models and `chest_base_single`/`chest_lid_single` geometry, plus every `*_minecart` file) are
byte-identical (`cmp` of the two sorted hash lists). `chest_lid_single_placed` runs from
`[0.998, 7.998, 7.998]` to `[15.002, 13.002, 22.002]`; `chest_base_left_placed` keeps its seam at
`x = 0.0` and `chest_lid_right_placed` at `x = 16.0`.

The two in-world legs (no oak shimmer through a shut warped chest at 2–60 blocks, Fancy and
Fabulous; twelve outline edges at 1–4.5 blocks) are the user's and not yet seen.

## S4 — Lid event routing — **done**

**Build.** `PlankedChestBlockEntity.signalOpenCount` stops queueing the vanilla block event.
Instead:

- A client with the handshake (real block, own renderer) is sent
  `ClientboundBlockEventPacket(pos, block, 1, count)` directly, within the 64-block radius
  `runBlockEvents` uses (`ServerLevel.java:1254-1270`).
- A vanilla client hears from the holder, whose watchers are exactly the vanilla clients
  (`ChestElementHolder.java:65-68`). On a tick where its lid starts or reverses, the holder sends
  one `ClientboundBundlePacket` with that tick's lid metadata and the event. A single switch
  (`IN_STEP` / `NONE`) leaves the event out for S6.

The trapped chest's neighbour updates are unchanged: `PlankedTrappedChestBlockEntity.java:31-41`
calls super first and keeps its redstone half. The server-side `triggerEvent` no longer runs. That
only fed the server's lid controller, which is never ticked (`ChestBlock.java:333-335`).

The bundle is the point of this stage. The holder ticks in `tickChunk`
(`polymer-virtual-entity/.../mixin/block/ServerWorldMixin.java:15-24`), and the vanilla event goes
out later in the same tick, from `runBlockEvents` (`ServerLevel.java:400-404`). If a client tick
boundary falls between the two arrivals, the lids start a tick apart and stay that way for the
whole swing, 24° at first. A bundle is handled in one go (`ClientPacketListener.java:2451-2457`).
The element sends its dirty data unbundled during its own tick (`GenericEntityElement.java:156-162`,
`:192-198`). So on a transition tick the holder has to build that `ClientboundSetEntityDataPacket`
itself and put it in the bundle. Polymer's `SafeBundler` is internal, so use
`new ClientboundBundlePacket(...)` directly.

**Done when.** First check that a bundle reaches a 26.1 bot intact through ViaBackwards. If Via
splits it, assert that the packets arrive in the same read instead. Then:
- With `IN_STEP`, a watching vanilla bot sees exactly one `block_action` (action 1, value = count)
  per open and per close, inside the same bundle as a lid `entity_metadata`.
- With `NONE` it sees none.
- A second opener (count 1 → 2 → 1) sends events but no lid motion.
- On the dev `runClient` (handshake), the user sees the real chest's lid open and close as before.

### As built

`display/LidEvents`: `Routing { IN_STEP, NONE }`, `packet(pos, block, count)` (block event
`ChestBlock.EVENT_SET_OPEN_COUNT`), and `sendToModClients(level, pos, block, count)`, which sends
it to every player with the handshake within 64 blocks (`distanceToSqr` against the block position,
as `PlayerList.broadcast` measures). `PlankedChestBlockEntity.signalOpenCount` calls that instead
of `super`, so vanilla's broadcast is never queued; `PlankedTrappedChestBlockEntity` still calls
`super` first and keeps its redstone updates.

`ChestElementHolder.onTick` keeps `shownCount` and `shownOpen`. On every count change it sends the
event to its watchers, which are the vanilla clients; on the tick the lid's direction flips it takes
the lid element's dirty entries (`getDirtyEntries`, so the element's own tick has nothing left to
send) and sends `new ClientboundBundlePacket(List.of(ClientboundSetEntityDataPacket, event))`. A
count change with no flip (1 → 2, 2 → 1) sends the bare event, as vanilla does; that is the reading
of "sends events but no lid motion" together with "exactly one per open and per close".

The switch is `LidEvents.ROUTING`, `IN_STEP` unless the JVM property
`sensiblestorage.lid_events` says `NONE`; the plan's constant became a property so the NONE leg
could run on the same build (`JAVA_TOOL_OPTIONS=-Dsensiblestorage.lid_events=NONE
scripts/devserver.sh <worktree> start`, then `--lid-events none`). S6, if built, flips the default.

Observed (2026-10-17), watcher `sscC`:

- bundle: `4 bundle_delimiter packets reached the 26.1 bot through ViaBackwards; 2 block_action(s)
  for the chest, bundled with a lid entity_metadata: true,true`. ViaBackwards passes bundles whole.
- IN_STEP: `block_action: (1,1) block #201 bundle 33 seq 206, (1,0) block #201 bundle 34 seq 219;
  lid metadata 20 packet(s), angles 24.4 43.9 59.1 70.6 78.8 84.2 87.6 89.3 89.9 90.0 89.9 89.3
  87.6 84.2 78.7 70.6 59.1 43.9 24.4 0.0; lid keyframes in the events' bundles: 1,1` (#201 is the
  client's `minecraft:chest`; the angles are S5's table, read from `right_rotation`).
- second opener: `block_action values in order: 1,2,1,0 (wanted 1,2,1,0); lid metadata from A's
  open settling to A's close: 0`.
- NONE: `block_action: 0; lid metadata 20 packet(s), angles 24.4 … 90.0 … 0.0`; second opener
  `values: none (wanted none)`; the bundle leg skips, having no event to judge.

The `runClient` leg is the user's and not yet seen.

## S5 — Vanilla lid kept, display lid in step — **skipped**

S5 kept the vanilla lid swinging (its block event bundled with the display lid's keyframe) and hid
the gap between the two lids with a lid margin. On a vanilla 26.2 client (2026-10-17) the user saw
60 and 144 fps through a 20 ± 15 ms proxy pass at 0.16 px, and `/tick rate 15` pass; the shut
outline lose its top edges at 0.16 px; a slight flicker at 0 px even at low ping; and through a
150 ± 75 ms proxy the vanilla lid visibly leave the display lid whatever the margin. That is the
jitter row of "Reference: lid timing", which no margin covers, so S6 is built instead.

What would revive it: S6's scrub window proving too visible, or a way to time the vanilla lid from
the server. The S5 state, with the five margin variants (`sensiblestorage.lid_margin_px`), the
`IN_STEP` route (`sensiblestorage.lid_events`) and interpolation 1, is
`/tmp/claude-1000/chest-stand-in/s5c.patch` against `01ee121`; S6's `LidEvents` and
`StandInScrubs` come out before it goes back in. The pairing fix (both halves drawn at
`easeOut(max(own, partner))`) started here and stays in S6.

### As built

The machine parts, on the same branch, reverted by S6 except the pairing fix. No ping-pong lid,
which waited on the 60 fps leg.

- `ChestElementHolder.LID_INTERPOLATION = 1`, set on the placed lid at construction;
  `ItemDisplays.DEFAULT_INTERPOLATION` stays 2 for everything else and the cart keeps its own
  durations. `LidTween` is unchanged: one keyframe a tick.
- The lid margin, in model px on top of `COVERING_MARGIN`: the placed lid's top and front faces
  (`extend`), every face of its latch but a half's seam (`grow`), and the placed base's front face,
  so the base's front stays flush with the lid's. The base's other faces and every seam face keep
  S3's ε. 0.16 px is 1% of a block; the plan's 0.14 is 1% of the 14-px lid.
- `ChestModels.LID_MARGINS = {0.16, 0.08, 0.04, 0.02, 0}`. Datagen writes the placed base and lid
  once per margin, ids suffixed `_m16` … `_m00` (`warped_chest_lid_placed_m04`, geometry
  `chest_lid_single_placed_m04`), all in the one pack. The server takes one at boot from the JVM
  property `sensiblestorage.lid_margin_px` (`ChestModels.LID_MARGIN`) and logs
  `Placed chest lid margin: <m> px`; a value with no models logs a warning and takes the default.
  A variant is a restart, not a rebuild. `DEFAULT_LID_MARGIN` is 0 by the user's choice
  (2026-10-17), so the placed set is S3's ε alone; the other four stay generated until the
  re-test at 0 passes.
- A double chest's halves draw one lid angle. The vanilla client draws each half at the larger
  raw openness of the two lids, eased after (`ChestRenderer.java:76-83`, `:92-94`;
  `ChestBlock.opennessCombiner`, `ChestBlock.java:311-325`; pairing per
  `DoubleBlockCombiner.java:24-54`, no blocked check since the renderer passes
  `ignoreBeingBlocked`). A chest placed beside an open single one pairs with it but gets no event
  of its own (the opener holds the single container), so its vanilla lid swings at the open
  one's angle while the display lid stayed shut. Now `ChestElementHolder.onTick` steps both
  halves once per server tick (`steppedTick` against `getTickCount()`, whichever holder ticks
  first) and poses both at `easeOut(max(own, partner))`; the partner is found as the combiner
  pairs, through `BlockBoundAttachment.get` on `getChunkNow`. On a tick a half's lid sets off or
  turns back, each watcher gets one bundle with the changed halves' events and both lid
  keyframes. `startWatching` first poses the lid at the pair's current angle without stepping,
  taking a partner that still reads single (the new half's holder starts watching inside
  `setBlockState`, before the neighbour's shape update pairs it), and clears the dirty entries
  for a first watcher, whose spawn already carries them. So a half placed beside an open chest
  spawns at its angle and moves with it from the next tick. `ChestVisual` gains `advanceLid` and
  `lidProgress`; the cart still uses `tweenOpenness`.
- `scripts/loadtest/lib/jitter-proxy.js`: a TCP forwarder, Node only. Server-to-client chunks
  are due at now + base ± uniform jitter, never before the chunk ahead (one FIFO timer queue per
  connection), client-to-server passes straight through; connections are logged. It listens
  dual-stack on `::` (IPv4 arrives as `::ffff:127.0.0.1`) and dials the target's literal address.
  CLI `node lib/jitter-proxy.js --listen 25785 --target 127.0.0.1:25784 --base-ms 20 --jitter-ms
  15`, or `start({...})` in-process, which the harness uses on a free port.
- Harness `--stages 5`: a fourth bot `sscD` joins through the proxy over `::1` beside the direct
  watcher `sscC`; one open and close at 20 TPS, then under `/tick rate 15` (restored after); each
  watcher must read 10 angles up and 10 down within ±0.1, and the direct watcher's median arrival
  spacing must sit within a quarter tick of the tick. The leg names the lid model the server sent.
- Harness `--stages 5b`, both watchers, on `single` and the spot east of it: (1) B placed about 3
  ticks into A's opening (timed from A's click) and (2) once A is open: the placed lid's spawn
  carries A's last angle, each later frame pairs with A's in arrival order, A's close event
  shares its bundle with both lids' 89.9, and the placed half gets no event; (3) the pair opened
  and closed from the placed half: both run the 20 angles in step, each open and close one
  bundle with both events and both keyframes; (4) the placed half broken while A holds the other
  open by itself: its displays go, A's lid gets no keyframe until A's close, then 10 down. On the
  previous holder (2026-10-17) legs 1–3 fail (the placed lid never turns; separate bundles) and 4
  passes, so 4 guards a regression and does not show the bug.

Regenerated (2026-10-17): 6124 files, was 3412. The 678 `*_placed` files became 5 × 678
`*_placed_mNN`, no other file changed, and the 659 cart-model files are byte-identical to before
S3. In `chest_*_single_placed_mNN` every other face sits at S3's ε (base
`[0.998, -0.002, 0.998]–[15.002, 10.002, ·]`, lid `[0.998, 7.998, 7.998]–[15.002, ·, ·]`); the
base's front at z 15 meets the shut lid's at hinge-relative z 22:

| Margin | Base front z | Lid top y | Lid front z | Latch |
| --- | --- | --- | --- | --- |
| 0.16 | 15.162 | 13.162 | 22.162 | `[6.838, 5.838, 21.838]–[9.162, 10.162, 23.162]` |
| 0.08 | 15.082 | 13.082 | 22.082 | `[6.918, 5.918, 21.918]–[9.082, 10.082, 23.082]` |
| 0.04 | 15.042 | 13.042 | 22.042 | `[6.958, 5.958, 21.958]–[9.042, 10.042, 23.042]` |
| 0.02 | 15.022 | 13.022 | 22.022 | `[6.978, 5.978, 21.978]–[9.022, 10.022, 23.022]` |
| 0 | 15.002 | 13.002 | 22.002 | `[6.998, 5.998, 21.998]–[9.002, 10.002, 23.002]` |

The left and right halves keep their seam at `x = 0.0` / `16.0` in base, lid and latch.

Observed (2026-10-17), bots:

- proxy: `direct sscC: opening 10 frame(s) 24.4 43.9 59.1 70.6 78.8 84.2 87.6 89.3 89.9 90.0,
  closing 10 frame(s) 89.9 89.3 87.6 84.2 78.7 70.6 59.1 43.9 24.4 0.0; arrival spacing
  min/median/max opening 49/50/51 ms, closing 49/50/50 ms; proxied sscD: [same angles]; arrival
  spacing opening 34/44/71 ms, closing 24/55/72 ms` through `proxy [::1]:… -> 127.0.0.1:25784,
  20 ± 15 ms (5–35 ms)`, lid model `oak_chest_lid_placed_m16`.
- `/tick rate 15`: both watchers the same 10 + 10 angles; `direct … opening 65/67/67 ms, closing
  66/67/67 ms (tick 66.7 ms); proxied … opening 38/67/94 ms, closing 52/65/89 ms`.
- `lid_margin_px=0.04`: both legs pass with lid model `oak_chest_lid_placed_m04`;
  `lid_margin_px=0.05` logs `No placed chest lid models at lid_margin_px=0.05; using 0.16 px`.
- The standalone proxy on 25785: status pings over `::1`, `127.0.0.1` and `localhost` answer, a
  26.1 bot logs in over `::1` and over `127.0.0.1`, and the pack URL a client is given
  (`http://localhost:25785/eu.pb4.polymer.autohost/…`) downloads through it over both, hash intact.

Observed (2026-10-17), bots at margin 0 with the pairing fix: S5 both legs pass with lid model
`oak_chest_lid_placed_m00` (direct spacing 49/50/51 and 49/50/51 ms at 20 TPS, 66/67/67 and
66/67/67 ms at 15; proxied 30/51/69, 38/47/65 and 49/67/78, 48/68/91 ms). S5b, on both watchers
over two runs: (1) `Y lid … spawned after X's 59.1, Y 59.1 70.6 78.8 84.2 87.6 89.3 89.9 90.0 89.9
… 0.0; in step true, widest pair gap 0 ms; X close event in a bundle with X's and Y's 89.9:
true,true; Y events 0`; (2) `spawned after X's 90.0, Y 90.0 89.9 … 0.0`, the same checks; (3)
`window 54 container slots; X and Y 24.4 … 90.0 … 0.0; in step true; events X 1,0, Y 1,0; each
open and close one bundle with both events and both keyframes: true`; (4) `X keyframes between the
break and the close: 0; after the close 89.9 … 0.0; Y displays … removed: true`. Stages 1, 2, 4
pass in the same run.

Observed (2026-10-17), the user on a vanilla 26.2 client at 0.16 px, before the base-front change:

- `/tick rate 15`, direct: pass, no visible lag, against the timing reference's prediction.
- Shut outline: the top four edges are hidden. Acceptable, but a smaller margin is to be tried.
- 60 and 144 fps through the dual-stack proxy: pass, no oak sliver.
- A chest placed beside one mid-swing showed two lids on the new half, one shut (the display)
  and one at the open chest's angle (the vanilla lid); fixed as above.

Then the user chose margin 0, saw it flicker slightly even at low ping, tried a 150 ± 75 ms proxy,
and S5 was skipped (above).

## S6 — Vanilla lid withheld, client block entities scrubbed — **skipped**

S6 withheld the lid event from vanilla clients and resent a chunk to each vanilla viewer after
every stand-in change, so that the client block entity, the only thing that draws vanilla's lid,
was gone between changes. On the user's client (2026-10-17) a chest just placed kept the oak lid
inside the display's until a relog. SubtleEffects 1.15.1 asks `Level.getBlockEntity` for every
block its `ClientLevel.animateTick` hook samples and, once it holds a ticker at a chest, again every
client tick while the block is any `AbstractChestBlock`; `getBlockEntity` builds a missing block
entity (`LevelChunk.java:385-391`), so a scrubbed one is back within a tick. No server-side scrub
can win against a client that rebuilds the block entity itself. The user chose instead to draw
vanilla's chest sheets transparent in the pack (S9) and have the server draw vanilla's chests too
(S10); any client block entity then draws nothing, and S8 takes S6 out.

What would revive it: a reason to keep vanilla's chest sheets intact for pack clients, together
with a scrub that survives a client rebuilding the block entity. The S6 state, with
`StandInScrubs`, `LidEvents` (no routing), S3's placed geometry, `sensiblestorage.lid_interpolation`
and `sensiblestorage.debug_scrubs`, is `/tmp/claude-1000/chest-stand-in/s6b.patch` against
`b801c34`, its harness `s6b-harness.patch`; S8 through S13 come out before it goes back in.

### As built

On the same branch, reverted by S8 except the pairing fix and the lid interpolation of 2.

- No vanilla client gets a chest event. `LidEvents` keeps only `packet` and `sendToModClients`
  (S4's direct route to clients with the handshake); `Routing`, `IN_STEP` and the
  `sensiblestorage.lid_events` property are gone, and `ChestElementHolder` sends no event.
- The pairing fix from S5 stays: a pair's halves step once per server tick, whichever holder ticks
  first, and both draw `easeOut(max(own, partner))`, so a double's two display lids move as one and
  a half placed beside an open chest spawns at its angle.
- Margin: S3's ε only. The five variants and `sensiblestorage.lid_margin_px` are gone, ids are
  `_placed` again and the base front is back at ε. Regenerated: 3412 files, byte-identical to the S3
  tree, the 659 cart-model files byte-identical to before S3.
- `ChestElementHolder.LID_INTERPOLATION` comes from `sensiblestorage.lid_interpolation`, 1 to 3,
  default `ItemDisplays.DEFAULT_INTERPOLATION` (2); another value logs a warning and takes 2.
- `block/StandInScrubs`. Two triggers, each scheduling a resend per viewer, chunk and dimension
  `DELAY` = 10 ticks after the last change there:
  - `PlankedChestBlock.onPlace`, which vanilla runs on every state change: placement, pairing and
    unpairing, waterlogging, a piston landing. Every viewer tracking the chunk without the
    handshake (`PlayerLookup.tracking`).
  - `UseBlockCallback`, for a viewer whose client item, as Polymer builds it in that viewer's
    context (`PolymerItemUtils.getPolymerItemStack`), places a `ChestBlock`: the chunk of the
    clicked block and the one beside it, and the chunk of any planked chest next to either. That
    catches a pairing the client predicts and the server never makes (next to another wood, or a
    vanilla chest), whose block entity no server change would scrub.
  - `END_SERVER_TICK` resends what has fallen due, if the viewer still tracks the chunk and it is
    not already pending; `DISCONNECT` drops a viewer's.
- The resend is the packet vanilla's `PlayerChunkSender.sendChunk` sends, built inside
  `PacketContext.supplyWithContext(viewer.connection, …)` exactly as Polymer's
  `PlayerChunkSenderMixin` wraps it, so the states are remapped and the planked block entities left
  out. It goes straight to the connection, not through `markChunkPendingToSend`, which would leave
  the chunk pending and its entities untracked until the next send.
- The client item: kalic0re 0.30.1's `PolymerModelBlockItem` gives a pack client RKC Polymer's
  stand-in policy choice, and the chest stand-in has more than one placement state, so that is the
  non-placing firework star; a client without the pack, or any client on stock Polymer, gets the
  `Items.CHEST` fallback and predicts. Since 2026-10-17 (the user's decision) every
  sensible-storage block item is a `block/UnpinnedBlockItem`, which gives a pack client the
  firework star whenever the policy is available, so bookshelves and crafting tables no longer pin
  their note-block stand-ins; on stock Polymer it keeps the vanilla fallbacks, since a star there
  would leave the placer silent. The worktree dev server runs RKC Polymer `rkc/26.2`: every module
  of its polymer-bundled, versioned 0.17.5.1+26.2, sits in `run/mods` as its own jar, because
  Fabric Loader prefers Loom's stock 0.17.5 root jars over newer nested ones.
- Not covered: a break the server refuses and resyncs (a claim, say) rebuilds the client block
  entity with no server change; it lasts until the next change in that chunk.

Observed (2026-10-17), bots direct and through 20 ± 15 ms and 150 ± 75 ms proxies, all passing on
stock Polymer but the pack-placer leg, which skips there:

- No events: `S2 sound legs, bots A/B/C: 0/0/0`; an open and close of the single and the double,
  `0 block_action, 20 lid frames on the single` for each watcher.
- Placement: `1 map_chunk (15483 B, 451 ms after the block update, 0 block entities, planked 0)`
  direct, 461 ms and 361 ms through the proxies, the placer the same. Pairing: `1 map_chunk (15487
  B, …)` at 450, 434 and 460 ms, the client reading `type=left` + `type=right`.
- Wall: `10 setblocks over 1518 ms`, each viewer `10 block update packet(s), 1 map_chunk (15483 B,
  ~450 ms after the last update)`.
- Vanilla chest in a resent chunk: `1 map_chunk, vanilla chest block entity true, a planked one
  false; vanilla block events 1,1,1,1,1,1,0,0` (vanilla re-sends the count while open).
- No-pack placer, oak beside birch across a chunk line: `client item chest; … type=single +
  type=single; map_chunk to the placer: target chunk 1, birch chunk 1; to direct sscC: target 1,
  birch 0`.
- Pack placer, with RKC Polymer's core: `client item firework_star; map_chunk to the placer:
  target chunk 1, birch chunk 0`; S2's place leg there hears the server's place sound at the
  placer, which predicts nothing.
- S5b on all three watchers: placed mid-opening `spawned after X's 59.1`, once open `spawned after
  X's 90.0`, opened from the placed half, broken while open, all in step, `block events 0`.
- Interpolation, 100 lid keyframes over 5 opens and closes, expected stalled ticks at 1/2/3 and
  dropped keyframes: direct `0.1–0.3/0/0`, `0.1–0.3`; 20 ± 15 ms `8.4/0/0`, `9`; 150 ± 75 ms
  `23–27/9/0.5–0.9`, `36–38`.

Observed (2026-10-17), the user on a vanilla 26.2 client (the Kalic0 Modpack profile) through the
150 ± 75 ms proxy: the lid looks right at interpolation 2, which stays the default; a chest just
placed kept the vanilla oak lid inside the display's until the user relogged.

The cause is on that client. Bots after a placement, the scrub, two opens and closes see only
identical-state block updates for the chest (`block_change chest[…] -> chest[…]` to the opener
after each click), on stock and RKC Polymer alike, and an identical state returns before touching
block entities (`LevelChunk.java:281-283`). SubtleEffects 1.15.1 on the client, with its chest
options on (`openingChestsSpawnsBubbles = true`, `chestsOpenRandomlyUnderwaterFrequency = 5` in the
profile), calls `ChestBlockEntityTicker.trySpawn` from its `ClientLevel.animateTick` hook for every
block the random animate tick samples, and `trySpawn` calls `Level.getBlockEntity`, which builds a
missing block entity (`LevelChunk.java:385-391`). Once it has a ticker at a chest, `positionedTick`
does the same every client tick while the block is any `AbstractChestBlock`, so a scrubbed block
entity is back within a tick; the scrub's own section rebuild then draws it, and only a relog
(which clears the tickers) ends it. A block entity built that way is drawn only after its section
is next rebuilt, which is why a chest nobody touched looks right. Vanilla's double-chest combiner
does the same for a rendered half's partner (`BlockEntityType.java:35-38` through
`DoubleBlockCombiner.java:47`). `sensiblestorage.debug_scrubs=true` logs every scrub scheduled,
sent or skipped. How S6 answers a client that rebuilds the block entity itself is not decided.

## S8 — Rip out the scrub, the lid routing and the covering margin — **in progress**

**Build.** Everything S9 makes unnecessary comes out; S1, S2 and the pairing combiner stay.

- S6: `block/StandInScrubs`, its `PlankedChestBlock.onPlace` trigger, its registration and the
  `sensiblestorage.debug_scrubs` property.
- S4: `display/LidEvents` and the `PlankedChestBlockEntity.signalOpenCount` override, so vanilla's
  own block event broadcast is back. A mod client needs it to swing its real lid, and a vanilla
  client's stand-in lid it moves draws nothing under S9.
- S3: `ChestModels.Geometry`, `ChestElementModelProvider.COVERING_MARGIN`, `grow` and the
  `_placed` models. Placed chests and cart cargo share the exact models again, and `ChestVisual`
  and `ChestCartDisplay` lose their geometry argument. Nothing visible remains under a display
  for a pack client (first finding), so no margin has anything to cover. The outline sits on the
  exact chest bounds, as on any vanilla chest.
- `sensiblestorage.lid_interpolation`: the lid keeps `ItemDisplays.DEFAULT_INTERPOLATION`, 2 ticks,
  which the user accepted through the 150 ± 75 ms proxy (S6).
- Harness: the S4 and S6 legs and S5's arrival-gap leg go. The S5 keyframe legs and the S5b
  pairing legs stay as this stage's, without their "no lid event" condition.

**Done when.**
- `src/` names none of `StandInScrubs`, `LidEvents`, `_placed`, `lid_interpolation` or
  `debug_scrubs`.
- Regenerated `src/main/generated` holds every planked chest file byte-identical to the tree before
  S3.
- Bots:
  - A watcher gets vanilla's `block_action` for a planked chest, count 1 then 0 (repeats while open
    collapsed), while its display lid runs 10 frames up and 10 down.
  - A second opener gives counts 1,2,1,0 and no lid motion.
  - Lid keyframes come one a tick at 20 and 15 TPS, direct and through both proxies.
  - The four pairing legs pass.
- In world, on the dev `runClient` (handshake), a planked chest's real lid opens and closes.

### As built so far

As planned, plus `ChestVisual.advanceLid` and `applyItems` now report whether anything changed
(S13 needs it). `src/main/generated`: 3216 files after regenerating, against 3186 before S3; every
planked file and every cart file is byte-identical to that tree. The differences are all S9's:
`vanilla_chest_base.json` and `vanilla_chest_lid.json` changed, `textures/block/chest/vanilla.png`
removed, and 32 vanilla-chest files added.

Observed (2026-10-17), on stock Polymer 0.17.5, direct and through both proxies:

- events: `block_action values 1,0 (repeats while open collapsed); lid metadata 20 packet(s)`.
- second opener: `block_action values 1,2,1,0; lid metadata from A's open settling to A's close: 0`.
- keyframes: lid model `sensiblestorage:oak_chest_lid`, interpolation 2 ticks. Direct arrival
  spacing was 49/50/51 ms at 20 TPS and 66/67/67 ms at 15.
- pairing: placed mid-opening, `spawned after X's 59.1`; placed once open, `spawned after X's 90.0`.
  Opened from the placed half, both ran 20 angles in step (widest pair gap 0–1 ms). Broken while
  open, `X keyframes between the break and the close: 0`.

The `runClient` leg is the user's and not yet seen.

## S9 — Transparent vanilla chest sheets in the pack — **done**

**Build.** `display/VanillaChestPack` hooks `RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT`
(`PolymerResourcePackUtils.java:38`), which runs after every mod's assets are copied
(`ResourcePackCreator.java:153-171`).

- For each of the nine sheets (`normal`, `trapped`, `christmas`, each single, `_left` and
  `_right`), `getDataOrSource` reads the pack's copy if a mod put one there, else the client jar
  Polymer caches (`DefaultRPBuilder.java:335-341`, `:384-389`).
- It copies that sheet to `sensiblestorage:textures/block/chest/vanilla{,_trapped,_christmas}`
  with the same suffix, then puts a fully transparent PNG of the same size in its place.
- It adds `assets/minecraft/items/chest.json` and `trapped_chest.json` from the jar's
  `sensiblestorage_pack/` directory. Datagen writes that directory outside `assets/`, so a client
  running this mod without the server pack keeps vanilla's chests.
- All or nothing: a sheet that cannot be copied is never blanked, and `blanked()` gates S10.

Datagen gives `vanilla` and `vanilla_trapped` every part a wood gets: base and lid per `ChestType`,
and a whole model. Each item definition selects on `minecraft:local_time`, pattern `MM-dd`,
switching to the `vanilla_christmas` model on 12-24, 12-25 and 12-26, as vanilla's own chest item
does. The two `minecraft:` item definitions use the same select over the whole models.
`ChestTextureProvider` stops copying vanilla's `normal.png` into the jar. The cart's `vanilla`
cargo reads the pack copy instead and gains the Christmas switch. The vendored `chest.png` goes.

**Done when.**
- Every pack bot is pushed the pack whose SHA-1 is `run/polymer/resource_pack.zip`'s.
- In that zip:
  - the nine sheets are 64×64, the client jar's size, with every alpha 0;
  - each copy is byte-identical to the client jar's sheet;
  - both item definitions and the vanilla display parts are the Christmas select, and every model
    and sprite they name is in the zip.
- In world, chest and trapped chest items draw as vanilla's: in the inventory, in an item frame, on
  the ground, and held in third person. They also draw that way held in first person once Punchy's
  `disableChestModels` is on.

### As built

As planned. The hook writes the sheets with `ImageIO`, type ARGB, all zero.

Observed (2026-10-17), bots:

- served: pack `d4c94a5134840749dff2e753c43b621c3c9f740d`, pushed to sscA, sscB and sscC.
- sheets: `normal 64x64 max alpha 0` and likewise for all nine, PNG colour type 6, every raw byte
  0.
- copies: `vanilla same; vanilla_left same; … vanilla_christmas_right same`.
- item definitions: `minecraft:chest: minecraft:select minecraft:local_time MM-dd on
  12-24,12-25,12-26 -> sensiblestorage:block/vanilla_christmas_chest_whole
  (sensiblestorage:block/chest/vanilla_christmas), else sensiblestorage:block/vanilla_chest_whole
  (sensiblestorage:block/chest/vanilla)`. The trapped chest, `vanilla_chest_left_lid` and
  `vanilla_trapped_chest_base` read the same way.

In world (2026-10-17), the user, on a vanilla 26.2 client with the pack, shaders on and off: chest
items look right.

## S10 — Vanilla chests drawn by the server — **done**

**Build.** `display/VanillaChestDisplays` binds a holder to `Blocks.CHEST` and
`Blocks.TRAPPED_CHEST` through Polymer's overlay (`BlockWithElementHolder.registerOverlay`,
`BlockWithElementHolder.java:43-50`). Polymer then creates and binds it as for its own blocks: at
chunk load and on a state change into the block (`WorldChunkMixin.java:54-109`).

- The binding happens at `SERVER_STARTING`, after every mod has registered, before any chunk loads.
  The overlay is one slot that overwrites silently (`BlockMixin.java:25-28`), so another mod's
  overlay already there is kept, with a warning.
- `ChestElementHolder` takes a chest id and an `Audience` instead of a `PlankedChestBlock`:
  - `STAND_IN` for a planked chest: every client without the handshake, as before.
  - `PACK` for a vanilla chest: `VanillaChestPack.blanked()` and Polymer's
    `hasMainPack(player)`, handshake or not. A mod client holding the pack also draws vanilla's
    sheets transparent.
- A vanilla chest draws `vanilla_chest` and a trapped chest `vanilla_trapped_chest`. The pairing
  combiner is shared.
- `mixin/ChestBlockEntityMixin` hands each `signalOpenCount` count to the chest's holder
  (`ChestLids.openCountChanged`). That count is the one vanilla's own block event carries, for any
  `ChestBlockEntity`.

A client without the pack is left alone: no display, vanilla's own chest. Polymer records pack
status per connection from the configuration-phase response (`ServerCommonPacketListenerImplMixin`
in polymer-common), so it is known before the first chunk.

**Done when.**
- Bots:
  - Each pack watcher sees a base and a lid at every vanilla chest with the vanilla models: single,
    trapped and both halves of a double.
  - The no-pack bot sees none there, and still sees the planked chest beside it drawn.
  - Pack and no-pack bots alike get vanilla's `block_action`, 1 then 0.
  - The lid keyframe legs pass for a vanilla chest at 20 and 15 TPS, direct and through both
    proxies.
  - The four pairing legs pass with vanilla chests.
- In world, on a vanilla 26.2 client with the pack:
  - No vanilla lid or body shows anywhere.
  - Vanilla chests, trapped chests and doubles look like vanilla's, and they open and close.

### As built

The holder also drops polling: Polymer never ticks it (S13). Observed (2026-10-17), bots:

- audience: at `1030 101 1000` each of sscC, sscD and sscE saw `sensiblestorage:vanilla_chest_base,
  sensiblestorage:vanilla_chest_lid`. The trapped chest showed `vanilla_trapped_chest_*` and the
  double `vanilla_chest_left_*` and `vanilla_chest_right_*`. `sscP (no pack)` saw 0 at each, and 2
  at the planked chest beside.
- events: `sscC 1,0; sscP 1,0`.
- keyframes: lid model `sensiblestorage:vanilla_chest_lid`, interpolation 2 ticks, the 20 angles
  at 20 and 15 TPS on all three watchers. Direct spacing was 49/50/51 ms and 66/67/67 ms.
- pairing: placed mid-opening, `spawned after X's 59.1 … in step true, widest pair gap 0 ms; block
  events X 1,0, Y -`; placed once open, `spawned after X's 90.0`. Opened from the placed half, `54
  container slots`, in step, events `X 1,0, Y 1,0`. Broken while open, `X keyframes between the
  break and the close: 0`.

In world (2026-10-17), the user, on a vanilla 26.2 client with the pack, shaders on and off: the
rig's planked, vanilla, trapped and double chests look and work right.

## S11 — Lootr's chests — **done**

**Build.** `VanillaChestDisplays` also binds `lootr:chest` and `lootr:trapped_chest`. It finds them
by id, since Lootr is never a dependency, and replaces LootrPolymer's indicator overlay there,
which is off on live (`lootr_polymer.json`). Lootr's opener counter calls vanilla's
`signalOpenCount`, so the holder gets its count from S10's mixin unchanged. Lootr chests are always
single.

Not covered here:
- Lootr's copper chests: they map to vanilla copper chests, whose sheets stay.
- Lootr's minecart, shown as `minecraft:chest_minecart`: S15. Its item frame, shown the same way by
  a LootrPolymer bug: S12.
- The per-player opened state: a vanilla client never saw it.

**Done when.**
- With live's Lootr 1.24.39.121 and LootrPolymer 1.2 in `run/mods-server`:
  - A client reads a Lootr chest and trapped chest as `chest` and `trapped_chest`.
  - Each pack watcher sees a base and a lid there with the vanilla models; the no-pack bot sees
    none.
  - Opening one gives its own 27 slots, every bot `block_action` 1 then 0, and the display lid 10
    frames up and 10 down.
- In world, a Lootr chest looks and opens like a vanilla chest.

### As built

As planned. The server logs `Replacing lootr:chest's element holder with a chest display` and the
same for the trapped chest. Observed (2026-10-17), bots, with chests `setblock`ed with a loot table:

- audience: both reach the client as `chest[waterlogged=false,type=single,facing=north]` and the
  trapped chest. sscC, sscD and sscE saw the vanilla models at each, `sscP (no pack): 0`.
- lid: `window 27 container slots; block_action sscC 1,0, sscP 1,0; lid 24.4 … 90.0 … 0.0`.

In world (2026-10-17), the user, on a vanilla 26.2 client with the pack, shaders on and off: Lootr's
chests look and work right.

## S12 — Other sources of a vanilla chest on live — **in progress**

**Build.** Nothing in this mod. A pack client draws any vanilla chest the server does not draw as
nothing. The live server was searched for every such source: all 112 mod jars (nested jars, class
constant pools and resources), the world datapacks, the configs, and the saved entities of 68,612
chunks. Each source below needs a decision.

| Source | A pack client sees | Recommendation |
| --- | --- | --- |
| `minecraft:chest_minecart` entities. Lootr's loot carts, 11,341 in the world, are sent as one. Legacy vanilla carts come from `/give`, the creative tab, dispensers and the item a broken Lootr cart drops. | An empty minecart: the cargo draws through the chest block model | Decided: S15 draws a chest on each. |
| Lootr item frames, which LootrPolymer's unordinalled `@ModifyArg` also sends as `chest_minecart`. Two are in the world, and End City elytra frames become them. | An empty minecart where a frame should be; a chest minecart before this change | LootrPolymer's bug. Overlay `lootr:item_frame` as `minecraft:item_frame`, or report it upstream. |
| A sensible-storage cart carrying a vanilla trapped chest. It is in the cargo tag, but `PlankedChestMinecart.createCargoDisplay` drew only `Blocks.CHEST`. | An empty minecart | Decided: drawn with `vanilla_trapped_chest` (S15). |
| `kalic0re.zip`'s `effect/display/create_from_blocks`, a block display per copied block | A copied chest draws nothing | Decided: S16 draws a chest over any block display of one. |
| `kalic0re.zip`'s parkour standing-block cart, a `chest_minecart` riding a block display | An empty minecart | Decided: S15 draws it like any chest minecart. |
| Block displays and falling blocks with a chest state, made by command | Nothing | Block displays: S16. Falling blocks: accept; none are in the saved world, among 4,869. |
| Chests as GUI or display items: graves and universal_shops icons, a shop's sold item, polydex | S9's item definitions: vanilla's look | Nothing to do. |
| Moving pistons | Nothing to see: vanilla refuses to push a block entity, and `PistonMovable` lets only crafting tables through | Nothing to do. |
| Punchy, a client mod, draws a chest or chest minecart held in first person | Nothing, planked chests included | No pack-side fix; see Reference: client compatibility. |

**Done when.** Each row has the user's decision, and each fix it calls for has its own check.

## S13 — Lid ticking and display cost — **done**

**Build.** Holders tick only while they have something to send. Neither block kind returns true
from `tickElementHolder`, so Polymer binds a non-ticking attachment (`ChunkAttachment.java:89-94`).
Watching does not need ticking: Polymer starts and stops watchers on chunk send and drop
(`ChunkDataSenderMixin.java:20-35`).

`display/ChestLids` keeps the awake holders:
- A holder wakes on its chest's opener count, through `signalOpenCount` on every change and every
  five-tick recheck while open (`ContainerOpenersCounter.java:66-94`).
- It also wakes on its block's state (`notifyUpdate(BLOCK_STATE_UPDATE)`), for pairing, unpairing
  and turning.
- At `END_SERVER_TICK` it steps every awake lid, then poses and sends. A moving half wakes its
  partner, so both draw the pair's angle.
- A holder drops out the first tick it sends nothing.

Measure what a chest-heavy view costs a player.

**Done when.**
- A pack client joining in view of 100 vanilla chests is sent 200 item displays, with the bytes
  reported; the no-pack client is sent none.
- Over 5 s idle, no packet goes to any of those displays.
- Opening one of the 100 sends keyframes to its lid alone, 20 of them.
- `tick query` is reported before and after.

### As built

As planned. Observed (2026-10-17) over two full runs, 100 vanilla chests two blocks apart, x
1000–1018, z 1020–1038:

| Reading | Run 1 | Run 2 |
| --- | --- | --- |
| Join in view (sscQ): 200 displays, 200 `spawn_entity` + 200 `entity_metadata` | 20,244 B, 202 B a chest | 20,300 B, 203 B a chest |
| No-pack client (sscP) | 0 `spawn_entity` | 0 |
| 5 s idle | 0 packets to any grid display | 0 |
| One chest opened and closed | 20 `entity_metadata`, all to its lid | 20, 500 B, 25 B a keyframe |
| `tick query`, grid built, before sscQ | 0.8 ms average, P99 1.7 ms | 0.7 ms, P99 1.6 ms |
| `tick query`, sscQ in view, idle | 1.0 ms average, P99 5.6 ms | 0.7 ms, P99 1.1 ms |

Run 2 passed all 38 legs of `--stages 1,2,8,9,10,11,13`; run 1 failed only S1's state leg, whose
new vanilla rig chests stood beyond the bot's view and were moved closer.

A chest nobody touches costs no server work per tick beyond Polymer's own walk of each ticking
chunk's attachments. Before this stage every planked chest's holder ran every tick: a block entity
lookup, a partner lookup and a pose comparison.

## S15 — Chests drawn on vanilla chest minecarts — **done**

**Build.** Every cart a client sees as `minecraft:chest_minecart` gets a chest display for pack
clients, whatever made it: Lootr's loot carts, legacy vanilla carts, `/summon`, the kalic0re parkour
cart (a `chest_minecart` riding an invisible block display, `NoGravity`). This mod replaces the
chest minecart's recipe, item and dispenser only (`chest-minecarts.md`), never the entities.

- `cart/VanillaCarts` binds one on `ServerEntityEvents.ENTITY_LOAD`, which covers carts loaded from
  disk, including at start, and newly spawned ones.
  - It takes a cart of type `minecraft:chest_minecart` or `lootr:chest_minecart` whose cargo a
    client is shown as vanilla's chest or trapped chest. The cargo is read through
    `PolymerBlockUtils.getPolymerBlockState`, so Lootr's own chest cargo counts.
  - The display is a `VanillaCartDisplay`, attached without ticking (`EntityAttachment.of`). The
    attachment starts the clients already tracking (`ElementHolder.java:221-232`), and a fresh
    passengers packet seats it for them.
  - `ENTITY_UNLOAD` destroys it. Polymer's entity tracking starts and stops later watchers.
  - The cart's own display state is never changed, so a client without the pack still draws its
    chest.
- `cart/VanillaCartDisplay extends ChestCartDisplay`:
  - Only the `PACK` audience is sent it. The lid follows a count of open menus.
  - `mixin/AbstractMinecartMixin` gives every cart a holder slot and ticks the holder from the end
    of the cart's own `tick`; a riding cart ticks through `rideTick` (`Entity.java:2383-2389`).
  - It ticks only while the cart moved or its lid moved, and for 9 ticks after: its pose reads up
    to 8 ticks back (`CargoDisplayHolder.MAX_RENDER_LAG`).
- Counting opens:
  - Vanilla's chest minecart counts none, and its `stopOpen` calls no super
    (`MinecartChest.java:60-62`). So `mixin/ChestMenuMixin` counts at the `ChestMenu` constructor
    that calls `startOpen` and at `removed` (`ChestMenu.java:49-54`, `:100-102`).
  - A Lootr cart's menu holds Lootr's per-player `LootrInventory`, which passes the open and close
    on to the cart (`LootrInventory.java:165-178`, Vineflower lines). `mixin/LootrInventoryMixin`
    counts there through its own `getContainer(Level)`. It is `@Pseudo` and never required, so a
    changed Lootr only stills the lid. Signatures were checked with `javap` on live's jar.
- `ChestCartDisplay` takes any `AbstractMinecart`, with `isOpen()` and `lidMoved()` for the
  subclass. `PlankedChestMinecart.createCargoDisplay` now draws a vanilla trapped chest cargo with
  `vanilla_trapped_chest`.

**Done when.**
- Bots, on four carts: a summoned `chest_minecart`, a Lootr cart, a cart riding a block display,
  and this mod's cart with a vanilla trapped chest.
  - Each pack watcher sees two item displays riding each cart, with the vanilla models (trapped for
    the last).
  - The no-pack bot sees none on the first three.
  - Opening the summoned cart and the Lootr cart gives 27 slots and the lid 10 frames up and 10
    down.
  - Teleporting the block display keeps the same two riders, none re-spawned.
- 100 vanilla chest minecarts in view:
  - A pack client is sent 200 displays and the no-pack client none.
  - 5 s idle sends no metadata to any of them.
  - Opening one sends 20 keyframes to its lid alone.
  - `tick query` is reported against the same carts carrying stone, which get no display.
- In world, every kind above shows its chest, and the lid opens with the menu.

### As built

As planned, with one thing the plan missed: Lootr's cart carries `lootr:chest` as its display
state, not vanilla's chest. Reading it raw found no chest, so the cargo goes through Polymer's
client-side mapping, as a client sees it.

Observed (2026-10-17), bots, on stock Polymer with live's Lootr and LootrPolymer:

- audience:
  - The summoned cart, the Lootr cart and the riding cart each showed `vanilla_chest_base,
    vanilla_chest_lid` to sscC, sscD and sscE, and `riders none` to sscP.
  - This mod's cart with a trapped chest showed `vanilla_trapped_chest_*` to every bot. Its display
    goes to every connection, as before.
- lid: summoned cart and Lootr cart, `window 27 container slots; lid 24.4 … 90.0 … 0.0`.
- moved: `block display 75 at 1072.500 102.000 1044.500 carrying cart 76; riders 77,78; re-spawned
  0`.
- grid: 200 displays, 28,840–28,900 B, 288–289 B a cart; sscP 0. Idle, 0 packets. One open, `20
  entity_metadata (503 B) to 1 display(s)`.
- `tick query`, averages over 100 ticks: 100 carts carrying stone, which get no display, 2.1 ms;
  the same 100 as chest carts with their displays, 2.2 ms; idle with sscQ watching, 1.7 ms.
  Against 0.7 ms with no carts, the cost is vanilla's own cart ticking, not the displays.
- The full run, `--stages 1,2,8,9,10,11,13,15`, passed all 45 legs.

In world (2026-10-17), the user, on a vanilla 26.2 client with the pack, shaders on and off:
vanilla, Lootr and block-display-riding chest minecarts, and this mod's carts with trapped and plain
cargo, one of them rolling on a rail loop, all look and work right.

## S14 — Lobby: chests for players who kept survival's pack — **in progress**

**Build.** The client keeps survival's pack across a Velocity switch, since the lobby's auto-host
is off (server-data `docs/plan/velocity-network.md` S5). On the lobby a pack client would therefore
draw every vanilla chest as nothing, while drawing chest items through S9's item definitions,
which travel with the pack. The user chose (2026-10-17) a proxy plugin that vouches to the lobby
that the player holds survival's pack, and sensible-storage on the lobby drawing for those players.

- **rkcore-proxy** gets `pack/HeldPackVouch`, on branch `feat/lobby-held-pack` off `9b07ea7`:
  - It records, per player and backend name, the last pack each backend pushed
    (`ServerResourcePackSendEvent`, which fires even when Velocity answers a push itself).
  - On `PlayerFinishConfigurationEvent` for a backend that never pushed this player a pack, it
    sends `rkcore:held_pack` to that backend. The message is sent only if one of the player's
    applied packs (`Player.getAppliedResourcePacks`, Velocity's record of what the client loaded)
    matches another backend's push by hash. It carries the pack id, then the hash with a varint
    length.
  - That event is awaited before Velocity lets the client leave configuration
    (`ClientConfigSessionHandler.java:246-266`, Vineflower lines). The message therefore reaches
    the backend while it is still configuring, before any chunk.
  - `PluginMessageEvent` drops the channel from clients, and `DisconnectEvent` forgets the player.
  - No server name is configured: survival pushes its pack on every configuration, so it is never
    vouched to; the lobby pushes none.
- **sensible-storage** gets `display/HeldPacks`, which receives the channel in configuration and,
  late, in play.
  - It marks the vouched pack id, and this server's own Polymer main pack, as held on the
    connection (`PolymerCommonUtils.setHasResourcePack`). So every Polymer mod on the lobby treats
    the player as holding the pack.
  - In play it also resends the player's chunks (`PolymerUtils.reloadWorld`), since chunks
    already sent passed the chest holders by.
  - `ChestElementHolder.Audience.PACK` becomes this server's blanking pack held, **or** a vouched
    pack held. The lobby builds no pack, so `VanillaChestPack.blanked()` stays false there.
- The receiver lives in sensible-storage, not rkcore-common: the lobby needs sensible-storage
  anyway, to draw the chests, and does not run rkcore-common. Putting it there adds one mod to the
  lobby instead of two, and no cross-mod API. If another mod ever needs the vouch, it moves to
  rkcore-common.
- Edge cases follow from Velocity's applied list:
  - A player who declined survival's pack, or who reached the lobby without ever being on
    survival, holds nothing, so is vouched nothing and sees vanilla's own chests.
  - A pack replaced or popped since is no longer applied. Only the pack the client holds now, and
    that survival last pushed it, is vouched for.

**Done when.**
- Bots, through a scratch Velocity 4.2.0 (live's jar and Via plugins) in front of this dev server
  as `survival` and a second dev server with auto-host off as `lobby`:
  - A bot holding survival's pack switches with `/server lobby`. It is pushed no pack there, gets
    its spawn chunk once (so the vouch came in configuration), and sees the lobby's chest and
    trapped chest drawn.
  - A bot that declined the pack sees none.
  - A bot that lands in the lobby first, through a forced host, sees none.
  - That bot forging `rkcore:held_pack` itself is dropped at the proxy: no display, no chunk
    resend.
  - As a control, the same forgery sent straight to the lobby is taken in play, and the chests are
    drawn after a chunk resend.
- In world, after the lobby deploy, a vanilla chest placed on the lobby looks like vanilla's for a
  player who came from survival.

The lobby deploy needs, in order, each swapped rather than stacked:
1. `rkcoreproxy-<next>.jar` in place of `rkcoreproxy-0.1.1.jar` in `proxy/plugins/`, then
   `scripts/restart.sh proxy`. It is harmless alone: the lobby discards a channel it does not
   know.
2. The sensible-storage jar at survival's exact version in `lobby/mods/`, beside the RKC Polymer
   fork jar and Fabric API already there. Then `scripts/restart.sh lobby`.
3. The lobby's other Polymer mods (better-barriers) must match survival's versions too, since a
   vouched player draws them from survival's pack.

Placing this mod's bookshelves or crafting tables on the lobby would still draw wrong: their note
block carrier states are allocated per server.

### As built so far

As planned. rkcore-proxy builds and lints clean, released as 0.1.2.

The scratch network ran entirely under `/tmp/claude-1000/t3/velocity/` (127.0.0.1:25790, offline
mode, no forwarding, forced host `lobby.test`). The lobby was the worktree `lobby-scratch` of this
branch on 25794, auto-host off.

Observed (2026-10-17):

- kept pack: `survival pushed sscH 454a1584…; pushes on the lobby 0; spawn chunk sent 1x; chest
  sensiblestorage:vanilla_chest_base, sensiblestorage:vanilla_chest_lid; trapped
  sensiblestorage:vanilla_trapped_chest_base, …`.
- declined pack: `chest none; trapped none`.
- lobby first: `pushes 0; chest none; trapped none`.
- forged at the proxy: `chest none; trapped none; map_chunk after 0`.
- control, forged straight to the lobby: `before none / none; after
  sensiblestorage:vanilla_chest_base, … ; map_chunk after 117`.
- The lobby logged no ERROR line.

Not exercised: a pack that changes between boots (survival rebuilding a different pack while a
player sits on the lobby), live's modern forwarding with FabricProxy-Lite, and LuckPerms. The rule
for a changed pack comes from Velocity's applied list (above), not from a run.

The in-world leg is the user's and waits for the deploy.

## S16 — Chests drawn over block displays — **done**

**Build.** A block display showing a chest or trapped chest gets the chest drawn over it for pack
clients, whatever made it. The case that prompted it is kalic0re.zip's `create_from_blocks`, which
copies blocks into block displays riding a root; the datapack is left as it is.

- `display/ChestBlockDisplays` binds a `ChestDisplayOverlay` on `ServerEntityEvents.ENTITY_LOAD`
  and drops it on unload.
  - It takes a block display whose block a client is shown as vanilla's chest or trapped chest
    (`PolymerBlockUtils.getPolymerBlockState`, so Lootr's count).
  - A planked chest keeps its wood's models.
- `mixin/BlockDisplayMixin` gives every block display an overlay slot. Its `onSyncedDataUpdated`
  queues that block display for the end of the tick, where the overlay is rebuilt if the block
  changed (removed if it is no chest any more) or re-posed. Nothing else runs per tick.
- `mixin/BlockDisplayAccessor` and `mixin/DisplayAccessor` read what `Display` keeps private: the
  block, the transformation, its interpolation, brightness, billboard and view range.
- `display/ChestDisplayOverlay`:
  - Its base and lid ride the block display itself, so they follow a `/tp`, its teleport
    interpolation and any vehicle it rides; kalic0re's parts ride their root with a static
    transformation, and only the root moves.
  - Each element's transformation is the block display's, then half a block in (a block display
    draws from its corner, an item display centred), then the placed chest's own pose: base
    rotated to its facing, lid at its hinge. Left and right halves get their half models, so a
    double stays whole, Christmas select included.
  - It copies the interpolation duration, starting it when the block display's does, as well as the
    brightness, billboard, view range, and the entity's yaw and pitch.
  - The lid stays shut; a block display has no opener.
  - Only the `PACK` audience is sent it. A new watcher's riders spawn at the block display's current
    place.
- `ChestVisual.hinge(orientation)` exposes the lid pivot for that pose.

**Done when.**
- Bots, with kalic0re.zip from its `datapackZip` installed in the dev world:
  - A 2×1 double chest copied by `create_from_blocks`, the blocks then cleared, shows each pack
    watcher both halves' models riding the two part displays. Each base sits half a block in from
    its part's translation. The no-pack bot sees none.
  - `animate_move_display`, then `animate_bounce_display` on the root, keeps the same four riders on
    the same parts, none re-spawned or removed.
  - A lone summoned block display of a trapped chest carries the trapped models, none for the
    no-pack bot, and keeps them through a `/tp`.
  - Turned to stone, its overlay is removed; back to a chest, it returns with the plain models;
    killed, it goes.
- In world, a copied chest, single or double, looks like the chest it was, from every side, and
  stays whole while the copy moves or bounces.

### As built

As planned. Observed (2026-10-17), bots:

- copy: `create_from_blocks … returned 2`. sscC saw `vanilla_chest_left_base, vanilla_chest_left_lid,
  vanilla_chest_right_base, vanilla_chest_right_lid`. The parts at translation `-1,-0.5,-0.5` and
  `0,-0.5,-0.5` had bases at `-0.5,0,0` and `0.5,0,0`. sscP saw `riders none`.
- move: the root reached `1089.000 101.500 1005.500` with riders unchanged, re-spawned 0, removed 0.
  Bounce: likewise, the root at `1085.707 103.963 1007.201`.
- lone: `vanilla_trapped_chest_base, vanilla_trapped_chest_lid` for sscC, none for sscP. After the
  tp the riders were the same, none re-spawned.
- state: on stone, riders removed; back to a chest, `vanilla_chest_base, vanilla_chest_lid`; on kill,
  removed.

The full run, `--stages 1,2,8,9,10,11,13,15,16`, passed all 50 legs.

Enabling kalic0re.zip mid-run logs function load errors: its parkour functions name enchantments
that load only at a restart. The pack causes them, not this mod, and the harness leaves them out of
its error check.

Not covered: a block display turned by `/tp` with a rotation, since entity rotation is no synched
data and is copied only at the next data change; and its glow and shadow, which are not copied.

In world (2026-10-17), the user, on a vanilla 26.2 client with the pack, shaders on and off: a
double chest copied by `create_from_blocks` stays whole while moved and bounced, and a lone
trapped-chest block display looks right.

## S7 — Retire the barrier-era notes — **in progress**

**Build.**
- `planked-chests.md` open decisions: mark the vanilla-client "Client selection box" and
  "Right-click ghost-placement flicker" resolved, pointing here.
- `client-mod-support.md`: change the vanilla row of the per-connection table from
  `Blocks.BARRIER` to the stand-in. Note that a pack client also gets S10's vanilla chests.
- `chest-minecarts.md`: the cart cargo's `vanilla` sheet now comes from the pack copy (S9). The
  "DisplayState air still shows its chest" puzzle has an answer: the entity data sends
  `Optional.of(air)` as id 0, which the client reads as empty and replaces with the default chest
  (`EntityDataSerializers.java:73-86`).
- `docs/TESTING.md` gains this run's traps:
  - localhost has no jitter;
  - a display keyframe stream restarts from the last rendered frame;
  - a client mod can rebuild a block entity the server cleared, so no server-side scrub holds;
  - a transparent texture hides only what an alpha-tested render type draws;
  - the live containers' processes run under the workspace user and are visible on the host, so
    `pkill -f velocity-4.2.0-30.jar` aimed at a scratch proxy also kills live's (it did,
    2026-10-17). Kill a scratch process by its own PID, started with a unique marker.
- The workspace `CLAUDE.md` sensible-storage row: the pack blanks vanilla's chest sheets and the
  server draws vanilla and Lootr chests.

The docs go in their own commit, ahead of any code.

**Done when.** The five files carry the change, committed separately from the code.

### As built so far

`planked-chests.md`, `client-mod-support.md` and `chest-minecarts.md` carry the change, in a docs
commit ahead of the code. `docs/TESTING.md`, which no repo holds, has the five traps. The
workspace `CLAUDE.md` row waits for the live deploy.

## Open decisions

- **Break particles.** Planks for onlookers, or uniform oak? For crimson and warped, the planks
  state also switches the onlooker's break sound to nether wood. The user decides.
- **Lootr item frames (S12).** LootrPolymer sends them as chest minecarts, an empty one now. Overlay
  `lootr:item_frame` as `minecraft:item_frame`, report it upstream, or accept.
- **LootrPolymer's indicator.** S11 takes its overlay slot, so turning `lootr_indicator` on later
  draws nothing. Accept, or have S11's holder add the indicator's elements when that config is
  on.
- **Vanilla art still in the jar.** S9 copies vanilla's chest sheets at pack build instead of
  shipping them. The copper chest sheets, shulker boxes, bookshelf and crafting table textures
  that datagen derives from vendored vanilla art still ship in the jar. Move them to the same
  pack-build copy, or leave them.

## Reference: client compatibility

What the user's client (the Kalic0 Modpack profile) does with S9's transparent sheets, read from
the jars:

- **Iris 1.11.4**, with the active pack (Distant Voxels, a rethinking-voxels fork): safe.
  - `entity_cutout_cull` maps to `BLOCK_ENTITY_DIFFUSE`, `ENTITIES_CUTOUT_DIFFUSE`,
    `HAND_CUTOUT_DIFFUSE` and `SHADOW_ENTITIES_CUTOUT`, all with `AlphaTests.ONE_TENTH_ALPHA`
    (`IrisPipelines`, `ShaderKey`).
  - `CommonTransformer` appends that discard to the shader pack's own fragment `main`.
  - The pack itself discards nothing there, and sets no `alphatest.` override for block, entities,
    hand or shadow, so a transparent chest also casts no shadow. A pack edit adding such an
    override would break this.
- **Sodium 0.9.2, Sodium Extra 0.9.4**: safe. The fast cuboid path keeps the render type, and
  neither touches chests.
- **EMF 3.3.12, ETF 7.2.6, Fresh Animations' chest `.jem`s**: safe. The models carry no
  `"texture"`, so they sample the transparent sheet. ETF counts a variant or emissive only from a
  pack at or above the base texture's, and the server pack is on top.
- **Punchy 2.8f**: draws a chest held in first person, and the chest of a held chest minecart,
  through vanilla's chest block entity renderer with a fake chest block entity
  (`punchy/client/render/ChestInHandRenderer`, `MinecartInHandRenderer`). With the blanked sheets
  both draw nothing, planked chests' `Items.CHEST` carrier included. There is no pack-side fix:
  players set `disableChestModels` (and `disableBoatMinecartRaftModels` for the minecart) to `true`
  in `config/punchy/punchy_config.json`. The user's client was changed on 2026-10-17. With Punchy
  the first-person arm does not swing on a firework-star placement, though it does in third person
  (the user, 2026-10-17); left as is.
- **SubtleEffects 1.15.1** (disabled in the profile since S6): it only drives the invisible client
  lid, plays chest sounds and spawns bubbles, so no lid shows.

## Reference: lid timing, worked through

Vanilla: `ChestBlockEntity.triggerEvent` sets the target on arrival. `lidAnimateTick` then moves
the controller 0.1 per *client* tick (`ChestLidController.java:10-18`). Each frame lerps the two
tick values and eases the result (`:20-22`, `ChestRenderer.java:83`, `:92-94`). The display
applies a new transformation at the client tick after it arrives. It interpolates linearly (slerp)
over `interpolation_duration` ticks, starting from the old interpolation evaluated at the progress
of the last *rendered* frame (`Display.java:155-187`, `:367-377`, `:423-434`; `lastProgress` is set
per frame from `DisplayRenderer.java:127`).

| Tick | Progress | Eased | Angle | Step |
| --- | --- | --- | --- | --- |
| 1 | 0.1 | 0.271 | 24.4° | 24.4° |
| 2 | 0.2 | 0.488 | 43.9° | 19.5° |
| 3 | 0.3 | 0.657 | 59.1° | 15.2° |
| 4 | 0.4 | 0.784 | 70.6° | 11.5° |
| 5 | 0.5 | 0.875 | 78.8° | 8.2° |
| 6–10 | 0.6–1.0 | 0.936–1 | 84.2°–90° | 5.4° down to 0.1° |

Lid geometry measured from the hinge (model (0,9,1)): the lid's far top edge sits 14 px out and 5 px
up, and the latch top 15 px out and 2 px up. Turning the lid an extra δ lifts those points by
14δ px and 15δ px (δ in radians), so 1° is 0.24 px and 0.26 px. A 1% margin (≈0.14 px) covers
δ ≤ 0.57°.

| Source | Size | Covered by |
| --- | --- | --- |
| Chord: the display lerps eased keyframes, vanilla eases a lerp | ≤ 0.64° in tick 1, ≈0.68°·(1−p) after | ≈0.2 px latch/top margin |
| Restart: each keyframe starts where the last frame stopped | 60 fps: up to ≈9°, typically ≈4°; 144 fps: up to ≈3.4° | ping-pong lid (S5) |
| Split send of the first frame and the event | one whole step (24.4°) if a client tick falls between | the S4 bundle |
| Jitter: a frame lands after its client tick | for one tick, up to that tick's step; then a jump | nothing in S5 |
| Server behind: tween per server tick, vanilla per client tick | at 15 TPS, ≈11.5° after 0.2 s | nothing in S5 |
| Display off-screen: `lastProgress` goes stale | a jump when it comes back into view | nothing; rare |

The restart figures come from the carried error `E(k+1) = (1−p)·(step(k) + E(k))`, where p is the
partial tick of the last frame before the tick (at least 1 − frame time/50 ms). While opening, the
display lags, so the vanilla lid's top and latch rise through ours. While closing, it lags the
other way, so the vanilla lid's front and latch drop below ours. Sounds are unaffected either way:
open and close play from the server when the opener count changes (`ChestBlockEntity.java:41`,
`:48`, `:109-122`), apart from both lids. Several viewers need nothing extra. The event carries the
count and the tween reads count > 0, so only 0 ↔ >0 moves either lid, and `recheckOpen` drops
viewers that vanished for both. TCP never reorders; the timing faults above are the only mismatch.

## Reference: how another server probably did it

The user's observation (a non-vanilla chest with a vanilla chest hitbox) proves only that the
server sent a chest state. Polymer has no chest-specific support. Its generic pieces cover
everything here: any vanilla client state, `BlockWithElementHolder` over any state, the
per-connection block-entity strip, `getPolymerBreakEventBlockState`, `handleMiningOnServer` and
`isPolymerBlockInteraction`. `onPolymerBlockSend` (`PolymerBlock.java:34-43`) can add client-only
block-entity data, but a chest's renderer reads none.

| Candidate | Verdict |
| --- | --- |
| Chest stand-in, display slightly larger, vanilla lid left to animate (the user's guess) | Plausible. At a glance, at high frame rate and low ping, the slivers are thin and brief. S5. |
| Chest stand-in, vanilla lid suppressed, client block entity avoided or ignored | Plausible. Chests that never show the vanilla lid, with or without a scrub. S6. |
| Pack-wide retexture of `entity/chest/*` | Retextures real chests too, so first rejected. Chosen after S6 as a transparent retexture, with the server drawing real chests as well (S9, S10). |
| Interaction or shulker entity for the hitbox | Gives clicks and collision but no block outline, which contradicts the observation. Rejected. |
| Block-entity data tricks | The renderer reads no block-entity data; a mismatched type in chunk data still leaves the client creating its own chest entity. Rejected. |

## Reference: alternative stand-ins

- **`minecraft:trapped_chest`** for trapped planked chests: used. It has the same hardness, sound
  and shape (`Blocks.java:2830-2833`), and it pairs only with trapped chests, as the server does.
- **Ender chest**: worse in every respect. It has no `TYPE`, so a double chest's outline becomes
  two boxes. It gives off light 7 and has hardness 22.5 (`Blocks.java:2629-2632`). Its particles
  are obsidian, and it spawns portal particles client-side (`EnderChestBlock.java:114-124`).
- **Copper chests**: `SoundType.COPPER` and hardness 3.0 (`Blocks.java:5281-5289`), so steps, hits
  and mining speed go wrong. They pair with every copper chest (`CopperChestBlock.java:66-67`).
  Nothing is gained, because the open and close sounds come from the server anyway.
- **Any retexture of the stand-in** reaches every real chest of that block. S9 does exactly that,
  transparently, which is why S10 and S11 draw every real chest too.
- **The RKC Polymer fork's pools** (`rkc/forks/polymer`, kalic0re `common/carrier`) are FULL_BLOCK
  note-block slots. `BlockModelType` has no chest pool (`polymer-blocks/.../BlockModelType.java`),
  and this mapping claims no state. It shares chest states with real chests and remaps none of
  them, so a real oak chest keeps its own block entity from chunk data; under S9 that block entity
  draws nothing and S10's display draws the chest.

## Reference: interplay

- **Chest minecarts.** Vanilla clients keep `minecraft:minecart` with the display passenger
  (`chest-minecarts.md` S3). `ChestVisual` is shared; since S8 placed chests and cargo draw the same
  exact models, and the plain cargo reads S9's copy of vanilla's sheet, Christmas included.
  `ChestCartDisplay` sets its own durations. Vanilla and Lootr chest minecart entities get a
  `VanillaCartDisplay`, the same visual for pack clients only (S15).
- **Hover labels.** No label targets a chest. `Crosshair` rides `player.pick`, which today stops at
  the barrier cube (first finding), so a frame or shelf just above or behind a planked chest gets
  its label where a vanilla client aims.
- **Mod clients.** Real state, no display for a planked chest, their own renderer, and vanilla's
  block event broadcast again since S8. Holding the pack, they draw vanilla's chest sheets
  transparent too, so S10 sends them vanilla chests' displays.
- **The client-item plan.** See Open decisions. Placement with the `Items.CHEST` fallback now
  predicts the stand-in itself, rather than a vanilla chest that the barrier replaced.

## Reference: verified APIs and line numbers

- Client block entities: `LevelChunk.java:326-348` (created on a state change), `:373-397`
  (`IMMEDIATE`), `:526-548` (chunk data clears every one at `:531`, then creates only listed
  entities); `ClientChunkCache.java:103-129` (a loaded chunk is reused);
  `ClientPacketListener.java:855-867`; server send `PlayerChunkSender.java:76-84`;
  `Level.java:694-703`; `BaseEntityBlock.java:23-27`.
- Client events: `ClientPacketListener.java:1525-1528`; `Level.java:905-907`;
  `ChestBlockEntity.java:105-107`, `:126-133`. Server events: `ServerLevel.java:400-404`,
  `:1254-1284`.
- Prediction: `ClientLevel.java:225-237` keeps the server state for revert. After a use,
  `ServerGamePacketListenerImpl.java:1393-1394` resends the clicked block and its neighbour.
- `ChestBlock`: shapes `:77-78`; `updateShape` `:164-191`; `chestCanConnectTo` `:193-195`;
  placement `:216-243`; `useWithoutItem` `:261-274`; client-only ticker `:333-335`.
- Render: `ChestRenderer.java:63-116`; `BlockEntityRenderDispatcher.java:72-90`;
  `BlockEntityRenderer.java:34-40` (64 blocks); outline `LevelRenderer.java:715-733`.
- Polymer (`libs/polymer`): `PolymerBlock.java:24`, `:43`, `:64-79`;
  `PolymerBlockUtils.java:94-96`, `:145-147`; `BlockBehaviourMixin.java:23-46`;
  `BlockStateBaseMixin.java:30-40`; `ClientboundBlockEventPacketMixin.java:16-20`;
  `ClientboundLevelChunkPacketDataMixin.java:18-28`; `ClientboundLevelEventPacketMixin` (break
  state); `ServerGamePacketListenerImpMixin.java:162-170`; virtual entity
  `ElementHolder.java:141-153`, `GenericEntityElement.java:156-198`, `DisplayElement.java:119-123`, `ServerWorldMixin.java:15-24`.
- Openers: `ContainerOpenersCounter.java:66-94` re-sends the count every 5 ticks while open.
- S6 in Polymer: `PlayerChunkSenderMixin.java:21-27` builds the chunk packet in the player's
  context; `BlockPaletteMixin.java:21-24` and `PalettedContainerDataMixin.java` remap through
  `PacketContext.get()`; `ClientboundLevelChunkPacketDataMixin.java:18-27` drops Polymer block
  entities. Client item: `UnpinnedBlockItem.java:41-55` over kalic0re
  `PolymerModelBlockItem.java:63-81`, RKC Polymer `PolymerBlockItemStandIns.java:206-207` (several
  placement stand-ins give the firework star).
- Transparent sheets and server-drawn chests: `ChestModel.java:26` (`entityCutoutCull`),
  `RenderPipelines.java:241-247` (alpha cutout 0.1), `ChestRenderer.java:97`, `:101-114`,
  `ChestSpecialRenderer.java:24-49`, `:61`, `BuiltInBlockModels.java:87-88`,
  `SpecialDates.java:10-12`; assets `items/chest.json`, `items/trapped_chest.json` (Christmas
  select), `atlases/chests.json`; server packs on top, `DownloadedPackSource.java:57`.
- Polymer for S9, S10 and S13: `PolymerResourcePackUtils.java:38`, `:112-143`;
  `ResourcePackCreator.java:153-171`; `DefaultRPBuilder.java:335-341`, `:384-389`;
  `BlockWithElementHolder.java:43-50`; `BlockMixin.java:25-28` (one overlay, overwritten
  silently); `WorldChunkMixin.java:54-109`; `ChunkAttachment.java:89-94` (`autoTick`);
  `ChunkDataSenderMixin.java:20-35`; `ElementHolder.java:141-154`, `:282`;
  `BlockBoundAttachment.java:80-84` (`BLOCK_STATE_UPDATE`).
- S15 and S16, vanilla: `MinecartChest.java:60-62` (`stopOpen` without super); `ChestMenu.java:49-54`,
  `:100-102`; `Entity.java:2383-2389` (`rideTick`); `Display.java:118-139`
  (`onSyncedDataUpdated`), `:146` (`createTransformation`, private), `:465-520` (`BlockDisplay`).
  Polymer: `EntityTrackerEntryMixin` (pairing sends a passengers packet when a holder has
  riders), `EntityPassengersSetS2CPacketMixin` (riders listed only to the holder's watchers),
  `ElementHolder.java:221-232` (`setAttachment` starts current trackers). Lootr (Vineflower):
  `LootrInventory.java:165-178`, `LootrChestMinecartEntity.java:48`, `:185`.
- S14: Velocity API 4.2.0 (`javap`): `ServerResourcePackSendEvent.getReceivedResourcePack`,
  `PlayerFinishConfigurationEvent.player()`/`server()`, `Player.getAppliedResourcePacks`,
  `ResourcePackInfo.getId`/`getHash`, `ChannelMessageSink.sendPluginMessage`,
  `PluginMessageEvent.ForwardResult.handled`. Velocity (Vineflower): `ConfigSessionHandler.java:
  115-160`, `ClientConfigSessionHandler.java:236-266`, `VelocityServerConnection.java:188-213`.
  Fabric networking 6.3.9: `PayloadTypeRegistry.serverboundConfiguration`/`serverboundPlay`,
  `ServerConfigurationNetworking.Context.packetContext`. Polymer:
  `PolymerCommonUtils.setHasResourcePack` (`PolymerCommonUtils.java:185-191`),
  `PolymerUtils.reloadWorld` (`PolymerUtils.java:86-88`).
- This mod: `PlankedChestBlock.java:137-152`, `:189-198`; `ChestBlocks.java:101-115`;
  `ChestElementHolder.java:45-64` (audiences), `:126-168`; `ChestLids.java`;
  `VanillaChestDisplays.java`; `VanillaChestPack.java`; `HeldPacks.java`; `ChestBlockDisplays.java`;
  `ChestDisplayOverlay.java`; `cart/VanillaCarts.java`, `VanillaCartDisplay.java`;
  `ChestBlockEntityMixin.java`, `AbstractMinecartMixin.java`, `ChestMenuMixin.java`,
  `LootrInventoryMixin.java`, `BlockDisplayMixin.java` and its accessors;
  `ChestVisual.java:78-131`; `PlankedTrappedChestBlockEntity.java:31-41`; kalic0re `LidTween.java`,
  `ItemDisplays.java:25`.
