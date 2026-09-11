# Client-optional real rendering

`planked-chests` is server-side. To a vanilla client a chest is an invisible `Blocks.BARRIER` with a
Polymer `ItemDisplayElement` drawn over it — which means a full-cube selection/collision outline and
a 20-step item-swap "lid animation" instead of real interpolation. Neither is fixable server-side.

A client that installs **the same jar** (plus Polymer client-side) now gets the real block instead:
exact chest hitbox, a real `ChestBlockEntity`, and vanilla-smooth lid animation from a normal Fabric
`BlockEntityRenderer`. Vanilla clients are unchanged.

## Why the client also needs Polymer

The rendering code is pure Fabric API. What needs Polymer client-side is *delivery* of the real
block state. Chunk packets carry block states as numeric `BLOCK_STATE_REGISTRY` palette IDs whose
values depend on the whole loaded mod set; the live server has ~100 mods, so a client with only this
one has different IDs. Polymer's login-time registry sync plus its real-state side channel
(`PolymerSectionUpdateS2CPayload`, gated on the client negotiating the polymer-core S2C protocol)
reconcile them. Without polymer-core client-side, planked chests arrive as the barrier substitute
like on any vanilla client — the mod jar alone does nothing.

Polymer stays a hard `depends` on both sides. A client without it fails to launch with a clear
message; client users install Polymer (one Modrinth file) alongside the jar. Not bundled via Loom
`include` — that would risk the live server loading a nested newer Polymer and breaking other mods.

## How it works, per connection

| Client | Handshake | `getPolymerBlockState` | Renders |
| --- | --- | --- | --- |
| Vanilla / Polymer-only | absent | `Blocks.BARRIER` | display entity (unchanged) |
| this jar + polymer-core | present | the real state | own `BlockEntityRenderer`; no display entity |

- `PlankedChestBlock implements PolymerClientDecoded` (an opt-in marker) so a polymer-core client
  keeps the real synced state instead of dropping it — the gate is in
  `PolymerClientProtocolHandler` (`libs/polymer`, `section.setBlockState` only when
  `checkDecode(block)`).
- `getPolymerBlockState(state, ctx)` returns `state` when `PlankedChests.HANDSHAKE.has(ctx)` says the
  client runs this mod, else the barrier — the `fabric_mod_skeleton` `PolymerExampleBlock` pattern.
- `ChestElementHolder.startWatching(ServerGamePacketListenerImpl)` returns `false` for
  handshake-positive connections, so those clients never receive the display entity.
- `ChestBlocks.registerSyncedBlockEntity` gates the block entity per connection —
  `(t, ctx) -> HANDSHAKE.has(ctx) ? t : null`. Without this the chest BE is stripped from chunk
  packets for *everyone* (Polymer's default for a synced BE type), so the mod client would get the
  real block state but no `ChestBlockEntity` for the renderer to draw.
- The real block model is particle-only (`ChestBlockStateProvider`), like vanilla
  `minecraft:block/chest`; the BER draws the chest. No `RenderShape` change.
- `PolymerModelBlockItem` is passed `HANDSHAKE::has`, so a mod client also sees the real *item*
  (correct name/tooltip), not the `Items.CHEST` fallback.

All four per-connection gates key on `HANDSHAKE.has(context)`, evaluated when each packet is built.
Polymer's registry sync finishes before a joining client's first chunks (observed in the dev log),
so the gates resolve correctly on join. A slow connection could in principle deliver spawn chunks
first; a handful of those chests would then show the display-entity fallback (as on a vanilla
client) until touched. That degradation is accepted rather than carrying a per-tick sweep or a
whole-world `reloadLevel` for it.

## rkcore pieces (shaded into consumers, relocated)

| Class | Role |
| --- | --- |
| `common/net/PolymerClientHandshake` | `create(modId)` / `register()` / `has(...)` — the per-connection "client runs my mod" flag (generalises the skeleton's `PolymerAware`). |
| `common/client/PolymerChestRenderer<T>` | `extends ChestRenderer<T>`; reuses vanilla's whole state extraction and only overrides `submit` to swap in a `ChestSpriteSelector` sprite through `Sheets.CHEST_MAPPER` (cached), instead of the fixed `ChestMaterialType` enum. |
| `common/client/PolymerChestRenderState` | `ChestRenderState` + a resolved `Identifier sprite`. |
| `common/client/ChestSpriteSelector<T>` | `Identifier sprite(T be, ChestType type)` — a mod resolves its own per-variant sprite (including the `_left`/`_right` half); the renderer only consumes it. `planked-chests` routes this through its existing `ChestModels.sprite(...)` so the naming lives in one place. |
| `common/item/PolymerModel[Block]Item` | + a `Predicate<PacketContext> useRealItem` ctor. |

`rkcore` is `environment: "*"`; Loom puts `net.minecraft.client.*` + the Fabric client rendering API
on its classpath, so `common/client` compiles. It ships in the runtime jar unused, loaded only by a
consumer's `client` entrypoint. `planked-chests`' `rkcoreSharedJar` filter now also lists
`common/client/**` and `common/net/**`.

## Server resource pack

`PolymerResourcePackUtils.markAsRequired()` was removed from this mod (correct for a client-optional
mod — a mod client never needs the pack). Whether vanilla clients may *decline* the pack and still
connect is the live server's `config/polymer/auto-host.json` (`required` / `mod_override`), a
server-wide setting affecting every Polymer mod — tracked separately, not by this mod.

## What this resolves

For a mod-installed client: the *Client selection box*, *Lid animation*, and *Right-click
ghost-placement flicker* open decisions in `planked-chests.md` — the barrier and display entity are
simply not used. Vanilla clients keep all three as-is.

## Follow-up: a chest *inside a cart*, for a mod client

Not built. Moved here from `chest-minecarts.md`'s open decisions, where it was recorded as blocked
on two things that the research below has since unblocked — so what is left is a scoping decision,
not an unknown. Nothing about it affects vanilla clients, who keep the stand-in and its tuned
`DEFAULT_RENDER_LAG` either way.

**What it would buy.** The cart cargo is drawn by a Polymer display element posed once per server
tick, so it is always a few ticks behind the cart the client is drawing, and `CargoDisplayHolder`
carries a rotation trail and a hand-tuned constant to compensate (S3's as-built has the full
mechanism). None of that is a property of carts — it is a property of the stand-in. Vanilla's
`AbstractMinecartRenderer.extractRenderState` runs *per frame* with `partialTicks` and resolves the
cargo from `getDisplayBlockState()` (`:103`, `:119`), and `submit` draws the cargo inside the **same
`PoseStack`** as the cart's interpolated rotation — `:59-67` nest under `newRender`'s `state.yRot` /
`state.xRot`, which come from `getCartLerpYRot(partialTicks)`. A real cargo block shares the cart's
matrix and therefore cannot drift at all: no trail, no constant, and the punch rock at `:54-57`
comes along for free because it is applied before the cargo.

**The two old blockers, and why neither holds.**

- *"`BuiltInBlockModels` builds its map at bootstrap with no Fabric hook, so a client mixin is needed
  first."* Still true as stated — `createBlockModels` returns `Map.copyOf(...)`, so a TAIL inject
  cannot append and you would cancel at RETURN with a merged map, or `@Invoker` the private
  `Builder`. But it is avoidable entirely: if the mod client receives the *real* entity type, this
  mod registers its own `EntityRenderer` for it, and `submitMinecartContents` is `protected`
  (`AbstractMinecartRenderer.java:159`). Draw the chest there and `BuiltInBlockModels` never enters
  into it.

  One caveat found later, the hard way: on a **vanilla** client the cart is presented as a plain
  `minecraft:minecart` and must stay that way, because a client told it is a container cart draws
  that cart's block under the stand-in and blanking the cargo slot to air does not stop it. See
  `chest-minecarts.md` S3's "Correction" — the behaviour is vanilla's, is not explained by the
  render path as written, and cost a wasted round trip. It does not block anything here, since a
  mod client would get the real type and the real renderer, but the vanilla path is not negotiable.
- *"S1 dropped the per-connection real-block-state route because `DATA_ID_CUSTOM_DISPLAY_BLOCK`'s
  numeric id needs an accessor mixin, and `rkcore`'s mixins are never shaded into consumers."*
  Correct, and also avoidable. That route was needed only to smuggle the cargo identity through
  *vanilla's* tracked-data slot. With the real entity type on the client, the cargo can ride a
  `SynchedEntityData` accessor this mod defines on its own entity class — no vanilla private field,
  no accessor mixin, nothing for `rkcore` to shade.

**The seam already exists.** `PolymerSyncedObject.getPolymerReplacement(object, PacketContext)` is
per-connection, so `CargoMinecarts.register`'s substitution lambda can hand a handshake-positive
client the real `plankedchests:chest_minecart` and everyone else `minecraft:minecart`. Then
`ChestCartDisplay.startWatching` returns false for those connections, exactly as `ChestElementHolder`
already does for placed chests. Same `HANDSHAKE.has(ctx)` gate as the other four.

**The actual cost is the lid, and it is the reason this is not a quick win.**
`ChestSpecialRenderer` bakes `openness` in at *bake* time — `Unbaked(texture, openness, chestType)`,
defaulting to `0.0F` — and implements `NoDataSpecialModelRenderer`, so it takes no per-frame data and
has no entity handle. Vanilla's cargo path draws a chest permanently shut, which is exactly what
`chest-minecarts.md` S7 exists to avoid. Taking that path as-is would trade the animated lid for zero
desync. Keeping both means a renderer of this mod's own, and the open state reaching the client:
`PlankedChestMinecart.openCount` is a plain field (`:49`) and would become synched data.

**One cross-effect worth knowing before starting.** New synched data on the cart is precisely what
triggers the off-schedule `ServerEntity` flushes described in `chest-minecarts.md` S3 — a short lerp
batch that the client still stretches over three ticks, walking the *vanilla* client's cargo delay by
up to three ticks until the schedule resettles. Only on open and close, so mild, but it is a
regression for the majority audience paid to improve the minority one, and it is miserable to
diagnose after the fact.

**Verdict.** Worth doing, on this track rather than as a cart change, and not urgent: vanilla clients
are the whole live audience and keep the tuned constant regardless. Revisit when someone running the
jar asks for it.

## Deferred: `supportsAll` gates key parity, not numeric-id parity

Found 2026-09-11, diagnosing a dev client being kicked on join with
`(Polymer) Failed to parse 'plankedchests:warped_chest' item! Invalid stack data!` and then
`DecoderException: Failed to decode packet 'clientbound/minecraft:custom_payload'`. Deferred
deliberately: nothing in the current cart run depends on it, and the fix is a handshake change that
wants its own pass. Recorded here rather than in `chest-minecarts.md` because the gate is this
track's, not the cart's.

**The gate, and what it actually proves.** Three places send real content on
`HANDSHAKE.supportsAll` — `ChestBlocks.java:169`, `ChestMinecartItem.java:39` and
`PlankedChestBlock.java:127`. `PolymerClientHandshake.supportsAll` answers whether the client
registered every key this side did, and its own Javadoc already warns that raw-id content needs more
than a per-key answer because "one absent mod shifts every id after it". That warning is correct and
insufficient: it describes a *different mod set*. The failure observed had **identical mod sets on
both sides** and differed only in the order Fabric ran their entrypoints.

**Why identical mod sets still diverge.** Fabric Loader's entrypoint order is hash-shuffled per JVM
launch, not sorted (the "Loading N mods" list is sorted only for display). Polymer partitions each
registry as `[vanilla][polymer entries in registration order]`, and Fabric never remaps
`minecraft:item` here — it logs `Skipping un-modded registry: minecraft:item` on good and bad joins
alike, because every modded item present is a Polymer entry. So registration order *is* the wire id,
and two launches of the same build disagree about it. Verified across four dev-server runs:

| run | entrypoint order | joins |
| --- | --- | --- |
| 22:02, 22:52 | `plankedchests` before `minecarttweaks` | fine |
| 22:48, 23:27 | `minecarttweaks` before `plankedchests` | kicked |

`minecarttweaks` contributes three Polymer items, so on the second ordering every planked-chests item
sits three higher on the server than on the client, and the last three — `warped_chest`,
`warped_trapped_chest`, `chest_minecart` — fall off the end of the client's registry.
`warped_chest` is simply the first, and the twenty-two entries before it decode *silently to the
wrong wood*, which is why nothing earlier appears in the log.

**Why `supportsAll` lets it through.** Polymer's item sync writes each entry's representation stack
with `ItemStack.OPTIONAL_STREAM_CODEC`, which Polymer's own `ItemStackPacketCodecMixin` routes
through `PolymerModelBlockItem.getPolymerItem`. That returns `this` — the server-side item, encoded
by raw id — precisely when `supportsAll` is true. A client that *fails* the gate gets the vanilla
fallback, whose id is below the divergence, and is unaffected: minecart-tweaks' own dev client, which
has no planked-chests, joined the same failing server run and played for eighteen minutes. The gate
is what selects for the crash.

**This is not only a dev-rig problem.** The rig makes it reproducible, but the premise — same mod
set, same build, ids agree — is false on any server whose clients load a different set, and the live
server runs `minecarttweaks` while the client profile cannot.

**The shape of the fix, when it is picked up.** Two halves, independent:

- *Items.* Stop returning `this` from `getPolymerItem`. A mod client already gets everything it
  renders from the fallback item plus `ITEM_MODEL` / `ITEM_NAME`, so the real id buys nothing and
  costs this. Cheap, and removes the item half entirely.
- *Block states.* These genuinely need the real id, so gate them on an id **fingerprint** negotiated
  through the handshake — a marker whose "version" is the server's own raw id for a known entry
  (`oak_chest`, say), registered after registries freeze — rather than on key presence. Both sides
  compute it from what they actually registered, so a reordering fails the gate instead of
  corrupting the stream.

**Not re-derived here.** The mixin chain, the vanilla item count of 1537 and the per-run registry
dump under `run/.fabric/debug/registry/` come from the diagnosis and were not independently checked;
the order table, the absent restart and the unaffected fallback client were.

**Verdict.** Deferred until the cart run finishes. Worth doing before any further reliance on
`supportsAll` for raw-id content, and worth doing before the chest-inside-a-cart follow-up above,
which would add a fourth such site.
