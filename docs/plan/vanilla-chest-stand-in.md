# Vanilla chest stand-in

Send a vanilla client `minecraft:chest` (and `minecraft:trapped_chest` for trapped ones) instead of
today's barrier, so it gets the true chest outline, hitbox and collision, while the existing display
entity keeps drawing the planked wood over it.

Claims were checked against `libs/decompiledjava/minecraft-26.2/`, the 26.2 deobf jar's assets,
`libs/polymer` (`dev/26.2`, `9a3e8150`) and the RKC Polymer fork at `rkc/forks/polymer`
(`rkc/26.2`). Nothing here has been built. The lid-timing, depth and outline figures are worked
out from the client code, not measured. Four things stay unverified until their stage runs: whether
bundles reach a 26.1 bot intact through ViaBackwards (S4), the double swing (S2), the client cost of
an in-place chunk resend (S6), and how visible a sub-pixel sliver of the vanilla lid is in motion
(S5).

Stage order: S1 (harness and stand-in state) → S2 (interaction, mining, break effects) → S3
(covering geometry) → S4 (lid event routing) → S5 or S6 → S7 (docs). S1–S3 deliver the outline,
hitbox and collision, but until S5 or S6 a vanilla client sees the vanilla oak lid swing through
ours on every open, so nothing deploys before one of them. S5 is the lead: keep the vanilla lid
moving and run the display lid in step with it. S6 is the fallback: withhold the vanilla lid and
scrub client block entities. Exactly one is built and the other becomes `skipped`. **S5 or S6 makes
the feature complete.** The client-item placement plan being written alongside this one is not a
dependency of S1–S5; S6 works better with a non-predicting client item (see Open decisions).

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
from spawn and is cleared to air first.

The machine legs go in a new `scripts/loadtest/sensible-storage-chests.js`, written in S1 and grown
by each stage, on the pattern of `player-portals.js`:

- `lib/flyer.js` for `joinBot`, `fixResourcePackReplies`, `endClientTicks` and `echoSwings`;
- the sound capture and `calibrateSounds`/`soundName` (`player-portals.js:2146-2160`, `:2199-2214`);
- the `world_event` listener (`:2331`);
- `lib/devserver.js` for `countFailedPackets` and `countWrongMoves`, over the whole run.

Each leg is one "Done when" line. Raw packets come from `bot._client.on('packet')`. The packet
names (`block_action`, `entity_metadata`, `bundle_delimiter`, `sound_effect`, `world_event`,
`map_chunk`) are minecraft-data's for 26.1; check each against a probe that is known to fire before
trusting a zero.

The visual legs are the user's, on a **vanilla** 26.2 client with the server pack accepted. The
dev `runClient` runs this jar, passes the handshake and gets the real block
(`client-mod-support.md`), so it never sees the stand-in. Brief the user on each leg before a
round, and leave the server up afterwards. The lid legs (S5, S6) also need:

- a capped frame rate (60 fps, then 144, in Video Settings);
- a slow server (`/tick rate 15`);
- jitter. Localhost has none, so a lid that matches there proves nothing about live. S5 builds the
  proxy for it.

A stage is done only when its "Done when" is observed, so a failure at stage N implicates stage N
and not the ones before it.

## S1 — Bot harness and the stand-in state — **planned**

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

## S2 — Interaction, mining and break effects — **planned**

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

## S3 — Covering geometry — **planned**

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

## S4 — Lid event routing — **planned**

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

## S5 — Vanilla lid kept, display lid in step — **planned**

**Build.** The lead lid strategy:

- The placed lid's interpolation duration becomes 1. `ItemDisplays.DEFAULT_INTERPOLATION` is 2
  (`ItemDisplays.java:25`), which on its own keeps the display a tick behind vanilla. Keyframes stay
  one per tick from the unchanged `LidTween`. The cart sets its own durations and is untouched.
- A lid margin: a datagen parameter for the lid's top, front and latch, on top of S3's ε. The latch
  sticks out past the outline, so its margin costs nothing. The lid top carries the outline's top
  edges, so its margin hides them. Start at the user's 1% (0.14 px) and step up.
- `scripts/loadtest/lib/jitter-proxy.js`, a TCP forwarder that delays server-to-client data by a
  base plus uniform jitter while keeping order. Both the user's client and a bot connect through
  it.
- Only if the 60 fps leg shows the gap opening at the start of each tick: a ping-pong lid. Two lid
  elements take alternate ticks. The hidden one snaps (duration 0) to the next tick's start while
  the shown one interpolates (duration 1), and they trade places at the tick boundary by swapping
  items (an empty stack hides). This removes the restart lag but does nothing for jitter or server
  lag; see "Reference: lid timing".

**Done when.** The requirement is the display effect. Watch a warped chest (teal against oak, the
worst contrast), 10 opens and 10 closes, from 2 blocks in front and from above. No oak shows at the
lid's front, top or latch in any of these conditions:
- at 60 fps through the proxy at 20 ± 15 ms;
- with the server at `/tick rate 15`.

The shut chest's outline still shows its top edges. The bot reads one lid `entity_metadata` per tick
for 10 ticks per transition, with angles 24.4°, 43.9°, 59.1° … 90° (±0.1°) decoded from
`right_rotation`. If the user accepts no lid margin that meets the requirement, this stage becomes
skipped and S6 is built. The timing reference predicts that jitter and `/tick rate 15` fail it at
1%.

## S6 — Vanilla lid withheld, client block entities scrubbed — **planned**

**Build.** The fallback lid strategy. S4's switch goes to `NONE`. No vanilla client gets a chest
event, so its lid never moves, and the display's lid (`LidTween`, as now) is the only one that
does. That still leaves a closed oak lid wherever a client already holds a block entity, and it
shows inside ours when ours opens.

Scrub those block entities. After a stand-in state change has gone to a vanilla viewer, resend
that chunk to that viewer in place. Two things count as a change: anything that sets a block
(placement, pairing or unpairing, waterlogging, a piston landing) and the viewer's own placement
prediction. A `ClientboundLevelChunkWithLightPacket` for a loaded chunk replaces its data and
clears every block entity first (`LevelChunk.java:528-546`), and Polymer's per-player remap and
strip apply to it like any chunk packet.

Debounce per chunk per viewer, about 10 ticks after the last change, which also lets a placer's
prediction ack land first. Polymer's `reloadLevel` (`PolymerUtils.java:93-120`) drops and resends a
whole view; one chunk in place is what's wanted here. The display margin falls back to S3's ε,
because a block entity now exists only between a change and its scrub.

The cost: on that client, real vanilla chests in the scrubbed chunk forget an open lid, as on any
chunk reload, and other block-entity animations restart.

**Done when.**
- A vanilla bot gets no `block_action` for a planked chest across the S2 sound legs.
- The bot gets one `map_chunk` for the rig's chunk within 1 s of each placement or pairing, and the
  bytes per scrub are logged.
- In world, a chest you placed yourself and then opened never shows an oak lid inside once the
  scrub has run.
- Placing a wall of 10 chests causes no visible chunk flicker.
- A real vanilla chest in the same chunk still renders and animates normally.

## S7 — Retire the barrier-era notes — **planned**

**Build.** `planked-chests.md` open decisions: mark the vanilla-client "Client selection box" and
"Right-click ghost-placement flicker" resolved, pointing here. `client-mod-support.md`: change the
vanilla row of the per-connection table from `Blocks.BARRIER` to the stand-in. `docs/TESTING.md`
gains this run's two traps: localhost has no jitter, and a display keyframe stream restarts from
the last rendered frame. The docs go in their own commit, ahead of any code.

**Done when.** The three files carry the change, committed separately from the code.

## Open decisions

- **S5 or S6.** S5's legs settle it, and the user prefers S5. The timing reference predicts that
  S5 fails under jitter and on a slow server. S6 costs one chunk resend per change but is immune to
  both.
- **The lid margin against the outline (S5 only).** Any lid-top margin beyond about 0.004 px hides
  the shut chest's top outline edges. The user's eye in S5 settles it.
- **Break particles.** Planks for onlookers, or uniform oak? For crimson and warped, the planks
  state also switches the onlooker's break sound to nether wood. The user decides.
- **The client item, which is the other plan's call.** What each option means here:
  - The `Items.CHEST` fallback predicts the stand-in's own block: right facing, and same-wood pairs
    right. Next to another wood or a vanilla chest it predicts a double that the ack reverts a
    round trip later. Good enough for S5.
  - For S6, every prediction creates a block entity that then has to be scrubbed, so a
    non-predicting client item is better.
  - A pin to `type=single` mispredicts every same-wood pair.
- **Clients without `ARB_clip_control`** (`GlDevice.java:93-96` enables zero-to-one clip space only
  when it is present; macOS OpenGL 4.1 lacks it). Depth precision there drops to about 0.04 px at
  64 blocks, far above ε. Such a client z-fights at range wherever it holds a block entity: always
  under S5, briefly under S6. The server cannot tell these clients apart, so accept it unless
  someone reports it.

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
| Pack-wide retexture of `entity/chest/*` | Only on a server that wants every chest custom; retextures real chests too. Rejected. |
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
- **Any retexture of the stand-in** reaches every real chest of that block. No approach here
  retextures anything.
- **The RKC Polymer fork's pools** (`rkc/forks/polymer`, kalic0re `common/carrier`) are FULL_BLOCK
  note-block slots. `BlockModelType` has no chest pool (`polymer-blocks/.../BlockModelType.java`),
  and this mapping claims no state. It shares chest states with real chests and remaps none of
  them, so a real oak chest keeps its own block entity from chunk data and renders as before. The
  one exception is S6's scrub, which resets real chests' lid state on the scrubbed client.

## Reference: interplay

- **Chest minecarts.** Vanilla clients keep `minecraft:minecart` with the display passenger
  (`chest-minecarts.md` S3). `ChestVisual` is shared, so the placed-only geometry (S3), duration
  (S5) and ping-pong lid (S5) are options on `ChestElementHolder`. `ChestCartDisplay` already sets
  its own durations.
- **Hover labels.** No label targets a chest. `Crosshair` rides `player.pick`, which today stops at
  the barrier cube (first finding), so a frame or shelf just above or behind a planked chest gets
  its label where a vanilla client aims.
- **Mod clients.** They are unchanged: real state, no display, their own renderer. Under S4 their
  event is sent directly instead of by broadcast.
- **The client-item plan.** See Open decisions. Placement with the `Items.CHEST` fallback now
  predicts the stand-in itself, rather than a vanilla chest that the barrier replaced.

## Reference: verified APIs and line numbers

- Client block entities: `LevelChunk.java:326-348` (created on a state change), `:373-397`
  (`IMMEDIATE`), `:528-546` (chunk data clears, then creates only listed entities);
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
- This mod: `PlankedChestBlock.java:125-146`; `ChestBlocks.java:101-115`;
  `ChestElementHolder.java:49-114`; `ChestVisual.java:44`, `:79-121`;
  `PlankedTrappedChestBlockEntity.java:31-41`; kalic0re `LidTween.java`, `ItemDisplays.java:25`.
