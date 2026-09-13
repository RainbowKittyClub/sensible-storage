# Chest minecarts

A chest minecart per `WoodType`, carrying that wood's chest instead of the vanilla oak one, built on
a general "a minecart carrying an arbitrary block" mechanism that lives in `rkcore` and is relocated
into this mod like the other shared packages. The 27 are craftable, give-able, pick-block-able and
browsable in this mod's own creative tab, alongside a 28th carrying vanilla's chest. Vanilla's own
chest minecart is no longer what `chest + minecart` crafts, and is renamed "Minecart with Chest
(Legacy)" so the two can be told apart — see S6. The same cart also carries the barrel, the eight
copper chests and the seventeen shulker boxes, and refuses any cargo that is not chest-like — see
S8.

Claims here were verified against `libs/decompiledjava/minecraft-26.2/`, the assets inside the 26.2
deobf jar, and the `libs/polymer` checkout (`dev/26.2`). Two things were stated from source and not
observable without building: where the client actually draws a display element mounted as a minecart
passenger, and how much the cargo's yaw lags the cart through a curve. Both were S3 questions and
**both are now settled in world** — see S3's as-built, which also records why neither could have been
read off correctly beforehand.

Stage order: S1 (rkcore machinery) → S2 (entity, item, recipe — full server-side gameplay, the cart
shows a plain vanilla chest) → S3 (the cargo display element; **S3 is what makes the feature
complete**) → S4 (hoist the generic half of S2 into `rkcore`) → S5 (item icons) → S6 (lang, tags,
lint, creative tab). S4 sits after S3 deliberately: S3 is the first thing that exercises the shared
display machinery for real, and a shared interface published before its first consumer has run is how
S1 already got one member wrong. S5 depends on S4 for the per-stack model hook it overrides; it was
also expected to wait on a piece of hand-authored art, which turned out to be derivable from
vanilla's own two cart sprites — see its as-built. S7 needs only S3 and was built straight after it,
out of numeric order — it is the same display pointed at one more sprite. S8 (guard the cargo, and
barrel / shulker box / copper chest carts) came out of S4 and was wanted rather than needed, so it
went last. S9–S13 each take one cargo further than S8 left it and run in that order: S9 (shulker
boxes draw their own box) and S10 (one rule for every cart icon) are independent of each other, S11
(barrel carts open) needs S10 for its icon, S12 (copper chest carts draw their own chest) needs
nothing but S8, and S13 (copper cargo weathers) needs S12. S14 (trapped chest carts, and a
comparator that reads them) needs S10 for its icon rule and nothing else, and reverses S2's decision
not to build them at all. **Every stage is done.** Ship order is `rkcore` first
(`publishToMavenLocal`), then this mod, for S1, S3, S4, S6 and S8 alike; S9–S14 are this mod alone.

S9–S12 were written up after the work rather than before it, from the code and from the session that
built them — so their **Build** text is what was decided at the time and not a plan that preceded
the stage, and the delta the "As built" sections usually carry is smaller than it looks. Everything
in them was re-verified against the files before it went in. S13 is the first of this run written
the intended way round.

Chest boats were assessed alongside this and deferred; the findings that decided that are kept in
"Reference: chest boats" so the question does not have to be re-researched.

## Findings that reshape the work

**A minecart's cargo slot is data, even though the chest drawn in it is hardcoded.**
`BuiltInBlockModels.java:87` maps `Blocks.CHEST` to a `ChestSpecialRenderer` unbaked model, so a new
chest block can no more render inside a cart than it can when placed. But the cart's cargo is a
synched `Optional<BlockState>` (`AbstractMinecart.java:52`, `DATA_ID_CUSTOM_DISPLAY_BLOCK`), and
`AbstractMinecartRenderer.java:119` resolves whatever `getDisplayBlockState()` returns and skips it
when the resolved model is empty (line 60). So the server can blank the cargo to air and draw its
own — and, for a client that *can* render the real block state, send that instead. That per-connection
choice is the whole of what `rkcore` owns here, and it is why the interface is "a block in a cart"
rather than "a chest in a cart".

**Polymer already mounts holder elements as passengers, per connection.**
`ElementHolder.addPassengerElement` (`ElementHolder.java:289`) records element ids that
`EntityPassengersSetS2CPacketMixin:45-50` injects into `ClientboundSetPassengersPacket`, filtered to
that holder's watching players. The cargo therefore tracks the cart with no follow lag — the client
positions it as a rider — and the existing `startWatching` handshake gate keeps working unchanged.
This is the difference between the minecart being easy and the boat being hard.

**A cart's passenger attachment point is a constant, so the cargo offset is a constant.**
`EntityTypes.java:286` builds `chest_minecart` with `passengerAttachments(0.1875F)`, and
`AbstractMinecart.java:180` only departs from it for villagers and wandering traders — which a
display element is not. One fixed compensating translation puts the cargo where vanilla draws it.

**These entities must define no synched data.** `SynchedEntityData.assignValues` indexes
`itemsById[item.id]` (`SynchedEntityData.java:110`); an id past the range of the class the client
thinks it is watching is an `ArrayIndexOutOfBoundsException` on that client. The wood therefore
lives in a plain field, saved to NBT, never in an `EntityDataAccessor`.

**The vanilla recipe does not conflict this time.** `data/minecraft/recipe/chest_minecart.json` names
`minecraft:chest` exactly, not `#minecraft:planks`, so a `<wood>_chest + minecart` shapeless recipe
is unambiguous. None of the `data/minecraft/recipe/` overrides that `planked-chests.md`'s own S6
needed are required here.

**`minecart-tweaks` needs no change.** Its mixins target `AbstractMinecart`
(`AbstractMinecartMixin.java:41`) and `MinecartItem` (`MinecartItemMixin`), not concrete cart types,
so a `MinecartChest` subclass placed by a `MinecartItem` subclass inherits chain linking, train
mechanics and the length limit for free. Worth an in-world check anyway, and it is a gameplay check
rather than a display one, so it belongs to S2's round and not S3's.

**The wood belongs on the stack, not in 27 item ids — and vanilla carries it the whole way.** A
custom `DataComponentType` marked server-side-only with `PolymerComponent.registerDataComponent`
(`PolymerComponent.java:13`) is stripped from client-bound stacks at the component-codec level
(`ComponentMapMixin:37`, `DataComponentPatchMixin:46`), so it is safe on any stack, not just a
Polymer item's. Placement needs no interception: `EntityType.appendComponentsConfig`
(`EntityType.java:145`) calls `entity.applyComponentsFromItemStack` on every cart the vanilla item
spawns, and `Entity.applyImplicitComponents` (`Entity.java:4033`) is `protected` — the entity reads
its own wood off the placing stack. Recipes need no custom serializer either: a shapeless recipe's
result is an `ItemStackTemplate` carrying a `DataComponentPatch` (`ItemStackTemplate.java:19`,
`ShapelessRecipeBuilder.java:31`). The one place vanilla does not carry it is the *drop*:
`VehicleEntity.destroy(ServerLevel, Item)` (`VehicleEntity.java:68`) builds a bare `new
ItemStack(dropItem)`, so the entity has to override `destroy` and rebuild the stack itself.

**No new chest art is needed; one new piece of icon art is.** The cargo reuses the per-wood base and
lid item models this mod already generates (`ChestModels.baseModel` / `lidModel`). What has no
existing source is the *item icon*: vanilla's `textures/item/chest_minecart.png` has the chest baked
into the sprite, so per-wood icons need a hand-authored 16×16 overlay in the same style as the
`plankedchests_overlay/` art already in the datagen source set. That is the one external dependency
in this plan.

**That last sentence was wrong, and S5 shows why.** No art was authored. Vanilla ships the same cart
twice — `item/minecart.png` is the chest one without its chest — so the chest's pixels are exactly
the pixels the two differ in, and the overlay is derived from them at datagen time. This plan has no
external dependency.

## How these stages get tested

Dev server via `scripts/devserver.sh rkc/mods/planked-chests <cmd>` (start / restart / rcon / logs),
watched in world by the user on their client — brief them per leg before each test round. No test
suite. A stage is done only when its "Done when" is observed in world or in a file listing; a
compile is not an exit criterion, so a failure at stage N implicates stage N, not S1–S(N-1).

`minecarttweaks-0.1.0.jar` was copied from that mod's `build/libs/` into
`rkc/mods/planked-chests/run/mods/` so linking and trains can be observed here rather than waiting
for the live server. It is a *copy*, so it goes stale: rebuild it there and copy it again after any
change to that mod. Its game rules answer over rcon (`gamerule minecarttweaks:trains_enabled` →
`true`, `max_train_length` → 128) and the world already has the `minecart_improvements` feature pack
on, which is what its mechanics assume.

`./gradlew build` needs the sandbox bypassed (`~/.gradle` writes). S1 and S4 both change shared
`rkcore` code, so `cd rkc/mods/rkcore && ./gradlew publishToMavenLocal` has to run before this mod
sees any edit to it — and dev mode runs against the un-relocated classes off the compile classpath,
so the relocation itself is only exercised by installing a real `shadowJar` jar.

## S1 — rkcore cargo-minecart machinery — **done**

**Build.** A new shared package `club.rainbowkitty.rkcore.common.vehicle`, alongside the existing
`common/item`, `common/datagen`, `common/net` and `common/client`. It must register nothing on its
own — every registration takes the consumer's namespace as a parameter, the way `ItemRegistration`
does, because consumers shade and relocate it rather than depending on the mod.

- `CartCargo` — the interface a consumer's minecart implements. Three things: the `BlockState` the
  cart carries, a `Predicate<PacketContext>` for "this connection can render that state itself", and
  a factory for the Polymer stand-in holder used when it cannot.
- `CargoDisplayHolder extends ElementHolder` — the stand-in base. Adds its elements through
  `addPassengerElement` so Polymer mounts them, applies the fixed attachment-point compensation, and
  pushes the cart's yaw to the elements each tick. `startWatching` returns false for connections that
  can render the real state. The consumer subclasses it and supplies the elements and their models;
  the base knows nothing about chests.
- `CartCargoSupport` — the glue a consumer's entity holds, since `MinecartChest` is already the
  superclass and a `rkcore` base class cannot be. It blanks `setCustomDisplayBlockState` to air,
  creates and destroys the holder with the entity, answers `getPolymerEntityType`, and swaps the
  real block state back into the tracked data for capable clients via
  `PolymerEntity.modifyRawTrackedData`.
- `PolymerMinecartItem extends MinecartItem implements PolymerItem` — the same fallback / model /
  `useRealItem` shape as `common/item/PolymerModelItem`, so a vanilla client sees
  `minecraft:chest_minecart` carrying our model.
- `CargoMinecarts.register(...)` — entity type registration under the consumer's namespace plus the
  `PolymerEntityUtils.registerType(type, syncedObject)` mapping.

The exact split between `CartCargo` and `CartCargoSupport` is the part most likely to be wrong on
paper; settle it by building S2 against it and moving the seam if the consumer ends up passing the
same three things twice.

**Done when.** `cd rkc/mods/rkcore && ./gradlew build publishToMavenLocal` succeeds and
`~/.m2/repository/club/rainbowkitty/rkcore/rkcore/0.1.0/rkcore-0.1.0.jar` contains
`club/rainbowkitty/rkcore/common/vehicle/`. `rkcore`'s own dev server still boots — nothing in the
new package runs unless a consumer calls it.

### As built

Five classes in `common/vehicle`: `CartCargo`, `CargoDisplayHolder`, `CartCargoSupport`,
`CargoMinecarts`, `PolymerMinecartItem`. Build green, `checkstyleTarget` clean after one 101-column
Javadoc line, `rkcore-0.2.0.jar` (not the `0.1.0` the exit criterion names — `rkcore`'s own version
had already moved on, and `planked-chests` pins `rkcore_version=0.2.0` so it picks this up) carries
all five, and the dev server boots to `Done (0.330s)! For help, type "help"` with zero mixin or
error lines and an answering rcon.

Four departures from the Build text:

- **`CartCargo` has two members, not three.** The planned per-connection "can this client render it"
  predicate is gone: the gate belongs on the stand-in, where a subclass overrides `startWatching`,
  which is exactly where `planked-chests`' existing `ChestElementHolder` already puts its handshake
  check. A predicate on the interface as well would have been a second way to say the same thing.
- **The per-connection *real block state* route was dropped, and the two routes are now chosen per
  cart instead.** It is not buildable in shared code: swapping that one entry in the tracked data
  needs the numeric id of `AbstractMinecart.DATA_ID_CUSTOM_DISPLAY_BLOCK`, which is
  `private static final` and reachable only through an accessor mixin — and `rkcore`'s mixins are
  never shaded into consumers, nor could a relocated mixin be registered by one. So
  `CartCargoSupport` reads `cargoState()` into vanilla's cargo slot when there is no stand-in, and
  blanks the slot to air when there is. This costs nothing yet: a capable client could not render a
  planked chest in a cart anyway, for the `BuiltInBlockModels` reason already under Open decisions.
  Reviving the per-connection route means reviving it there, together.
- **The cart entity does not implement `PolymerEntity`.**
  `PolymerEntityUtils.registerType(type, syncedObject)` answers the spawn packet from the synced
  object whenever the entity is not itself a `PolymerEntity`, so the one lambda passed at
  registration covers both registry sync and spawn. S2's Build text is edited accordingly.
- **`CargoMinecarts.containerCartBuilder` was added**, unplanned. The vanilla container-cart
  dimensions and attachment point have to match on both sides — the client positions a mounted
  cargo element from the attachment point of the type it *thinks* it is watching — so the numbers
  belong in one place rather than in each consumer.

One thing deliberately not done: `ElementHolder.updatePosition` still runs. Mounted elements are
positioned by the client, so its per-tick move packets are redundant, but the initial spawn packet
reads the holder's position and the saving is a couple of packets per cart per tick. Revisit only
if cart-heavy chunks show up in a profile.

**That last paragraph was wrong, and S3 corrects it.** Those packets are not merely redundant: they
aim at the cart's own position rather than the passenger point the client is drawing the element
at, and the client honours one for the frame before it puts the rider back — a drop and snap on
every tick of movement. `addCargoElement` now calls `ignorePositionUpdates()` on what it is given.

## S2 — Entity, item and recipe — **done**

**Build.** Consume S1 from this mod: add `include 'club/rainbowkitty/rkcore/common/vehicle/**'` to
the `rkcoreSharedJar` filter in `build.gradle` (line 48), next to the four already listed.

The wood rides on the item stack as a component, not in the item id — **one** registered item for
all 27 woods.

- `cart/CartWood` — a `DataComponentType` holding the wood, registered under this mod's namespace
  and passed to `PolymerComponent.registerDataComponent` so it never reaches a client.
- `cart/PlankedChestMinecart extends MinecartChest implements CartCargo` — holds its `WoodType` in a
  plain field written to and read from NBT (never synched — see the findings), and overrides
  `applyImplicitComponents` to take the wood off the placing stack. Holds a `CartCargoSupport`,
  driven from `tick`, `remove` and whenever the wood changes. Its stand-in holder is where the
  `PlankedChests.HANDSHAKE` gate goes, via `startWatching`. Overrides `destroy(ServerLevel,
  DamageSource)` to
  drop a stack carrying the component, since `VehicleEntity`'s drop would not. Overrides `getName()`
  so the container menu reads "Birch Chest Minecart" (`Entity.getDisplayName():3285` reads
  `getName()`; the vanilla default is the entity type's description id, shared by all 27 woods).
- One entity type `plankedchests:chest_minecart`, registered through `CargoMinecarts.register` and
  mapped to `EntityType.CHEST_MINECART` for clients that do not run this mod.
- One item `plankedchests:chest_minecart` — a `PolymerMinecartItem` built with our entity type,
  fallback `Items.CHEST_MINECART`. It derives the per-wood presentation from the component per
  packet rather than storing it: `getPolymerItemModel(stack, …)` returns
  `plankedchests:<wood>_chest_minecart`, and `getPolymerItemStack` adds an `item_name` holding a
  translatable `item.plankedchests.<wood>_chest_minecart` with an English `fallback` string
  (`TranslatableContents.java:41`), so a client that declined the server resource pack still reads a
  real name instead of a raw key. Deriving beats baking the presentation into the recipe results:
  one source of truth, and a wording change does not mean regenerating 27 recipes.
- `ChestRecipeProvider`: 27 shapeless `<wood>_chest + minecraft:minecart` recipes whose result is an
  `ItemStackTemplate` carrying the wood component. No custom recipe serializer, and no
  `data/minecraft/recipe/` override — see the findings.

Why one item and not zero. The vanilla `minecraft:chest_minecart` item spawns the entity type baked
into it, a plain `MinecartChest` with nowhere to put a wood; getting our entity out of it means
either a mixin or intercepting `UseBlockCallback` and re-implementing the rail, slope, sound, stat
and creative-consumption body of `MinecartItem.useOn`. The mod has no mixins today and copied vanilla
runtime behaviour is exactly what this workspace's conventions warn about. Constructing `MinecartItem`
with our own type costs one registry entry and keeps every line of vanilla's placement code.

Trapped chests get no cart — vanilla has no trapped chest minecart and a trapped chest has no
redstone meaning inside one. With the wood on the stack that is now a question of 27 recipes versus
54, not of item count. **S14 reverses this**: the first half is still true and the second half was a
decision rather than a fact, so S14 gives a trapped cart the redstone meaning it was said to lack.

**Done when.** In world: `/give plankedchests:chest_minecart` with a birch component produces one
item named **Birch Chest Minecart**; placing it on rails gives a rolling cart that looks like a
*plain empty minecart* (the cargo is blanked here and only drawn in S3). Right-click opens a 27-slot
menu titled Birch Chest Minecart; a hopper under the rail pulls items out; breaking it drops an item
**still named Birch Chest Minecart** along with its contents; `<birch chest> + <minecart>` crafts one
and a vanilla chest still crafts a vanilla chest minecart. Then drag the item around a creative
inventory and place it again — that round trip is what proves Polymer's `$polymer:stack` is carrying
the component back, and it is the one failure mode this design has that 27 items would not.

### As built

Built as described and observed in world. The cart rolls on rails like a vanilla one; right-click
opens a 27-slot menu titled "Minecart with Birch Chest"; a hopper under the rail pulls from it;
`<birch chest> + <minecart>` crafts one and a vanilla chest still crafts a vanilla chest minecart;
breaking one drops a cart item still carrying its wood, alongside its contents. Moving a `/give`n
cart between slots in `gamemode=creative` preserved the component. With `minecart-tweaks` installed,
two of them link into a train with an iron chain and a plain right-click still opens the chest —
that mod's mixins on `AbstractMinecart` and `MinecartItem` reach a `MinecartChest` subclass exactly
as the findings predicted, with no change to either mod.

Verified over rcon before that round: the cart summons named per wood, `Wood` round-trips through
saved data, `CartCargoSupport` applies the cargo (`DisplayState` reads `minecraft:chest`), the
component parses from a command string, destroying one drops `plankedchests:chest_minecart` carrying
`{"plankedchests:wood": "birch"}`, `generateAssets` writes 27 recipes with the wood in the result
and mod-loaded conditions on the modded woods, and the dev server boots with no recipe or load
errors.

Three corrections to the text above.

**The creative round trip the exit criterion describes cannot happen, and no later stage inherits
it.** An item dragged into the creative panel is destroyed, not returned; a stack only comes back
out of that panel if something put it there, and nothing will — the carts go in no creative tab
(S6). What is left of the round trip is a creative-mode player moving a `/give`n cart between slots,
because in creative the client sends its own copy of the stack back in a
`ServerboundSetCreativeModeSlotPacket` rather than asking the server to move it, and only Polymer's
stashed `$polymer:stack` carries the wood through that. That is what was tried, and it held.

**"No later stage inherits it" did not hold.** S6 puts the carts in the tab after all — the
no-creative-tab decision was retracted once S5 gave every wood its own icon — so a stack does come
back out of that panel now, and the full round trip this criterion describes is reachable and is
part of S6's "Done when". Nothing above is wrong about the mechanism; only about it staying
unreachable.

**The name follows vanilla's shape, not the one first built.** Vanilla qualifies the cargo rather
than the vehicle — `chest_minecart` is "Minecart with Chest", and `oak_chest_boat` is "Oak Boat with
Chest", where the wood belongs to the boat it names. A cart carrying a birch chest is therefore
"Minecart with Birch Chest", not "Birch Chest Minecart"; the cart has no wood of its own.

**The `minecart-tweaks` check belonged here, not in S3's "Done when" where it was first written.**
Linking is gameplay and owes nothing to the cargo display, so it had no reason to wait behind it.
S3's Build text is edited to keep only the visual half — the chain elements and the cargo elements
on one entity, which is a genuinely new combination.

Five departures from the Build text:

- **The cart shows a vanilla chest, not an empty cart.** `cargoState()` returns
  `getDefaultDisplayBlockState()` and `createCargoDisplay()` returns null, which takes S1's vanilla
  route. It has to: nothing remaps block state ids inside entity data — Polymer's
  `ClientboundSetEntityDataPacketMixin` rewrites item stacks and villager data only — so a planked
  chest state would resolve to an unrelated block on a vanilla client. The "plain empty minecart" in
  the exit criterion above is wrong; a normal-looking chest minecart is the right S2 state, and S3
  flips both methods at once. `CartCargo`'s Javadoc gained this constraint.
- **Per-wood naming is `Item.getName(ItemStack)`, not a Polymer stack hook.**
  `PolymerItemUtils.createItemStack` sets `ITEM_NAME` from the *server* stack's own name
  (`PolymerItemUtils.java:455`) after the hooks that modify the client copy run, so a name written
  in `modifyBasePolymerItemStack` is overwritten. Naming the server-side stack is also what makes
  command output and the container menu agree with the item.
- **`PolymerMinecartItem`'s model is now nullable** — Polymer documents null as "keep the fallback
  item's model", which is what this needs until S5 generates per-wood icons.
- **The component class is `CartComponents`**, holding a `WOOD` field, rather than the planned
  `CartWood`; a second cart component would otherwise need a second class.
- **`ChestCarts.displayName` builds the name for both the item and the entity**, so the cart's
  container title and its item can never drift apart.

## S3 — The cargo display element — **done**

**Build.** Extract the base/lid transform maths in `display/ChestElementHolder` — the `HINGE`
offset, `RENDERER_YAW_OFFSET`, `yawFor`, the openness tween and `applyTransforms` — into a
`display/ChestVisual` used by both the existing block-bound holder and a new cart-bound one, rather
than copying it. `cart/ChestCartDisplay extends CargoDisplayHolder` then supplies the two
`ItemDisplayElement`s from `ChestModels.baseModel` / `lidModel` for `ChestType.SINGLE` and drives the
lid from the cart's own `getOpenCount`, so a cart chest opens like a placed one.

Two unknowns to resolve by building, not by guessing:

- Where the client actually puts a mounted display element. The attachment point is the constant
  0.1875 above the cart, but the renderer also scales the vanilla cargo by 0.75 and offsets it by
  `(displayOffset - 8) / 16` (`AbstractMinecartRenderer.java:60-64`), and none of that applies to a
  passenger. Expect to find the compensating translation empirically against a vanilla chest
  minecart placed beside ours.
- Yaw through a curve. Passengers get their position from the client but not their rotation, so the
  cargo's yaw is pushed once per tick like the placed chest's is. `setInterpolationDuration` should
  smooth it; whether it still visibly trails on a tight curve at speed is the thing to watch.

**Done when.** In world: a birch chest minecart rolls along a curved track with a birch-textured
chest sitting in it, no visible lag or float; right-clicking it opens the lid and it closes with the
menu; a spruce one looks spruce; the cart still links to a neighbouring cart with a chain and runs
in a train with the link chain drawn between the carts and the chest still sitting straight in each
— the `minecart-tweaks` *gameplay* check belongs to S2, but its chain visual and this cargo are two
sets of virtual elements on the same entity and have never been seen together. This is the stage
that makes the feature complete.

### As built

Recorded round by round as it was tested, so it reads as a log rather than a description; the last
round is where it ended up.

Built and compiling; nothing below has been looked at yet. `display/ChestVisual` holds the two
elements, the model naming, the lid tween and the pose maths, and hands the hinge back to its holder
through a `HingePlacement` — because the two holders position their elements differently and only
that differs. `ChestElementHolder` keeps `setOffset`, its proven form; `cart/ChestCartDisplay` has
to use the transformation's own translation instead, since a passenger's position is the client's to
decide. `PlankedChestMinecart` counts menus through `startOpen`/`stopOpen` (vanilla keeps no such
count for a cart, and `ChestMenu` calls both) so the lid has something to follow, and falls back to
the vanilla chest when the wood's mod is absent rather than naming a block that is not registered.

**`minecart-tweaks` had already solved most of this, and reading it first would have saved three
bugs.** Its `visual/LinkChain` also hangs virtual elements off a moving cart, and it supplied:
`ignorePositionUpdates()` on every ridden element (see the correction appended to S1); a
`ClientboundSetPassengersPacket` re-announce, because riders are declared to a client only when it
starts tracking the cart, and this cargo is built on the cart's first tick when nearby clients are
already tracking — without it the elements spawn and never mount; and the number for the yaw
question this stage was meant to answer by eye. A cart's rotation reaches the client about **3.5
ticks** late (three lerp steps flushed every third tick, replayed one per tick, plus a network hop),
so posing the cargo from the cart's current yaw runs it *ahead* of the cart being drawn. The
correction is a five-sample rotation trail in `CargoDisplayHolder`, mixing the two samples either
side of 3.5.

The other unknown was settled on paper rather than by eye, which is worth flagging as the weaker
kind of answer: the chest faces **along** the cart's body, not across it. Vanilla turns the cargo by
the cart's yaw and then 90° more, and a cart's yaw is built mirrored — its long axis is
`(-cos yaw, *, sin yaw)`, not the usual `(-sin yaw, *, cos yaw)`, per `LinkChain#lengthwise` — so
the two cancel to a facing yaw of 90 minus the cart's. If that is wrong the chest will sit square
across the cart, which is unmistakable. The slope roll is the least-verified line in the file and
only a sloped rail can settle it.

First round in world. Confirmed: the facing, the height and the scale all match a vanilla chest
minecart parked beside ours; no jitter; the lid opens and closes with the menu; the woods are
distinct; and the chain and the chest draw together on a linked pair. Two things were wrong, and
both are now fixed.

- **The chest spent a few ticks rotating into place as it spawned.** A display element is created
  carrying no transform at all, so every watching client was sent an unturned chest and then
  interpolated it round. The pose now happens in the holder's constructor, before it is attached and
  therefore before anything is sent; `CargoDisplayHolder.poseNow()` exists to be called there and
  says so. This is the same reason `LinkChain` aims in *its* constructor, which should have been the
  hint.
- **On a slope the chest tipped about the wrong axis.** The roll was composed *after* all the
  turning, which leaves its axis 90° from the slope. Vanilla's order is yaw, then roll, then the
  cargo's own 90° turn, and the roll has to stay between the two turns: `YP(yaw) · ZP(-pitch) ·
  YP(90)`. That reduces to the flat case already confirmed, so it is a strict improvement on the
  guess even if the sign of `-pitch` still turns out to need flipping.

Second round. The axis and direction of the roll are right, the spawn is correctly turned, and the
vanilla variant reads as vanilla. Three more things, two of which had a common cause.

- **The cargo still slid upward into place on spawn, and out to the cart's edge as it turned.** Both
  are `setInterpolationDuration(2)`, carried over from the placed chest: the client eases between
  transforms, so it eased from the default transform on spawn, and eased between poses as the cart
  turned. A placed chest can afford that; a cart cannot, because its own rotation reaches the client
  in whole-tick steps and a cargo easing between those steps slides around inside it rather than
  sitting in it. The cart's elements now interpolate over **0** ticks and take each pose as it
  comes.
- **On a slope the chest walked out over the end of the cart.** Separate from the roll: vanilla's
  cargo centre swings with the cart, and ours was translated a fixed 0.5625 *vertically* from the
  passenger point. `applyPose` now turns that offset by the cart's frame before using it. The
  passenger point itself does not swing — an attachment of `(0, y, 0)` is yaw-invariant and pitch
  never reaches it — so only the offset needs turning.
- **A vanilla chest was still being drawn underneath the stand-in.** The cart was presented to
  clients as `minecraft:chest_minecart`, and a container cart draws its own block whenever the cargo
  slot is not overridden — blanking the slot to air is not the same as the client having nothing to
  draw. It is now presented as a plain `minecraft:minecart`, which has no cargo of its own and is
  otherwise identical to a client: same model, same hitbox, same `passengerAttachments(0.1875F)`
  (`EntityTypes.java:665` against `:284`). `CargoMinecarts.register`'s Javadoc gained this, since it
  applies to any cart that draws its own cargo, not just this one.

Third round, all three of them refinements of the second.

- **The render lag is a little under the 3.5 `LinkChain` uses**, and was found by eye on a curve at
  speed because there was never another way to find it: 3.5 trailed by a tick, 2.5 led clearly, 3
  still led slightly. A chain is placed *between* two carts and waits out a hop of its own, where
  this rides one cart and is drawn from it. `CargoDisplayHolder.DEFAULT_RENDER_LAG` currently reads
  3.25, and the trail is sized for anything up to 8 so the figure can move without resizing it.

  Because each value cost a rebuild and a reconnect, the figure is being tuned through a scoreboard:
  `ChestCartDisplay` overrides `renderLag()` to read `cargolag` for the fake player `lag`, in
  hundredths of a tick, falling back to the default when the objective is absent.
  **That override is temporary and has to come out before this stage is done** — only the
  `renderLag()` seam in `rkcore` stays, and it earns its place as the hook a cart with different
  sync behaviour would need anyway. The objective itself lives only in the dev world.
- **The cargo still rose into place on spawn, because it was spawning at the cart's feet.** Posing
  in the constructor fixed the rotation but not the position: an element is spawned at the holder's
  own position, and a client only lifts a passenger to the seat once it has processed the passenger
  packet — with position updates off, nothing corrects it in between, so the chest appeared inside
  the cart's floor and climbed out. `addCargoElement` now gives every element a
  `PASSENGER_ATTACHMENT_Y` offset, which is where the client is going to put it anyway.
- **The lid can ease after all, on the ticks that matter.** A display's translation, rotations and
  scale share one `interpolationDuration`, so the lid angle cannot be smoothed independently of the
  cart's rotation — but the two rarely move on the same tick. A tick where the cart has not turned
  is a tick where the only thing that moved is the lid, and easing that is free: there is no
  rotation for it to slide against. `applyPose` compares against the rotation it last posed from
  and sets the lid's duration to 2 or to 0 accordingly. The base never eases.

**Punching a cart rocks it, and the cargo does not rock with it. This is being left alone.** Vanilla
rolls the cart's whole frame by `sin(hurtTime) · hurtTime · damageTime / 10 · hurtDir`
(`AbstractMinecartRenderer.java:56`) before drawing the cargo, so a vanilla cargo does rock. The
inputs are synched data this mod can read, so it could be reproduced — but it is computed per
*frame* from `hurtTime - partialTicks`, and a stand-in can only be re-posed per tick, so a 20 Hz
copy would visibly stutter against the smooth cart, and now that interpolation is off it would
stutter harder. The deciding argument is the other one: vanilla does not rock *passengers* either.
`Entity.positionRider` sets a rider's position and nothing else, so a villager in a cart you punch
sits dead still while the cart rocks around it. A cargo that rides the cart behaving the same way is
consistent with the rest of the game rather than a gap in it. Revisit only if the cargo ever stops
being a passenger.

Fourth round, and the last. **The render lag settled at 2.8**, found by moving `cargolag` in world
until the chest stopped visibly leading or trailing on a curve at speed; 2.8 was as close as the eye
could get. `DEFAULT_RENDER_LAG` now reads 2.8, the scoreboard override and its two constants are
gone from `ChestCartDisplay`, the objective is dropped from the dev world, and only the `renderLag()`
seam in `rkcore` remains. The knob was worth building: it turned a rebuild-and-reconnect per
candidate into a chat command, and the last three rounds of this stage would otherwise have been ten.

Asked at that point whether the figure could ever be exactly right, and whether a player's ping
feeds into it. Read out of 26.2 rather than guessed, and written into the constant's Javadoc because
it is the thing a future reader will most want and least be able to reconstruct:

- **The pipeline is fixed, not a sawtooth.** The server appends one `MinecartStep` per tick and
  flushes the batch on the tracker's `updateInterval`, 3 for a cart (`ServerEntity.java:125`,
  `EntityType.java:482`); `NewMinecartBehavior` replays a batch across exactly three client ticks
  (`lerpDelay = 3`, `alpha = (3 - lerpDelay + partialTick) / 3`, `NewMinecartBehavior.java:78,122`).
  Accumulation and replay run at the same rate, so the batching contributes a *constant* delay — the
  client starts a batch on its oldest step, which is two ticks old — rather than the ±1 tick cycle
  one might expect from the word "batch". The first guess in the third round, that the batching was
  the jittery part, was wrong.
- **Ping mostly cancels.** The cart's `ClientboundMoveMinecartPacket` and this cargo's transform are
  produced in the same server tick and go out in the same flush, so they cross the wire together and
  land together. A player on 200 ms sees both 200 ms late and the offset *between* them is untouched.
  Starvation cancels the same way: when the client's queue drains, `cartHasPosRotLerp()` goes false
  and the cart freezes — and the cargo, having had no packet either, freezes with it.
- **What does not cancel, and why no constant can fix it.** A cart interpolates its rotation *within*
  a tick, from `partialTicksInStep`, so it draws a fresh rotation every frame; a display element with
  an interpolation duration of zero snaps once per tick and crosses the cart's curve twice a tick
  instead of following it. That is about half a tick of error, oscillating at frame rate, and it is
  the floor on how good this can look. Raising the lid's trick — easing on ticks where the cart has
  not turned — to the base is not available, since easing the base is exactly what the second round
  removed.
- **The one real inconsistency is the flush schedule, not the network.** `ServerEntity` flushes on
  `tickCount % updateInterval == 0` **or** `needsSync` **or** dirty synched data. An off-schedule
  flush ships a batch of one or two steps, and the client still spends three ticks on it
  (`lerpDelay` is a flat 3 regardless of batch size), so the cart plays back in slow motion and the
  effective delay walks by up to three ticks until the schedule resettles. Anything that dirties a
  cart's tracked data — damage, a cargo change — can trigger it. Worth knowing before chasing a
  stutter that only appears when something else happens to the cart.

**Done when** is met: birch and spruce carts read correctly and roll a curve with the chest sitting
still in them, the lid follows the menu, and a linked pair draws chain and cargo together.

**Correction, from a bug found after S4.** The second round's third bullet blames the vanilla chest
on "a container cart draws its own block whenever the cargo slot is not overridden — blanking the
slot to air is not the same as the client having nothing to draw". The fix was right; the reason is
wrong, and the wrong reason cost a round trip later because it made presenting as `chest_minecart`
look re-testable.

What was actually established, by putting the substitution back and looking:

- **Blanking to air does not suppress a container cart's cargo, and this is vanilla's doing.** A
  plain `minecraft:chest_minecart` summoned with `{DisplayState:{Name:"minecraft:air"}}` — no
  Polymer, no stand-in, nothing of this mod's — still draws its chest. The NBT does apply; the
  server reads the slot back as air.
- **It is not explained by the code.** `getDisplayBlockState` returns the custom state unfiltered
  (`AbstractMinecart.java:579-585`), `BuiltInBlockModels.createAir` maps air to an
  `EmptyBlockModel`, and `AbstractMinecartRenderer#submit` skips the cargo when the model is empty.
  Whatever bridges that gap was not found, and two attempts to reason it out from the sources were
  both wrong. Recorded as an observation, not a theory.
- **The override does reach the client.** A cart carrying `minecraft:furnace` — which needs no
  stand-in, so the real state goes into the slot — draws a furnace. So the substitution forwards
  tracked data fine, and only the air case behaves oddly.
- **Nothing is given up by saying `minecart`.** Every cart type shares one renderer
  (`EntityRenderers.java:93` against `:164`), one body mesh (`LayerDefinitions.java:190`, one
  variable for all of them) and one hardcoded texture. A chest minecart has no model of its own;
  the chest is entirely the cargo block. The two types differ in exactly one method,
  `getDefaultDisplayBlockState`.

The cost of the substitution is interaction, not looks, and that is now paid explicitly — see
`CargoMinecartChest#interact`, added after a report that sneak-clicking one of these carts with an
iron chain both linked it *and* opened the chest where a vanilla chest cart only links. A client
runs the vanilla `interact` of the type it *believes* it holds to decide whether the click did
anything, and on a pass it tries the other hand and sends a second interaction
(`Minecraft.java:1698`, `MultiPlayerGameMode#interact`). `Minecart#interact` passes whenever the
player sneaks **or** the cart has passengers — and a cart with a stand-in always has passengers — so
every click on one of these arrives twice, and the second arrives with an empty off hand that
`minecart-tweaks` rightly declines to treat as a link. Answering only the main hand cancels the
duplicate exactly. `minecart-tweaks` was never at fault: its `UseEntityCallback` gates on
`instanceof AbstractMinecart` and cannot tell our cart from a vanilla one.

**The lid also gained a sound, for the same reason S7 exists.** A vanilla chest minecart is silent —
nothing in `MinecartChest`, `AbstractMinecartContainer` or `ContainerEntity` plays anything, because
the chest it draws never opens. This one's does, which made the silence conspicuous. It hangs off
the same `openCount` transitions as the lid, so the two cannot disagree, and only 0↔1 makes a sound:
a second player opening the same cart is silent, as a placed chest is. The event comes from the
cargo block's own `ChestBlock.getOpenChestSound()` / `getCloseChestSound()` rather than a hardcoded
`SoundEvents.CHEST_OPEN`, which is what `ChestBlockEntity.java:41,48` does and what makes a copper
or trapped chest cargo correct for free in S8; volume and pitch jitter are taken from
`ChestBlockEntity.java:121`. All three confirmed in world along with the interaction fix.

## S4 — Hoist the generic cargo cart into `rkcore` — **done**

**Build.** Everything in `cart/` that is not about wood moves into
`club.rainbowkitty.rkcore.common.vehicle`, leaving this mod with the chest-and-wood half. What makes
that possible is changing the cargo's type: it stops being a `WoodType` and becomes a
`Holder<Block>`. `rkcore` has no way to know what a wood is, but it can carry any block and read a
default state, a name and a registry path off one — which is the whole of what the generic half
needs.

`rkcore` gains, alongside the five S1 classes:

- A cargo component factory on `CargoMinecarts` — a `DataComponentType<Holder<Block>>` over
  `BuiltInRegistries.BLOCK.holderByNameCodec()`, registered at `<namespace>:cart_cargo` and passed
  to `PolymerComponent.registerDataComponent`. Persistent codec only, no stream codec, exactly as
  `CartComponents.WOOD` is today: it never reaches a client.
- `CargoMinecartChest extends MinecartChest implements CartCargo` — the generic container cart,
  holding the cargo in a plain field beside its `CartCargoSupport`. It takes every override
  `PlankedChestMinecart` has today that does not mention wood: `tick`, `remove`,
  `addAdditionalSaveData` / `readAdditionalSaveData`, `applyImplicitComponents`, `getPickResult`,
  `destroy(ServerLevel, Item)` and `getTypeName`. `cargoState()` defaults to the cargo block's
  default state and `createCargoDisplay()` to null, so a cart carrying a *vanilla* block needs no
  subclass at all — that is the case that proves the mechanism is general.
- `CargoCartItem extends PolymerMinecartItem` — reads the same component off the stack, names it
  through the same helper, and exposes a `cargoModel(Holder<Block>)` hook returning null, which is
  where S5's per-wood icons attach.
- Name composition, as two statics: "Minecart with %s" from a consumer-supplied pattern key, and a
  cargo name of `translatableWithFallback(block.getDescriptionId(), <title-cased registry path>)`.
  A block named `plankedchests:birch_chest` therefore falls back to "Birch Chest" with no per-wood
  table anywhere, and the composed string is byte-for-byte what S2 already produces.

This mod keeps `ChestCarts` (registration, and the wood ↔ chest-block bridge in both directions),
a `PlankedChestMinecart` cut down to its constructor plus S3's `createCargoDisplay`, and
`ChestMinecartItem` only if S5 wants somewhere to put the model override. `CartComponents` goes;
`ChestCarts` registers the component through `rkcore` instead.

Constructor ordering is the one trap. The entity factory is handed to `CargoMinecarts.register`
before the item and the component exist, so the base class cannot take them as plain values — but
nothing constructs an entity until long after `init()` returns, so a `Supplier` for each works, as
does reading them off the consumer's own class the way `PlankedChestMinecart` reads `ChestCarts`
today.

Two formats change on disk: the entity's saved cargo becomes a block id
(`plankedchests:birch_chest`) rather than `"birch"`, and the item component becomes
`plankedchests:cart_cargo` rather than `plankedchests:wood`. Both exist only in the dev world, so no
migration is written — but say so out loud before the first restart after this stage, because every
cart already placed there reverts to the default cargo.

**Done when.** `cd rkc/mods/rkcore && ./gradlew build publishToMavenLocal` is green, then this mod's
`./gradlew build`. Then, from the `rkcore` checkout:

```
grep -rniE 'plankedchests|woodtype' src/main/java/club/rainbowkitty/rkcore/common/vehicle/
```

returns nothing, which is the actual test of whether the seam landed in the right place — a shared
package that names its first consumer has not been hoisted, only moved. In world, every S2 and S3
criterion again and unchanged from the outside: a `/give`n birch cart places, rolls, opens a menu
titled "Minecart with Birch Chest", shows a birch chest, and drops itself still birch — with
`/data get entity` now reporting the cargo as `plankedchests:birch_chest`.

### As built

Built as described. The exit grep is the headline: `grep -rniE 'plankedchests|woodtype'` over
`common/vehicle/` returns nothing, so the shared package names no consumer. Both builds green, lint
clean, `rkcore-0.2.0.jar` carries the two new classes, and the dev server boots to `Done (0.299s)!`
with no recipe load errors.

`rkcore` gained `CargoMinecartChest` and `CargoCartItem` beside the S1 five, and `CargoMinecarts`
gained `registerCargoComponent`, `cargoName` and `cartName`. This mod kept `ChestCarts` — now also
the wood ↔ chest-block bridge in both directions — a `PlankedChestMinecart` of about fifty lines,
and `ChestMinecartItem`; `CartComponents` is gone.

Verified over rcon rather than by reading the code back:

- `summon` with `{Cargo:"plankedchests:birch_chest"}` answers **"Summoned new Minecart with Birch
  Chest"** and reads the id back. So the composition really is byte-for-byte what S2 produced — the
  Build text's claim, and the one most likely to have been wrong, since it now runs through a lang
  key that does not exist yet and lands on the English fallback.
- A stack carrying `plankedchests:cart_cargo` survives a container round trip with the component
  intact and names itself "Minecart with Spruce Chest".
- Damaging an acacia cart to death drops
  `{components: {"plankedchests:cart_cargo": "plankedchests:acacia_chest"}, id:
  "plankedchests:chest_minecart"}`, so the `destroy` override still rebuilds the stack from the
  cargo now that it lives in `rkcore` and no longer knows what a wood is.
- 27 recipes regenerate with the cargo in the result and their mod-loaded conditions unchanged.

Four departures from the Build text.

- **The entity reads its component, default cargo and name pattern off its *item*, rather than
  taking each as a `Supplier`.** The constructor-ordering trap is real and the Build text's fix
  works, but three suppliers is three chances for the entity and the item to disagree about things
  they must agree on. `CargoMinecartChest` takes one `Supplier<CargoCartItem>` and asks it, so there
  is no second copy to drift. `CargoCartItem` becomes the definition of a cargo cart and the entity
  its behaviour, which also gives S5's `cargoModel` hook an obvious home.
- **The cargo's saved form is a raw block id, with the `Holder<Block>` derived from it.** Not in the
  plan, and it matters. `holderByNameCodec()` fails to decode an id nothing is registered under, so
  a plain `Holder` field would drop the cargo of any cart whose wood comes from a mod the server is
  currently without — and then write the loss to disk on the next save. S2 chose explicitly to keep
  the wood in that case ("losing it would be worse than showing the wrong chest"), and storing the
  id keeps that property: such a cart shows the default cargo until the mod returns. Confirmed by
  summoning one with `{Cargo:"somemod:ghost_chest"}` — it reads as "Minecart with Chest" and still
  reports `somemod:ghost_chest` when asked.
- **`PlankedChestMinecart` keeps `getTypeName`, and the open count with it**, rather than being cut
  to a constructor and `createCargoDisplay`. The open count is the lid's and was never generic. The
  name is a deliberate exception: composition would give the wood-less variant "Minecart with Chest"
  assembled from a pattern key this mod only ships in English, where vanilla's own
  `item.minecraft.chest_minecart` is translated on every client. S7 exists to make that variant
  indistinguishable from vanilla, so it keeps vanilla's key and only the 27 woods compose. The 27
  were already English-only under S2, so nothing regressed.
- **`cargoState()` needed no override here at all**, which was not obvious in advance. The generic
  default — the cargo block's default state, falling back to the item's default cargo — covers both
  of this mod's awkward cases, because an unresolvable cargo *is* the null case. S7's as-built
  predicted this.

**The format change bit, and the Build text's warning was not quite wide enough.** It says to say so
out loud because every cart already placed reverts to the default cargo. Sweeping the world for
*entities* before restarting found two carts, both wood-less, so nothing to lose — and the restart
then logged a `BlockEntity` serialization error for a cart **item** sitting in a hopper: `Failed to
decode … {"plankedchests:wood":"acacia"} … No component with type 'plankedchests:wood'`, and the
stack was dropped. Stored stacks carry the component too, so a sweep has to look in containers as
well as on rails. Dev world only, as predicted, and writing no migration is still right — but the
next stage that retires a component should check both.

## S5 — Item icons — **done**

**Build.** Blocked on one piece of art: a 16×16 `chest_minecart_overlay.png` in the style of the
existing `plankedchests_overlay/` files — vanilla's `textures/item/chest_minecart.png` traced, with
the chest body knocked out to transparency so a wood can show through. Given that, a
`ChestMinecartTextureProvider` mirrors `ChestTextureProvider`: composite the wood's 16×16 plank PNG
(already vendored in `src/datagen/resources/plankedchests_planks/`) under the overlay and write
`textures/item/<wood>_chest_minecart.png`, plus a `minecraft:generated` item definition per wood.
The 27 definitions are not the models of 27 registered items — nothing points at them but the model
S4's `cargoModel` hook returns per stack, which is why they can exist without the items existing.

Flat sprites rather than the 3D models the chest items use: vanilla is itself inconsistent this way
(`chest` is a special model, `chest_minecart` a sprite), and matching vanilla's cart icon is the
cheaper and more legible of the two.

**Done when.** `./gradlew generateAssets` writes 27 PNGs and 27 item definitions; a birch chest
minecart on the hotbar — crafted or `/give`n, since there is nowhere to browse for one — shows a
birch-toned chest in a cart, distinct from a vanilla one held beside it.

### As built

Met, and the blocker never materialised. All 12 vanilla-wood carts were read side by side in world
and each is distinctly its own wood; the plain variant and a true vanilla cart were indistinguishable
beside them. `generateAssets` writes 82 files — 27 sprites, 27 models, 27 item definitions and one
more (below) — and a second run writes nothing, so the output is stable. Build green, lint clean
after two violations.

**The one external dependency in this plan was not one.** The Build text called for a hand-authored
`chest_minecart_overlay.png` tracing vanilla's icon with the chest knocked out, and the preamble
named it the only thing here that could not be produced from the repository. It can: vanilla ships
the same cart twice, and `item/minecart.png` is `item/chest_minecart.png` without the chest, so the
chest's pixels are exactly the ones the two differ in. Both are vendored into
`plankedchests_vanilla/` beside S7's `chest.png`, for the reason that directory already exists —
they are client assets and not on the mod classpath — and `ChestMinecartTextureProvider` derives the
overlay at datagen time rather than reading a vendored one.

Deriving beat tracing on more than effort. The region is exact rather than eyeballed; a tone inside
it that the palette does not recognise throws, so a future retexture fails the build instead of
silently shipping a chest with a hole in it; and the derivation is readable as the reason, where a
vendored PNG would have been a pixel artefact with the reasoning lost.

Three choices inside it that the Build text did not anticipate:

- **The wash is two-directional, not a knockout.** A transparent hole would have dropped vanilla's
  shading and left a flat plank patch. Instead each of the five wood tones becomes a black or white
  wash at the alpha reproducing its ratio to the chest's top face, so vanilla's own shading survives
  and only the colour under it changes — the same trade the placed chests' overlays make.
- **Anchored on the top face rather than the brightest tone.** The top face is by far the largest
  area, so anchoring there is what keeps a pale wood pale and a dark one dark; anchoring on the
  brightest would have darkened every wood by its distance from a six-pixel highlight.
- **The trim and the latch stay vanilla's colour.** An outline that took the wood's colour stops
  reading as an outline. This is what the existing `chest_overlay.png` already does with its
  `(55,49,39)` trim, so it is consistent rather than a new decision.

**A bug outside this stage's text, found by building it.** The cart item had no item definition at
`items/chest_minecart.json`. That is invisible to a vanilla client, which is sent
`Items.CHEST_MINECART` instead — but a client running this jar is sent the *real* item id, and an
item with no definition draws as a missing model. Every cart has looked broken to such a client
since S2. The provider now writes that file too, pointing at `minecraft:item/chest_minecart`;
vanilla's own definition was read out of the jar and is the same shape, so a mod client sees exactly
vanilla's icon for any cargo without one of its own.

Two things verified rather than assumed, both load-bearing and both cheap to get wrong: `atlases/
items.json` stitches `textures/item/` through a `DirectoryLister`, whose `listMatchingResources`
scans every namespace, so `plankedchests:item/*` needs no atlas file of its own; and
`cargoModel` returning null for a non-wood cargo is what keeps S7's variant drawing as the vanilla
cart it presents as, rather than needing a case of its own.

## S6 — Lang, tags, lint — **done**

**Build.** `ChestLanguageProvider` gains three keys: the composition key S4 introduced,
`item.plankedchests.minecart_with` = "Minecart with %s"; the cart entity type's own description id;
and `item.plankedchests.chest_minecart` = "Minecart with Chest" for the plain variant, which used to
borrow vanilla's key and cannot any more (below). It gains no per-wood cart keys: the cargo's
`block.plankedchests.<wood>_chest` keys already exist and the name is composed from them, so 27 keys
became one.

**The carts go in the tab, and vanilla's is renamed out of the way.** This reverses an earlier
decision — the text here used to say nothing was added to a creative tab and that this was a
requirement rather than an omission. It was retracted after S5, once every wood had a distinct icon
and 28 entries were legible rather than 28 identical ones. `ChestBlocks.registerCreativeTab` now
emits the chests, the trapped chests, and then `ChestCarts.carts()`: the plain-chest variant first,
then one per wood this build registered a chest for. The tab grows from 54 entries to 82.

Three things follow, and the first is the reason the other two matter less than they look.

- **A vanilla client cannot see this tab at all, and never could.** The vanilla creative screen is
  built client-side from the client's own registry, and `PolymerServerProtocol
  .sendCreativeSyncPackets` sends nothing to a connection that did not negotiate the creative-tab
  protocol. So the tab reaches Polymer clients natively and everyone else only through
  `/polymer creative`. That was already true of the 54 chests; it is not a new limitation, but it
  was not written down, and it is what stops "browsable" from meaning what it sounds like. Crafting
  and pick-block remain the routes that work for the whole live audience.
- **The creative round trip S2 worried about is now reachable**, where S2's as-built said it could
  not be. A stack only comes back out of the creative panel if something put it there, and now
  something does. The mechanism is the one S3 already proved for a slot-move — Polymer's stashed
  `$polymer:stack` carries the `cart_cargo` component back — but taking a cart *out of the panel* is
  a path that has never been exercised, and it is the one thing in this stage that could fail
  quietly, by handing back a cart that has lost its wood.
- The recipe book still dispenses ingredients rather than results, so nothing round-trips there.

**Vanilla's `chest + minecart` now crafts this mod's cart.** A hand-written
`src/main/resources/data/minecraft/recipe/chest_minecart.json` replaces vanilla's recipe at its own
id, rather than adding a 28th recipe over the same two ingredients — two shapeless recipes matching
identically would both apply and which won would be an ordering accident. It has to be hand-written:
`FabricRecipeProvider.getRecipeIdentifier` rebuilds every id it is given as `<modId>:<path>`, so
datagen cannot write into the `minecraft` namespace, and overriding that method would also move the
generated advancement onto vanilla's `minecraft:recipes/transportation/chest_minecart`. Nothing
needs to replace that advancement: it grants recipe id `minecraft:chest_minecart`, which now
resolves to this one. The `mineable/axe` tag is hand-written in the same namespace for the same
reason.

This also settles the open decision about whether vanilla's chest minecart should stay craftable:
it is not craftable, because that recipe now yields ours. Not a stub — a redirect, which is why it
lives in `data/minecraft/` unconditionally rather than in the `disable_vanilla_recipes` pack beside
the chest stubs. An admin turning that pack off wants vanilla chests back, not a static lid.

**And vanilla's cart is renamed "Minecart with Chest (Legacy)"**, in a hand-written
`src/main/resources/assets/minecraft/lang/en_us.json` overriding both `item.minecraft.chest_minecart`
and `entity.minecraft.chest_minecart`. Verified rather than assumed: `ClientLanguage.loadFrom`
(`:32-48`) walks every namespace's `lang/<code>.json` through `getResourceStack` and applies each
with `translations::put`, so one key is overridden and the rest of vanilla's file is untouched.

That rename forced a key split, which unwinds a piece of S4's reasoning. The plain variant borrowed
`item.minecraft.chest_minecart` so it would stay translated for a client that declined the resource
pack; a shared key would now rename both halves of the pair. It gets `item.plankedchests
.chest_minecart` instead, with `translatableWithFallback` keeping the English. A client that
declines the pack sees neither the rename nor our key, so for it the pair reads exactly as it did
before; what is genuinely lost is a non-English decliner, which now reads one of the two in English
— the same deal the 27 woods have taken since S2.

Any vanilla entity type tag the cart should join would go in a merging
`data/minecraft/tags/entity_type/` file under the `minecraft` namespace — the same rule the
`mineable/axe` tag had to learn in `planked-chests.md`'s own S6. **There are none**: no vanilla
entity-type tag covers minecarts and `minecart-tweaks` defines none either, so this item is answered
rather than built. See the reference section. `checkstyleTarget` on both `rkcore` and this mod.

**Naming is a component in 26.2, and this mod's cart had been throwing it away.** Found while
building the tab, because it is what made every entry there read "Minecart with Chest". `Item#getName`
*is* the `ITEM_NAME` lookup rather than something consulted after it (reference section), so
`CargoCartItem`'s override made the component dead on this item. That broke three things at once,
and only the third was visible before the tab existed:

- An operator's `/give …[item_name=…]` did nothing.
- **A client running this mod showed every cart as "Minecart with Chest", in every slot, since S2.**
  Polymer was already sending the right name — `createItemStack` writes it as exactly this
  component, from `itemStack.getItemName()` — but such a client is sent the *real* item, runs our
  override, and discards it. A vanilla client was unaffected the whole time because its item is
  `Items.CHEST_MINECART`, whose default `getName` reads the component.
- A stack in a Polymer creative tab had no way to be named at all, since that path sends stacks raw.

`CargoCartItem.getName` now answers an explicit `ITEM_NAME` before composing, reading the patch and
not the resolved component. `ChestMinecartItem` overrides `cartName(Holder)` instead of `getName`,
so the base class's handling cannot be bypassed the same way again, and the wood-less naming rule
lives only there — `ChestCarts.displayName` forwards to it. Both halves of that mattered: keeping
the rule in `displayName` *as well* made the two call each other without end, which crashed every
client until it was found.

**A Polymer creative tab sends its stacks with no conversion**, which is why they carry baked
presentation and no other cart stack does. `PolymerCreativeTabContentAddS2CPayload.of` takes the
`isPolymerCreativeModeTab` branch and writes the server stacks through vanilla's
`ItemStack.OPTIONAL_LIST_STREAM_CODEC`, which strips `cart_cargo` on the way out. So
`ChestCarts.carts()` sets `ITEM_NAME` and `ITEM_MODEL` itself. Baking rather than syncing the
component per connection — Polymer does offer that, through `PolymerComponent.canSync` — because a
tab's contents are cached once for every player (`CONTENT_CACHE`, keyed by tab id and op status),
so there is no connection to key on when they are built.

**`/polymer creative` is a tab *list*, not a search.** The bare command opens `CreativeTabListUi`, a
grid of group icons; its search button opens an unfiltered paginated dump of every group's contents
with vanilla groups first and Polymer-registered ones last. A mod whose items sit in vanilla tabs
therefore appears on the early pages and this one does not appear for many. `/polymer creative
plankedchests:chests` goes straight to the tab, and is the form worth telling anyone to use.

**Done when.** `cd scripts/lint && ./gradlew checkstyleTarget` is clean for both checkouts and
`./gradlew build` is green for both. In world:
`/give @s plankedchests:chest_minecart[plankedchests:cart_cargo="plankedchests:dark_oak_chest"]`
yields an item reading "Minecart with Dark Oak Chest"; the same string appears on a client that
*declines* the server resource pack, which is what proves both fallbacks are wired.

Then the tab and the recipe. `/polymer creative` shows the `plankedchests:chests` tab holding the
chests, the trapped chests and then 13 carts on this dev server (the plain one plus the 12 vanilla
woods — the other 15 have no chest block here), each drawing its own S5 icon. Crafting
`minecraft:chest + minecraft:minecart` yields **"Minecart with Chest"**, not the Legacy one, and
`/give @s minecraft:chest_minecart` yields **"Minecart with Chest (Legacy)"** — the two are the
whole point of the rename and have to be read side by side.

And the round trip that S2 could not reach: take a wood cart *out of* the `/polymer creative` tab in
creative mode, put it in the hotbar, and place it. It must still be that wood. This is the one step
here that can fail silently, so it is not optional.

### As built

Met. Both builds green and `checkstyleTarget` clean for both checkouts; in world the names read per
wood on a vanilla client and on one running this mod, the tab holds the chests, the trapped chests
and the 13 carts this dev server can offer, `chest + minecart` crafts ours, `/give
minecraft:chest_minecart` reads "Minecart with Chest (Legacy)", and a wood cart taken out of the tab
and placed is still that wood — the round trip S2's as-built said no later stage would inherit.

The retraction that started it, the tab, the recipe redirect and the rename are all described in the
Build text above, which was written as the work was done rather than before it. What follows is only
what the Build text could not have said.

**The stage found a bug older than itself, and the tab is what made it visible.** Every cart read
"Minecart with Chest" on a client running this mod — in the hotbar, not only in the tab. It had done
since S2, and no one had noticed because the live audience is vanilla clients, which were never
affected: their item is `Items.CHEST_MINECART`, whose default `getName` reads `ITEM_NAME`, so
Polymer's composed name landed correctly. A client sent the *real* item ran this mod's `getName`
override instead, which ignored the component Polymer had just written the name into, and threw it
away. The mechanics are in the reference section; the shape of the mistake is worth keeping
separately, because it is not specific to naming: **overriding a vanilla method that exists to read
a component silently disables that component**, and the failure only shows on the clients that get
the real item — a minority this mod has no easy way to test against.

**Three wrong turns before that, all mine, and all from asserting a mechanism instead of reading
it.** They are recorded because each cost a round trip with the user at the keyboard.

- Claimed vanilla clients browse the tab through `/polymer creative`. They can, but the bare command
  opens a tab *list*, and its search button is an unfiltered paginated dump with Polymer-registered
  groups last — so the honest answer to "can a vanilla client browse these" is "only if told the
  tab's id". The chests have been in this position since they shipped.
- Assumed the tab sync applies Polymer's item conversion. It does not, which sent the fix to baking
  `ITEM_NAME` onto the tab stacks — correct, but for a reason that turned out not to be the reason
  anything was broken. The baking is still needed and still there.
- Moved the wood-less naming rule onto `ChestMinecartItem.cartName` while leaving a copy in
  `ChestCarts.displayName`, so the two called each other without end. Every client crashed. The
  whole point of that edit was to stop two paths from drifting, and it shipped with two paths.
  A `/summon` over rcon exercises the same method and would have caught it in seconds; it now runs
  before anything is handed over.

**Entity type tags turned out to be an answer, not a file.** No vanilla `entity_type` tag covers
minecarts — all 48 were searched, and `EntityTypeTags` has no cart or vehicle constant — and
`minecart-tweaks` defines none. Nothing to join.

**One thing deliberately not changed.** `PlankedChestMinecart.getTypeName` now returns exactly what
the inherited `CargoMinecartChest.getTypeName` would, since both reach `ChestMinecartItem.cartName`.
It is redundant rather than wrong, and removing an override while fixing a crash it did not cause is
how the crash happened in the first place. Worth deleting the next time this file is opened for
another reason.

## S7 — A vanilla-chest cargo — **done**

**Build.** S3's lid animation is a straight improvement on vanilla, whose cargo renderer draws a
chest minecart's chest permanently shut. Without something here that is a trap: every chest this mod
makes is built from plank texture plus overlay, oak included, so choosing a cart with a working lid
means giving up vanilla's own chest art entirely — there is no variant that looks like the chest
everyone knows.

So the cart gains one more cargo that is not a wood. **A cart stack with no wood component is the
vanilla chest**, rather than oak as it was: vanilla's name (`item.minecraft.chest_minecart`, which
every client already has and so needs no fallback), vanilla's icon by way of the fallback item, and
vanilla's chest art — differing from `minecraft:chest_minecart` only in that its lid opens. Oak
stops being the default and becomes an ordinary wood like the other 26, which it should have been.

`WoodType` is left alone; the wood field and component are simply nullable. The alternatives were a
28th enum entry, which would register a `plankedchests:vanilla_chest` block nobody wants, and a
sentinel, which is a null in a hat. Nullable also lines up exactly with S4, where the cargo becomes
a `Holder<Block>` and this variant is just `minecraft:chest`.

The art is vanilla's `assets/minecraft/textures/entity/chest/normal.png`, vendored into
`src/datagen/resources/plankedchests_vanilla/` for the reason the plank textures already are — it is
a client asset, not on the mod classpath — and copied through by `ChestTextureProvider` rather than
composited, since it is already a 64×64 sheet in the same layout. `ChestElementModelProvider` gains
`addCargoVariant`, a SINGLE-only sibling of `addVariant`: this chest is never placed, so it is never
half of a double chest, and it has no item to want a whole-chest icon.

Deliberately not built: a vanilla-looking chest *block*. That is a much larger change — block, item,
recipe, tab entry — and it collides with `disable_vanilla_recipes`, which stubs `minecraft:chest`.

**Done when.** In world, three carts side by side: a vanilla `minecraft:chest_minecart`, a
`/give`n `plankedchests:chest_minecart` with no component, and an oak one. The middle is
indistinguishable from the first until it is opened, and then its lid rises; the oak one is visibly
oak-planked beside both. All three read "Minecart with Chest", "Minecart with Chest" and "Minecart
with Oak Chest". `./gradlew generateAssets` writes `textures/block/chest/vanilla.png` plus exactly
two item definitions for it and no whole-chest model.

### As built

Built as described, and ridden along with S3 — it needed only S3's display element, which is why it
was built out of numeric order.

Observed in world across S3's test rounds rather than as a separate sitting: a vanilla
`minecraft:chest_minecart` was parked beside ours from the first round, and the component-less
variant read as vanilla beside it, differing only when opened. The wood variants are visibly distinct
from both. The three names were not separately read off side by side; the vanilla one takes
`item.minecraft.chest_minecart` through the `wood == null` branch of `ChestCarts.displayName`, which
is the same key vanilla uses, so there is nothing in it that could differ.

The datagen half was checked directly. `generateAssets` writes exactly
`items/vanilla_chest_base.json`, `items/vanilla_chest_lid.json` and the two block models behind
them — against birch's seven item definitions, which add the left/right halves and the
`_whole` icon — and no whole-chest model, as `addCargoVariant` intends.

One correction to the text above: **"copied through" is pixel-for-pixel, not byte-for-byte.**
`ChestTextureProvider` decodes and re-encodes through `PngAssets` like every other texture, so the
written `vanilla.png` is a different file from the vendored `chest.png` on disk while decoding to
identical 64×64 RGBA. That matters only if someone ever tries to assert the copy with a checksum.

Two things this stage quietly settled that S4 inherits. Making the wood nullable rather than adding a
28th `WoodType` was the right call for the reason given, but the payoff is bigger than stated: every
call site that had to stop defaulting to oak — `ChestMinecartItem.woodOf`, `ChestCarts.stack`,
`displayName`, `PlankedChestMinecart.cargoState` and `createCargoDisplay` — is exactly the set of
places S4 has to generalise to a `Holder<Block>`, and they now all handle "cargo that is not one of
this mod's woods" instead of having to grow that case later. And `cargoState` falling back to
`getDefaultDisplayBlockState()` for the null wood means the vanilla variant needs no special case in
`CartCargoSupport` at all.

## S8 — Guard the cargo, and open it up to barrels, shulker boxes and copper chests — **done**

Raised after S4, from watching `/summon plankedchests:chest_minecart {Cargo:"minecraft:furnace"}`
work: it produced a cart that looks like a furnace cart and holds 27 slots, which is nonsense.
`CargoMinecartChest` will carry any block it is handed, and that generality is right for `rkcore` —
what is missing is the consumer saying which blocks it means.

**Build.** Two halves, and the first is the one that matters.

- **Refuse a cargo this cart has no business carrying.** A `CargoMinecartChest` is a container cart
  with a chest's inventory, so its cargo should be a chest-like block: chest, trapped chest, the
  eight copper chests, barrel, the seventeen shulker boxes, and this mod's own per-wood chests.
  Anything else falls back to the default cargo rather than being drawn. A block tag is the obvious
  shape for the list, since it is then a datapack's to extend and `planked-chests` does not have to
  know about a future consumer's blocks either. The seam belongs on `CargoCartItem` or beside it,
  as a predicate the consumer supplies once — `rkcore` cannot know which blocks are chest-like any
  more than it knows
  what a wood is, and hardcoding a list there would be the same mistake S4 was built to avoid. Guard
  on the way in (`setCargo`, `setCargoId`, `applyImplicitComponents`) rather than at the draw, so
  saved data and the item component never hold a cargo the cart would not honour.
- **Barrel, shulker box and copper chest carts.** Once the guard exists these are recipes and
  nothing else: each is a `Holder<Block>` like any other, and **vanilla already renders all of them
  in a cart**, so none needs a stand-in, a model or a texture. Copper chests in particular are
  already in the hardcoded table — `BuiltInBlockModels.java:89-95` walks
  `WeatheringCopper.WeatherState` and registers a `ChestSpecialRenderer.COPPER` model for both
  `Blocks.COPPER_CHEST.weathering().pick(state)` and `.waxed().pick(state)`.

  Counts, so the recipe loop is written against the right thing: copper chest is a
  `WeatheringCopperCollection<Block>`, which is two `ByState` maps (weathering and waxed) over four
  weather states — **eight blocks**, enumerable with `asList()` / `forEach` / `zipUnwaxedWaxed`
  rather than by naming ids. Shulker boxes are the plain `Blocks.SHULKER_BOX` plus
  `Blocks.DYED_SHULKER_BOX`, a `ColorCollection<Block>` of sixteen. Barrel is one block.

Open questions, none of them blocking:

- **Does a copper chest cart weather?** A placed one oxidises over time and can be waxed or scraped.
  A cart's cargo is a block id in a component and will not change on its own, so a cart would be
  frozen at whatever state it was crafted from. Freezing is defensible — the cart is a vehicle, not
  a placed block — but it should be a decision rather than an accident, and if it stays frozen then
  waxing and scraping a *cart* should probably do nothing rather than half-work.
- **Does a shulker box cart keep the box's contents?** It should not: the cart is the container, and
  a shulker box carrying items into a cart's own 27 slots is two inventories in one entity.
- **Whether all sixteen dyed boxes get recipes**, or whether dye is applied to the cart.
- **Naming.** "Minecart with Barrel" and "Minecart with Oxidized Copper Chest" compose for free from
  S4's pattern, since both blocks already carry their own names. "Minecart with Shulker Box" is the
  one that reads oddly next to vanilla's hopper and chest cart vocabulary.

Wanted but not urgent, and deliberately after S5 and S6: nothing in the shipped feature is wrong
without it, and the guard is easier to write once S5 has settled how a cargo maps to an icon.

**Done when.** `/summon plankedchests:chest_minecart {Cargo:"minecraft:furnace"}` produces a cart
drawing no cargo and reading "Minecart with Chest". The same with `minecraft:barrel`,
`minecraft:shulker_box` and `minecraft:weathered_copper_chest` each produces a cart drawing that
block, named after it, opening a 27-slot inventory, and dropping itself with its cargo intact; each
is craftable as `<block> + <minecart>`, and `generateAssets` writes one recipe per cargo — eight for
copper chest, seventeen for shulker boxes, one for barrel.

### As built

Every clause of "Done when" verified over rcon against the dev server, including the counts: 53 cart
recipes, of which 27 are the woods' and 26 are new (1 barrel, 8 copper chest, 17 shulker box). The
furnace cart comes out drawing `DisplayState: minecraft:air` and named "Minecart with Chest"; the
other three each draw their own block, name themselves after it, take an item in `container.26` and
refuse `container.27`, and drop the cart with its `cart_cargo` intact.

**The guard is one predicate read from two places, deliberately.** `CargoCartItem#acceptsCargo`
defaults to accepting everything and `ChestMinecartItem` narrows it to a block tag; `cargoOf` filters
a stack's component through it and `CargoMinecartChest#apply` filters a cart's. Two call sites of one
method, rather than two copies of a rule — which is the S6 crash written down as a constraint rather
than as a regret.

**Where the Build text was not followed.** It asked for a refused cargo to be dropped from saved data
"so saved data and the item component never hold a cargo the cart would not honour". As built the id
is *kept* and only the resolved holder is dropped, which is what the surrounding code already does
for a block whose mod is absent. The two cases are indistinguishable from the cart's side — this
build will not draw that block today — and keeping the id makes neither destructive: a cart whose
cargo an operator's `replace: true` datapack drops out of the tag reverts to a plain chest rather
than being silently rewritten to one, and comes back if the tag does. A cart's *item* has no such
second chance and needs none, since a stack is only ever rewritten by the player holding it.

**A regression S6 introduced, found here.** Our cart item had no dispense behaviour — vanilla
registers one per cart item by name (`DispenseItemBehavior:399-404`) — so a dispenser loaded with
one dropped it on the floor instead of placing it on the rail. Harmless while the item was only
reachable by `/give`, and a live breakage from the moment S6 redirected `chest + minecart` to it:
any existing contraption dispensing a freshly crafted chest minecart stopped working, and nothing in
S6's own checks would have shown it. Fixed with `CargoMinecarts#registerDispenseBehaviour`. The
general shape is worth more than the instance: **taking over a vanilla item's recipe inherits every
place that item is special-cased by identity, and vanilla special-cases carts by item in at least
this one table.**

**The open questions, answered.**

- *Does a copper chest cart weather?* No, and no code says so. A cargo is a block id in a component
  and nothing ticks it, so a cart is frozen at whatever it was crafted from. Waxing and scraping a
  cart do nothing for the same reason. Defensible on its own terms — the cart is a vehicle, not a
  placed block — and the alternative is a weathering rule that would have to be invented rather than
  inherited.
- *Does a shulker box cart keep the box's contents?* Not as a box, but the items are not destroyed
  either, and the plan's own answer would have destroyed them. A broken shulker box carries its
  contents on the item (`BlockLootSubProvider#createShulkerBoxDrop` copies `DataComponents.CONTAINER`)
  and a shapeless recipe builds its result from the recipe alone, so `filled box + minecart` would
  have voided everything inside with no warning. The seventeen box recipes are `crafting_transmute`
  instead, which keeps the input's components — vanilla reaches for exactly this for exactly this
  reason, since every shulker box dye recipe is one — and `CargoMinecartChest#applyImplicitComponents`
  empties the arriving container into the cart's own 27 slots. That is also vanilla's rule rather
  than an invention: a block item carrying a `container` fills the block entity it places. Net
  effect: the items travel into the cart, there is no inventory nested inside another, and nothing is
  lost. Verified end to end — a crafter turned a box holding 5 diamonds and 3 emeralds into a cart
  item carrying both, a dispenser placed it, and the cart came up with the diamonds in slot 0 and the
  emeralds in slot 26.

  That left the conversion one-way — break the cart and the items spill, as they do from any
  container cart — and the second half closed it: **a cart is broken like the block it is drawn as.**
  `CargoCartItem#cargoKeepsContents` is a third consumer hook beside `acceptsCargo` and `cargoModel`,
  false by default, and `ChestMinecartItem` answers true for `BlockTags.SHULKER_BOXES` alone. A
  shulker cart's contents ride the dropped item; a chest, barrel or copper chest cart spills exactly
  as before. The rule is inherited rather than invented — a mined shulker box keeps its contents and
  a mined chest does not — and it hands out no reach: a shulker cart already cost a shulker box,
  which was already a container you could carry full. Answering true for the chests instead would
  have made a cart of planks and iron do what vanilla gates behind a trip to the End, which is a
  change to what storage costs rather than to how a cart behaves.

  The mechanism is the same three steps whatever the answer — capture the inventory, clear it, write
  `CONTAINER` onto the dropped stack — wrapped in the hook, so widening it later is one line.
  Verified as a pair in world: a loaded shulker cart and a loaded oak cart broken side by side, the
  shulker one dropping a single cart item carrying both stacks and nothing loose, the oak one
  dropping five diamonds, three emeralds and a bare cart. The shulker item was then hoppered into a
  chest, dispensed back onto a rail, and came up loaded again.
- *Whether all sixteen dyed boxes get recipes.* Yes, all seventeen including the plain one. Dyeing a
  cart would mean a second way to reach the same stack and a second thing to name.
- *Naming.* Composed, as everything else is. "Minecart with Shulker Box" does read oddly next to
  vanilla's vocabulary, but naming it by hand means one more string that can drift from the block it
  describes, which is the trade S2 already made for 27 woods.

**Not in the tag, and each for its own reason** — recorded in `ChestCartCargoProvider`'s Javadoc
rather than here, since that is where the list is. The one worth repeating: *this mod's own trapped
chests* are excluded because nothing would draw them. They are Polymer blocks, so vanilla's cargo
slot renders nothing, and `PlankedChestMinecart#createCargoDisplay` maps only plain chests to a
stand-in. Whether they get carts at all is still open below, and admitting an invisible cargo is not
the way to open it. Vanilla's trapped chest has no such problem and is in.

**One thing the tag cost.** `TagsProvider` resolves every required tag reference against the tags the
provider itself defines, and there is no parent provider to hand it vanilla's, so
`#minecraft:copper_chests` and `#minecraft:shulker_boxes` had to go in as *optional* references or
datagen fails outright. Both are `BlockTags` constants, so the compiler has already checked they
exist and nothing is actually being waved through.

**Left alone on purpose:** `PlankedChestMinecart#getTypeName`, still redundant with the inherited
one. S6 flagged it for "the next time this file is opened for another reason"; this stage read that
file but had no reason to edit it, and deleting an override while changing how cargo is accepted is
the shape of edit that caused S6's crash.

## S9 — Shulker box carts draw their own box — **done**

S8 put the seventeen shulker boxes in on the strength of "vanilla already renders all of them in a
cart", which is true and insufficient: vanilla renders them *shut*. A box's openness lives on its
block entity, a cart has none, and `AbstractMinecartRenderer` resolves a block state with nowhere to
put one — so a shulker cart drawn vanilla's way is a sealed cube however many players are in it.

**Build.** The chest's bargain again, for the same reason: blank vanilla's cargo slot to air and
draw the box ourselves. A `ShulkerCartDisplay` beside `ChestCartDisplay`, two `ItemDisplayElement`s,
a `ShulkerModels` naming class, and a `ShulkerElementModelProvider` transcribing
`ShulkerModel.createShellMesh` — which is exactly two cubes, a 16×8×16 base at `texOffs(0,28)` and a
16×12×16 lid at `texOffs(0,0)`, overlapping through four pixels, which is why a closed box reads as
a full cube. The lid needs none of the chest's hinge arithmetic: a display entity rotates about its
model cube's centre, and a shulker lid turns about the box's vertical axis, for which x and z are
already centred at 8.

Vanilla's own lid motion, not an approximation of it: rise half a block and turn 270°
(`ShulkerBoxRenderer$ShulkerBoxModel#setupAnim`), ramped 0.1 a tick with no easing curve on top
(`ShulkerBoxBlockEntity#updateAnimation`) — unlike the chest, which eases.

The sheets are vanilla's, referenced rather than copied. They live on the `shulker_boxes` atlas,
which only the special renderers read, and an item model can only name a sprite on the block atlas —
so `ShulkerAtlasProvider` writes `assets/minecraft/atlases/blocks.json` to *add* the seventeen to
vanilla's own. That is a merge rather than an override (`SpriteSourceList.load` appends every pack's
sources), and vanilla already pulls entity textures onto that atlas the same way —
`entity/bell/bell_body` and `entity/enchantment/enchanting_table_book` are both `minecraft:single`
sources in its own `blocks.json`.

**Done when.** A cart carrying any of the seventeen draws that colour's box; the lid rises and turns
when a player opens it and returns when they close it; and the box sits level with a chest cart's
chest parked beside it rather than riding proud of it.

### As built

**The finding that cost the most, and the one worth keeping: a block model culls back faces and the
shulker renderer does not.** A faithful transcription of the two cubes therefore shows nothing of
the box's own interior, which for an open one is most of what there is to see. The first attempt
answered that by inventing geometry — a hollowed base, inner walls, bespoke UV constants — and it
never read right, because it was reproducing a guess at what no-cull draws rather than what no-cull
draws.

What does reproduce it exactly is a second copy of each cube with `from` and `to` swapped:
`ElementModels#invertedTwin`. Every wall gets an inward-facing surface coincident with its outward
one, so only ever one of the pair faces the camera and the two never fight over a pixel. The swap
works because of how the baker reads the file, not because of a quirk in the format —
`FaceBakery`'s vertex picker takes `from` for every `MIN_*` extent and `to` for every `MAX_*` with
no sorting, `CuboidModelElement.Deserializer.getPosition` validates only that each component falls
in −16..32, and `recalculateWinding` leaves the twin alone because each quad already agrees with its
own computed normal. Each face moves to its opposite key and takes one mirrored axis, since a quad
seen from behind is a quad mirrored: the four sides swap their v pair, up and down their u. All the
bespoke UV arithmetic went with it, and `ShulkerElementModelProvider#addGeometry` is now four lines.

**Disabling culling instead is not reachable from here**, and was checked rather than assumed before
the twin was built. The item render path is `Sheets.cutoutBlockItemSheet()` → `RenderTypes.itemCutout`
→ `RenderPipelines.ITEM_CUTOUT` ← `ITEM_SNIPPET`, and `RenderPipeline.Builder` defaults
`this.cull.orElse(true)`. There is no no-cull item pipeline and no JSON field that selects a
pipeline, so a server-side mod has nothing to reach for.

**A model tool came out of it**, in `exports/shulker/`: `export_model.py` rewrites the generated
models into a resource-pack layout Blockbench opens clean (texture bound to a real path,
`texture_size [64,64]` so it shows texels while the file keeps vanilla's 0–16), plus OBJ/MTL, and
`preview.py` renders them headless. Two traps recorded there: EEVEE segfaults under `blender -b` for
want of a GL context, so it renders through Cycles on CPU; and OBJ import converts Y-up to Z-up, so
the camera and the lid transform are derived from world bounds rather than from authored axes. The
preview deletes polygons facing away from its fixed orthographic camera, which makes it cull exactly
like the game — that is what turned it into a substitute for launching a client, and what made the
twin verifiable without one.

**Two pose numbers depart from vanilla's cargo transform, and both are recorded as derivations
rather than as dialled-in values.** The scale is 0.745 rather than 0.75: a chest model is 14 wide
inside its 16-wide block so vanilla's figure leaves it clear of the cart, where a shulker box fills
the block edge to edge and at 0.75 one side lands exactly on the cart's wall, leaving which of the
two draws to float rounding. And `CARGO_CENTRE_Y` is computed from `ChestCartDisplay`'s own scale
and model top rather than from vanilla's 0.75, because a box that fills its block rides two model
pixels proud of every chest cart beside it — deriving it means changing either scale keeps the two
tops level instead of quietly drifting apart.

## S10 — One rule for every cart icon — **done**

S5 built the per-wood icons by *deriving* the cart art: vanilla ships the same cart twice, as
`item/minecart.png` and `item/chest_minecart.png`, so the chest's pixels are exactly the ones the
two differ in and a wash could be computed from vanilla's own shading. That was the right answer
while the only cargo was a chest made of planks. It does not extend to a shulker box, a barrel or a
copper chest, and it cannot express a cut-out.

**Build.** One rule, hand-authored art, two providers. An overlay drawn once per cargo shape lies
over whatever texture the cargo is read through — a wood's planks for
`ChestMinecartTextureProvider`, a vanilla block's own texture for `CargoCartTextureProvider` — and
everything after that point is shared in `CartIcons`. Straight source-over, so transparent is the
cargo at its own colour, opaque is the overlay's own pixel, and anything between is shading over the
cargo. No trimming to the overlay's shape: the silhouette comes from the overlay painting `#FF00FF`
where there is nothing, which is the one thing source-over cannot express. Delete the derivation and
the two vanilla icons vendored to feed it.

**Done when.** `generateAssets` writes a sprite, a `minecraft:item/generated` model and an item
definition for every wood and for every vanilla cargo; each reads as a cart carrying that cargo
rather than as a plain cart; and nothing under `plankedchests_vanilla/` is a vanilla item icon any
more.

### As built

**The chroma key is read off the overlay, not off the result.** Same pixels either way today, and
not the same rule: reading it off the composite would let a base that happened to contain the same
magenta punch holes in itself, which for art that follows whatever resource pack the player has on
top is a real case rather than a hypothetical one.

**More was deleted than the request asked for, and it was flagged at the time.** Removing the two
vendored icons meant the derivation had nothing left to read, so `ChestMinecartTextureProvider` lost
about seventy lines — `washOverlay`, `wash`, `luminance`, the reference-pixel constants, the
grey-spread bounds. The reasoning is kept in that class's Javadoc so the idea is recoverable; S5's
own text is untouched, since the record is that it was built that way and later replaced.

**The barrel icon was wrong and looked right.** It was built over `barrel_bottom.png` —
verified md5-identical to the jar's, so not a misnamed file — and read as a barrel lying on its
side. `composite` samples the base 1:1, and the underside's plank gaps fall exactly where the
*side's* iron hoops do, so the eye reads hoops. `barrel_top.png` is both the fix and the honest
choice, since it is the face a cart's barrel actually turns up — see S11.

## S11 — Barrel carts lie on their back, and open — **done**

**Build.** Two things S8 left as vanilla's default rather than as decisions. A barrel's default
state is `facing=north`, which is what it is *registered* with rather than a considered choice for
cargo — a placed barrel takes the direction it was placed from, and a cargo block has none — so a
cart carries an opening pointing out of one side. Lay it on its back: `facing=up`. And a barrel's
openness is a block state, so unlike every other cargo here it is something the cart can simply
send: set `BarrelBlock.OPEN` from `isCargoOpen()` and re-send the state on the open and close
transitions. Sounds with it, from `BarrelBlockEntity`'s own pair.

**Done when.** A barrel cart shows a lid facing up. Opening it creaks, pops the lid open for
everyone watching, and closing it reverses both.

### As built

**The one cargo whose `DisplayState` changes over a cart's life.** `CartCargoSupport` writes that
state once, when the cargo is applied, because for every other cargo it never changes again — so the
barrel needed `PlankedChestMinecart#refreshCargoState`, gated on the cargo being a barrel rather
than made a generic `CartCargoSupport#refresh`. Two reasons, and the second is the load-bearing one:
for a cargo this mod draws itself the applied state is deliberately `AIR`, so pushing the real block
over it would draw the cargo twice; and a refresh would avoid that only by rebuilding the stand-in
from scratch on every open, resetting a chest or shulker lid mid-swing.

**One sound call for every cargo.** `ChestBlockEntity#playSound`, `ShulkerBoxBlockEntity#startOpen`
and `BarrelBlockEntity#playSound` all use half volume, `nextFloat() * 0.1 + 0.9` of pitch, and
`SoundSource.BLOCKS` — only the event differs, so `playLidSound` is shared and `lidSound` answers
the event alone. A chest is asked for its own pair through `ChestBlock#getOpenChestSound`, which is
why a wood shipping custom chest sounds is heard through them, and why the copper chests of S12
arrive with their weathered hinges already working. A cargo that is none of the three gets silence
rather than a guess.

**An addition rather than a fix, and worth saying so:** a vanilla chest minecart is silent. Nothing
in `MinecartChest` or `ContainerEntity` plays anything, because the lid it draws never moves. This
mod's lids move, which is what made the silence audible.

**The finding this stage produced, which is about every other cart.** A barrel's cargo tracks the
cart through a curve perfectly — no perceptible lag even at 120 fps — and the reason is that it is
the one cargo here with *no second entity*. It rides vanilla's own cargo slot, so it does not have a
rotation of its own to keep in step; it is part of the cart being drawn. Every cargo this mod draws
instead poses from where the cart was `DEFAULT_RENDER_LAG` ticks ago (2.8), and S3 already recorded
why no constant closes it: a cart interpolates its rotation *within* a tick and a display element
with an interpolation duration of zero cannot, leaving half a tick of error that oscillates at frame
rate. So the barrel is not better tuned than the others — it is exempt, and the residue is the price
of drawing a cargo at all rather than a bug on the vanilla path. Assessed and declined: there is
nothing to close for a vanilla client, and closing it for a client running this mod is a feature
rather than a mixin — per-connection holder suppression, an openness to sync, a renderer, and a
pose-math refactor. It sharpens the client-mod entry under Open decisions and changes nothing here.

## S12 — Copper chest carts draw their own chest — **done**

**Build.** The second of S8's three cargoes to need what S8 said none of them would — the shulker
boxes were the first, in S9, and the barrel is the one that genuinely did not. S8 read
`BuiltInBlockModels` correctly: it does register `ChestSpecialRenderer.COPPER.pick(state)` for both
halves of the collection (`BuiltInBlockModels.java:89-95`), so a copper chest cart drawn vanilla's
way is at least the right metal. The wrong conclusion was drawn from it, because that path bakes the
lid shut exactly as it does for a plain chest. Route copper chests through `ChestCartDisplay` like
the vanilla chest, with per-state art.

Eight blocks, four variants: a waxed copper chest is the same metal as its unwaxed twin and vanilla
draws the pair from one sheet. So `ChestModels.COPPER` is a `WeatheringCopperCollection<String>`
built with `same`, naming its four after vanilla's own sheets, `ChestCarts#copperChestId` maps a
cargo block onto one, and the two providers walk `.weathering()` rather than the whole collection.

**Done when.** A cart carrying any of the eight draws a copper chest of the right weather state,
with a lid that opens and closes like a wooden one and the hinge sound to match.

### As built

Twenty generated files: four 64×64 sheets copied through from vanilla, eight models, eight item
definitions. Checked that `copper_exposed_chest_lid.json` binds
`"chest": "plankedchests:block/chest/copper_exposed"`, and that the display-part ids
(`copper_chest_base`, `copper_chest_lid`) do not collide with the flat icon id that S10 writes for
the same block (`copper_chest_minecart`).

**A naming hazard left in place on purpose.** `plankedchests_vanilla/` now holds `copper.png` — the
chest *sheet* — beside `copper_block.png`, the block texture S10's icon is composited over; and
`copper_exposed.png` beside `exposed_copper.png`. Both halves of each pair are vanilla's own name
for the file, so renaming either would trade a confusing pair for a name that matches nothing
upstream. Worth knowing about before editing one.

## S13 — Copper cargo weathers — **done**

Reverses S8's answer to its own open question. S8 recorded that a copper chest cart does not
weather, "and no code says so"; this stage says so. It needs S12, which is what gave a copper cargo
a chest of its own to draw.

**Build.** Model the placed block, not the copper golem. Both are vanilla behaviours this could
inherit, and the golem is the closer fit on paper — it is vanilla's own copper *entity*, and it
solves exactly the problem of a copper thing that moves. It ages on a wall clock instead:
`CopperGolem.java:61-62` schedules the next state 504000–552000 ticks out, seven real hours a stage,
with no reference to what is around it. That is right for a pet and wrong here. A cart's cargo is a
copper chest, and the comparison a player actually makes is with the copper chest standing beside
the rail — so take the block's rules whole: its random-tick cadence, its neighbour scan, its game
rule, and its refusal to age while someone has it open.

- **Ageing.** A new `CargoWeathering` in the `cart` package, driven from `PlankedChestMinecart#tick`.
  Two rolls stand in for the random tick the cargo would have been picked for — `randomTickSpeed`
  out of a section's 4096 blocks, then `ChangeOverTimeBlock`'s own per-tick chance — and what
  follows is vanilla's `getNextState`, *called* rather than transcribed, so the four-block scan runs
  against the real world at the cart's position. Gate on `isCargoOpen()`, which is
  `WeatheringCopperChestBlock#randomTick`'s own gate.
- **Waxing and scraping.** Override `interact` on the cart, ahead of the container's click, main
  hand only. Honeycomb through `HoneycombItem.WAXABLES`, axe through `WeatheringCopper.getPrevious`
  then `HoneycombItem.WAX_OFF_BY_BLOCK` — `AxeItem#evaluateNewBlockState`'s order, so one click on a
  waxed oxidized cargo takes the wax and leaves the oxidation. Require a sneaking player, because
  that is the placed block's gate: an ordinary right-click on a copper chest runs the block's use
  and opens it, and only a secondary-use click reaches the item's `useOn`.
- **Nothing new is saved.** The weather state and the wax together are just which of the eight
  copper chest blocks the cart carries, and the cargo block is saved already. Ageing is a
  `setCargo`; so is waxing. A waxed cargo stops ageing for free by not being a `ChangeOverTimeBlock`.

**Done when.** With `random_tick_speed` wound up, a summoned `minecraft:copper_chest` cart walks its
`Cargo` through the weather states and stops at `oxidized`, renaming itself as it goes; a
`waxed_copper_chest` cart does not move at all; and a cart parked next to unaffected copper blocks
holds where it is until they are removed. In world, honeycomb and an axe on a sneak-click wax and
scrape the cargo with the block's own particles and sounds, and an ordinary click still opens it.

### As built

Every clause of "Done when" observed: the ageing half over rcon against the dev server, and waxing
and scraping in world, which is the only way to reach them — rcon cannot send a right-click.

**The numbers.** At `random_tick_speed 4096` a cart aged unaffected → weathered inside two seconds
and reached oxidized by the fourth, then stayed there; a waxed cart sat unchanged through the same
window and through eight seconds more. The neighbour scan was the one worth proving, because a
version of this that never read the world would look identical at a glance: a cart on a rail at
`0 -59 12` with unaffected copper blocks filled in at `0..2 -59..-57 13..14` held at `exposed` for
eighteen seconds while an unobstructed cart aged twice in four, and oxidized within six seconds of
the copper being replaced with air. Restored to 3 afterwards. At that default the cadence works out
at roughly a state every twenty minutes of loaded, ticking time for an isolated cargo — the same
arithmetic as an isolated copper block, which is the whole point.

**One transcribed constant, and it is a maintenance hazard.** `ChangeOverTimeBlock#changeOverTime`
keeps its per-tick chance in a *local* called `eachBlockOncePerDayChance`
(`ChangeOverTimeBlock.java:17-18`), not in a field this could read, so `CargoWeathering.AGE_CHANCE`
is a copy of `0.05688889F` that a Minecraft version could silently invalidate. The section size is
the same kind of copy: `ServerLevel#tickChunk` picks `randomTickSpeed` positions out of each 16³
section every tick (`ServerLevel.java:495-522`, with the rule read at `ServerChunkCache.java:377`),
which is the arithmetic behind `SECTION_BLOCKS`. Everything else is called, not copied.

**Which level events carry their own sound.** `LevelEventHandler.java:426-434`: 3003 spawns the
wax-on particles *and* plays `HONEYCOMB_WAX_ON`, while 3004 and 3005 are particles alone. So waxing
plays nothing of its own — vanilla's `HoneycombItem#useOn` does not either — and the two axe
branches play `AXE_WAX_OFF` and `AXE_SCRAPE` themselves, as `AxeItem#spawnSoundAndParticle` does.
All three are broadcast with a null actor rather than vanilla's "everyone except the player who did
it": a vanilla client has no idea this entity is copper and predicted nothing to play twice.

**A decompiler artifact worth knowing about.** `CopperGolem.java` reads `itemStack.is(Items.SHEARS)`
and `itemStack.is(ItemTags.AXES)`, and neither compiles: `javap` on the 26.2 deobf jar shows
`ItemStack` has exactly one `is`, taking `Predicate<Holder<Item>>`, and neither `Item` nor `TagKey`
implements it. The forms that do compile are `stack.getItem() == Items.HONEYCOMB` and
`stack.typeHolder().is(ItemTags.AXES)`, which is what CLAUDE.md already says. Vineflower output is
a reliable guide to *behaviour* and not to what will build.

**The creative tab caught up while this was open.** S8's `ChestCarts#carts` left the barrel and the
eight copper chests out on the grounds that neither had an icon, "and give them icons and they
belong here" — which S10 had quietly satisfied. They are in now, after the 27
woods and before the shulker boxes, in the order `ChestRecipeProvider` writes the vanilla cargoes'
recipes in, so the mod has one order for them rather than one per place they are listed. All nine
icon definitions exist under `src/main/generated/assets/plankedchests/items/`, so no entry resolves
to a missing model. A tab stack is baked once for every player and never ticks, so a copper entry
does not age in the menu.

**Left for a later hand.** Nothing here names copper chests, so any cargo vanilla knows how to
weather ages — which today is the eight copper chests and tomorrow is whatever a datapack adds to
`plankedchests:cart_cargo`. And the cart *item* does not age, deliberately: no copper item ages in
an inventory, and a cargo that aged in a chest would be the one place this stopped mirroring the
block it is named after.

## S14 — Trapped chest carts, and a comparator that reads them — **done**

Reverses S2's "trapped chests get no cart". S2 gave two reasons and only one of them was a fact:
vanilla has no trapped chest minecart, true then and now — but "a trapped chest has no redstone
meaning inside one" was a decision, and this stage makes a different one. Twenty-seven trapped chest
carts, one per wood, a twenty-eighth carrying vanilla's trapped chest, and a comparator beside the
rail that reads how many players have the cart open. That is the number the placed block gives out,
so the cart keeps the block's meaning rather than inventing one for it.

**Build.** Four pieces, and only the last is new ground.

- **The carts are a re-keying, not new code.** S8's guard already accepts any cargo in
  `plankedchests:cart_cargo`, and vanilla's trapped chest was already in it and already worked. What
  had to go is `ChestCarts`' assumption that a cargo maps to a *wood*: `WOODS_BY_CHEST` becomes
  `CHEST_IDS`, a `Block` → chest-id map filled from `ChestBlocks.chests()` and
  `ChestBlocks.trappedChests()` alike, because one wood now owns two chests. Everything downstream
  carries a chest id instead of deriving one from a wood, which is what
  `ChestModels.cargoCartModel(chestId)` already took — so `cartModel(woodId)` goes, and with it a
  javadoc explaining that there was no trapped variant to tell apart.
- **The 3D cargo and most of the datagen needed nothing.** `ChestElementModelProvider:55` has always
  called `addVariant(files, wood.trappedChestId(), ...)` and `ChestTextureProvider` has always
  composited the trapped sheets, because the *placed* trapped chests need both. Adding the carts is
  one more `sink.write` per wood for the icon, one `addOptional` per wood for the cargo tag, and a
  `cartRecipe` per wood plus one for vanilla's trapped chest.
- **The icon is hand-authored, and had to be.** The trapped-versus-plain difference in the
  placed-chest art is a 32-pixel latch recolour, not a hue shift over the sheet, so no filter turns
  the plain cart sprite into a trapped one — it is drawn, not derived.
  `plankedchests_overlay/trapped_chest_minecart.png` is the plain cart overlay with four latch
  pixels taken from grey to red, carrying the same 97-pixel `PngAssets.CUTOUT` silhouette so it
  drops into `CartIcons.composite` unchanged.
- **The signal, which is this mod's first mixin.** A rail cannot be the signal source. Redstone
  power is answered from a block state alone — `isSignalSource(BlockState)` and
  `hasAnalogOutputSignal(BlockState)` are handed no level and no position — so "is there an open
  trapped cart here" is not a question a rail can be asked at all.
  `ComparatorBlock#getInputSignal(Level, BlockPos, BlockState)` gets both, which makes it the only
  hook, and `ComparatorBlockMixin` injects at its `RETURN`. `TrappedCartSignal` holds the logic so
  the mixin stays a thin inject point, and reuses `DetectorRailBlock#getSearchBB`'s 0.2 inset
  verbatim so that "which rail is this cart on" has one answer on this server — the one players
  already know from detector rails, including a cart wide enough to read on two of them.
  `PlankedChestMinecart#refreshSignal` wakes comparators through
  `Level#updateNeighbourForOutputSignal`, per tick rather than from `startOpen`/`stopOpen`, because
  a cart rolling with its menu open changes which rail carries the reading without either firing.

**Done when.** A birch trapped chest cargo gives **Minecart with Birch Trapped Chest**, craftable
from `<birch trapped chest> + <minecart>`, drawing the trapped chest on the cart and a red-latched
icon in the inventory. A comparator facing the rail a trapped cart stands on reads the number of
players who have it open, and falls back to 0 when the last one closes it or the cart rolls off. A
comparator facing a *detector* rail still reads container fullness exactly as it does today,
whatever cart is parked there.

### As built

Built as described. 27 icons, 27 item definitions and 27 models generated, 28 recipes — the woods
plus vanilla's trapped chest — with their advancements, and 28 trapped entries in
`plankedchests:cart_cargo`. `./gradlew build` clean, Checkstyle clean over both source sets, and the
dev server boots with zero mixin failures, which is load-bearing rather than decorative here:
`plankedchests.mixins.json` is `required` with `defaultRequire: 1`, so a descriptor that failed to
bind would have thrown at `ComparatorBlock` load instead of passing quietly.

**The non-destructive injection is the design, and it is what was tested.** Injecting at `RETURN`
and writing a value only when `signal > 0` means the mixin can raise a reading vanilla left at zero
and can never lower one. Without that guard a closed trapped cart would overwrite a detector rail's
container fullness with a 0 — silently breaking every rail-and-comparator contraption on the server
the moment somebody parked one. Measured over rcon against the dev server, reading the comparator's
own `OutputSignal` off its block entity:

| On the rail | `OutputSignal` |
| --- | --- |
| nothing | 0 |
| **vanilla** chest cart, 3 stacks | **2** |
| trapped cart, 3 stacks, closed | **2** |
| trapped cart, empty, closed | 0 |

The vanilla row is the point of the table: it is the control that says 2 is what this rig reads when
the mixin is not involved, so row three matching it is the regression result rather than a number
that merely looks plausible.

**The half that needs a player.** `openCount` only rises when someone opens the menu and rcon cannot
send a right-click — the same limit S13 hit with waxing and scraping. So the positive leg is a
watched in-world check, not a scripted one.

**Two rig mistakes worth not repeating**, both of which produced a clean, believable set of zeroes
rather than an error: a comparator placed with no support beneath it is removed by its own
`updateShape` immediately after `setblock` reports success, and a diode's `FACING` points at its
*input*, so a comparator built facing the way its arrow appears to point reads the lamp and drives
the rail. Generalised into `docs/TESTING.md` under "In-world checks", along with the probe habits
that let both hide.

## Open decisions

- **Client-mod parity — moved out, and no longer an unknown.** A client running this jar renders
  placed chests for real but still cannot render one *inside a cart*. This now lives in
  `client-mod-support.md` under "Follow-up: a chest inside a cart", where it belongs: both of the
  blockers recorded here turned out to be avoidable — the `BuiltInBlockModels` mixin is unnecessary
  if the mod client gets the real entity type and this mod registers its own renderer, and S1's
  dropped tracked-data route is unnecessary for the same reason, since the cargo can then ride a
  synched accessor on this mod's own entity class. What is left is a scoping call, not research, and
  the cost is the lid rather than the geometry. Vanilla clients keep the stand-in and its tuned
  constant either way, so nothing in this document depends on the answer. S11 added a second thing
  such a client would gain besides fidelity: a cargo it renders itself is part of the cart being
  drawn, so it sheds the half-tick of rotation residue that no value of `DEFAULT_RENDER_LAG` can
  close — the barrel already demonstrates what that looks like, by riding vanilla's cargo slot.
- **Trapped chest minecarts — built, in S14.** The estimate recorded here was wrong in both
  directions and worth keeping for that. It was too low on art: "27 more recipe files and no code at
  all" missed that a trapped cart needs an *icon* of its own, and the trapped-versus-plain
  difference is a localised latch recolour rather than a tint, so nothing could be derived. It was
  too high on the display: the `createCargoDisplay` branch S8 predicted never had to be written,
  because keying cargo by chest id instead of by wood made the trapped chests resolve through the
  same path as every other cargo. The redstone meaning they were said to lack is the one thing here
  that was genuinely a new decision, and S14 makes it.
- **The cart item sends its real registry id on `HANDSHAKE.supportsAll`** (`ChestMinecartItem:39`),
  which is one of three sites now known to be gated on the wrong thing — key parity rather than
  numeric-id parity. It kicks a matching mod client on the launches where Fabric happens to order
  entrypoints differently. Not a cart bug and not fixed here: see `client-mod-support.md`,
  "Deferred: `supportsAll` gates key parity, not numeric-id parity", which owns the gate.
- **Hopper minecart and furnace minecart variants.** The shared machinery is not chest-specific, and
  S4 makes `CargoMinecartChest`'s siblings a matter of picking a different superclass. Out of scope
  until someone asks.

## Reference: chest boats, and why they are deferred

Assessed on 2026-09-10 and deliberately not built. The facts below are verified and are the reason —
they will not change without a Minecraft version change, so re-research is unnecessary.

**There is real content here.** `assets/minecraft/textures/entity/chest_boat/birch.png` and
`.../dark_oak.png` were extracted from the 26.2 deobf jar and compared: the hull region is
wood-tinted, the chest region is identical between them and is the plain vanilla oak chest. Every
vanilla chest boat carries an oak chest regardless of hull wood, so wood-matched chests would be a
visible improvement on all of them.

**But the chest cannot be retextured server-side.** A boat's texture is bound to its entity type and
a vanilla client will not accept a new one, so the chest has to be a display element drawn over a
*plain* boat entity type — never over a chest boat, whose chest cannot be removed.

**And a boat's cargo cannot be mounted the way a cart's can.**
`AbstractBoat.getPassengerAttachmentPoint` (`AbstractBoat.java:136`) returns
`getSinglePassengerXOffset()` for one passenger but switches to a fixed +0.2 / −0.6 pair for two, so
a display element mounted as a passenger sits centred on an empty boat and jumps to one end the
moment a player boards. The alternative — `EntityAttachment` position-following — costs two or three
ticks against a boat whose rider's own client is predicting its motion, which reads as the chest
trailing the boat. Neither is wrong enough to refuse and neither is right; the choice needs to be
made by looking at both in world, which is a session of its own.

**The item combinatorics are solvable and are not the blocker.** The live server has 20 boat woods
(9 vanilla, the bamboo raft, terrestria's 9, and `woods_and_mires`' pine — all of which already ship
chest boat items) against 27 chest woods, so the naive scheme is 540 items. It collapses to 27 by
putting the hull wood in a data component instead of the item id: Polymer round-trips the full
server-side stack through `$polymer:stack` (`PolymerItemUtils.java:57,475`) so a custom component
survives a creative client, and recipes are no longer synced raw — `ClientboundUpdateRecipesPacket`
carries only property sets and stonecutter entries, with the recipe book fed by `Recipe.display()`
— so a code-defined `CraftingRecipe` matching "any boat plus any planked chest" is invisible to a
vanilla client. Icons can be layered `minecraft:generated` models, which is 540 small JSON files but
only 47 sprites, and no registrations at all.

**One wrinkle to remember if this is revived.** `AbstractBoat.getDropItem()` is `protected final`
(`AbstractBoat.java:773`), fed by a `Supplier<Item>` taken in the constructor. Making one entity type
drop per-combination items needs that supplier to read a field that does not exist yet at the
`super()` call — a mutable box passed through a private constructor, or a mixin.

## Reference: verified APIs and numbers

- `AbstractMinecart`: `DATA_ID_CUSTOM_DISPLAY_BLOCK` is `Optional<BlockState>` (line 52);
  `getDisplayBlockState()` / `setCustomDisplayBlockState(Optional)` (lines 579, 599);
  `createMinecart(Level, double, double, double, EntityType<T>, EntitySpawnReason, ItemStack,
  Player)` (line 123); `getPassengerAttachmentPoint` villager-only override (line 180).
- `MinecartChest`: `getDropItem()` and `getPickResult()` are both overridable (lines 30, 35);
  `getDefaultDisplayBlockState()` is `Blocks.CHEST` facing north (line 45), `getDefaultDisplayOffset`
  is 8 (line 50).
- `MinecartItem(EntityType<? extends AbstractMinecart>, Item.Properties)` is public; the type field
  is private with no accessor.
- Components on a placed cart: `EntityType.appendComponentsConfig` (line 145) →
  `Entity.applyComponentsFromItemStack` (line 4038) → `protected applyImplicitComponents(
  DataComponentGetter)` (line 4033), which vanilla itself only uses for `CUSTOM_NAME` and
  `CUSTOM_DATA` (lines 4034-4035). The drop path does *not* round-trip components:
  `VehicleEntity.destroy(ServerLevel, Item)` (line 68) sets only `CUSTOM_NAME`.
- Recipe results carry components: `ItemStackTemplate(Holder<Item>, int, DataComponentPatch)`
  (`ItemStackTemplate.java:19`), taken by `ShapelessRecipeBuilder.shapeless(HolderGetter<Item>,
  RecipeCategory, ItemStackTemplate)` (line 31).
- `DataComponents.ITEM_NAME` (line 132) overrides an item's name without marking it renamed;
  `TranslatableContents` carries an optional `fallback` string (line 41).
- **`Item.getName(ItemStack)` *is* the `ITEM_NAME` lookup in 26.2**, not a fallback consulted after
  it: `ItemStack.getHoverName():795` tries `CUSTOM_NAME` and then `getItemName():817`, which is
  `getItem().getName(this)`, and vanilla's implementation (`Item.java:342`) returns
  `components.getOrDefault(DataComponents.ITEM_NAME, EMPTY)`. Every item is registered carrying a
  default `ITEM_NAME` (and `ITEM_MODEL`) component — `Item.Properties#finalizeInitializer`
  (`:679-686`) sets both. Two consequences for anything that overrides `getName`: the component
  goes dead on that item unless the override answers it, and an override that *does* answer it must
  read `stack.getComponentsPatch()` rather than the resolved component, or it reads back the item's
  own default and shadows whatever it was computing.
- No vanilla `entity_type` tag covers minecarts. All 48 of `data/minecraft/tags/entity_type/` were
  searched and none names one, and `EntityTypeTags` has no cart or vehicle constant;
  `minecarttweaks-0.1.0.jar` ships no entity-type tags either. So there is no tag for this cart to
  join, and the question S6 raised has "none" as its answer rather than a file.
- Polymer: `PolymerComponent.registerDataComponent(DataComponentType…)` marks a component
  server-side-only; the strip happens in `ComponentMapMixin:37` and `DataComponentPatchMixin:46`,
  i.e. at the codec, so it covers stacks of vanilla items too.
- Polymer: `eu.pb4.polymer.core.api.entity.PolymerEntity` (`getPolymerEntityType(PacketContext)`,
  `modifyRawTrackedData`), `PolymerEntityUtils.registerType(EntityType, PolymerSyncedObject)`;
  `eu.pb4.polymer.virtualentity.api.ElementHolder.addPassengerElement`,
  `…api.attachment.EntityAttachment.ofTicking`, `…api.elements.DisplayElement.setTeleportDuration`.
- For S4's `Holder<Block>` cargo: `Registry.holderByNameCodec()` is a default method returning
  `Codec<Holder<T>>`; saved data takes a codec directly through `ValueOutput.store(String, Codec<T>,
  T)` and `ValueInput.read(String, Codec<T>)`, which returns an `Optional<T>`.
- `BlockBehaviour.getDescriptionId()` is `public final`, so a cargo block's translation key is
  readable off the block. `Block.getName()` returns a plain `MutableComponent` with no fallback,
  which is why the key is read and rebuilt rather than that component being reused.
- `Component.translatableWithFallback(String, String, Object...)` exists alongside the two-argument
  form — the args overload is what a "Minecart with %s" pattern needs.
- The `disable_vanilla_recipes` built-in pack stubs `minecraft:chest` and `minecraft:trapped_chest`
  with a single `minecraft:barrier` ingredient rather than deleting the recipe, so with the pack on
  neither a vanilla chest nor anything crafted from one is reachable in survival.
- `WoodType` has 27 entries; all four wood mods it gates on are installed on the live server, so all
  27 register there.
- Cargo and container blocks: vanilla's copper chest is `WeatheringCopperCollection<Block>`, two
  `ByState` maps over four weather states — **eight blocks**, walked with `forEach`/`asList`. Shulker
  boxes are `Blocks.SHULKER_BOX` plus `Blocks.DYED_SHULKER_BOX`, a `ColorCollection<Block>` of
  sixteen. `BlockTags.SHULKER_BOXES` and `BlockTags.COPPER_CHESTS` already exist and hold exactly
  those 17 and 8, so a tag that wants them needs two references rather than twenty-five ids.
  `BuiltInBlockModels.addDefaults` registers a special renderer for every one of them plus vanilla's
  chest and trapped chest, so all of them draw correctly in a cart's own cargo slot; the barrel is an
  ordinary block model and needs nothing.
- `crafting_transmute` (`TransmuteRecipe`) is how a recipe keeps its input's components. Its result
  goes through `ItemStackTemplate#apply(count, patch)`, which builds the stack from the **input's**
  patch and then applies the template's own on top — so the recipe's components win on collision and
  everything else on the input survives. `TransmuteRecipeBuilder.transmute(category, input, material,
  ItemStackTemplate)` is the datagen entry point. Vanilla uses it for all seventeen shulker box dye
  recipes, and for nothing else in the shulker box's vicinity.
- Ingredients in 26.2 are an item `HolderSet` and nothing else (`Ingredient.CODEC` wraps
  `NON_AIR_HOLDER_SET_CODEC`) — there is no component predicate, so a recipe cannot refuse a filled
  container by matching on its contents. Preserving beats refusing for that reason alone.
- `DispenserBlock.DISPENSER_REGISTRY` is keyed on `Item` identity and `DispenseItemBehavior.bootStrap`
  registers `MinecartDispenseItemBehavior` against each vanilla cart item by name (lines 399-404). A
  mod's own cart item therefore has none until it registers one, and `DispenserBlock.registerBehavior`
  is safe to call from a mod initializer — the map is a plain `IdentityHashMap` that is never frozen.
  The behaviour hands the dispensed stack to `AbstractMinecart.createMinecart`, so a dispensed cart
  reads its components exactly as a hand-placed one does. This is also the only way to exercise
  `Entity#applyImplicitComponents` over rcon: `/summon` does not go near it.
- `AbstractMinecartContainer.itemStacks` starts as a **36**-slot `NonNullList` and is only resized by
  `clearItemStacks()`, which is called from `readChestVehicleSaveData` alone. Writing into it during
  `applyImplicitComponents` is therefore safe — nothing replaces the list afterwards on the
  item-placement path, since `appendDefaultStackConfig` applies components first and `entity_data`
  second. `ItemContainerContents#copyInto` walks the *destination*, so a source larger than the
  destination silently loses the overflow.
- `TagsProvider` validates that every **required** tag reference resolves against the tags the
  provider itself defines; `FabricTagsProvider` exposes no parent-provider constructor, so a
  reference to a vanilla tag must use `addOptionalTag` or datagen fails with "missing following
  references". Element ids are not checked the same way — `addOptional` is only about the loaded
  server, not about datagen.
- A container cart spills its inventory from **two** places, not one, and both must be handled
  together: `AbstractMinecartContainer#remove` calls `Containers.dropContents` whenever
  `reason.shouldDestroy()`, and `AbstractMinecartContainer#destroy(ServerLevel, DamageSource)` calls
  `chestVehicleDestroyed` afterwards, which calls it again. `VehicleEntity#destroy(ServerLevel, Item)`
  runs before both — it is what calls `kill` — so clearing the inventory there is upstream of each.
  Note the asymmetry: `chestVehicleDestroyed` checks `ENTITY_DROPS` and `remove` does **not**, so with
  that rule off a broken cart still spills while dropping no cart item. Anything that empties the
  inventory to move it onto a stack has to check the same rule or it becomes the one path that truly
  destroys items.
- **`ItemStack` has exactly one `is`, and it takes `Predicate<Holder<Item>>`.** Verified with `javap`
  against the 26.2 deobf jar; neither `Item` nor `TagKey` implements that interface. The decompiled
  `CopperGolem.java` nevertheless reads `itemStack.is(Items.SHEARS)` and
  `itemStack.is(ItemTags.AXES)`, neither of which compiles — Vineflower output is a guide to
  behaviour, not to what will build. The
  forms that do compile are `stack.getItem() == Items.HONEYCOMB` and
  `stack.typeHolder().is(ItemTags.AXES)`.
- Random ticks, for anything that wants a block's cadence without being a block:
  `ServerChunkCache` reads `GameRules.RANDOM_TICK_SPEED` once a tick (`:377`) and hands it to
  `ServerLevel#tickChunk`, which picks that many positions out of **each 16³ section** every tick
  (`:495-522`) — so a given block's chance of being random-ticked is `randomTickSpeed / 4096`.
  `ChangeOverTimeBlock#changeOverTime` then rolls `0.05688889F` for the copper family, a figure that
  lives in a *local* (`:17-18`) rather than a readable constant. `getNextState` is public and
  side-effect-free: it does the four-block Manhattan scan and returns the next state, leaving the
  caller to place it.
- Copper's two lookup tables are both public suppliers of a Guava `BiMap<Block, Block>`:
  `WeatheringCopper.NEXT_BY_BLOCK` / `PREVIOUS_BY_BLOCK` (with `getPrevious(Block)` and
  `getPrevious(BlockState)` overloads), and `HoneycombItem.WAXABLES` / `WAX_OFF_BY_BLOCK`. Waxed
  blocks appear in neither weathering map, which is what makes `AxeItem#evaluateNewBlockState`'s
  order — scrape, then unwax — take one step per click rather than two.
- **A redstone diode's `FACING` points at its input, not its output.** `ComparatorBlock#
  getInputSignal` reads the block at `pos.relative(state.getValue(FACING))` (`:99-100`), and
  `DiodeBlock` does the same, so the output is at `FACING.getOpposite()`. Placement is what makes
  this read the other way round in game: a diode is placed with `getHorizontalDirection()
  .getOpposite()`, so the arrow a player sees points *away* along the output while the property
  points back at the source.
- Redstone power is answered from a block state and nothing else: `isSignalSource(BlockState)` and
  `hasAnalogOutputSignal(BlockState)` take no level and no position, so a block cannot be a
  *conditional* signal source based on what is standing on it. `getAnalogOutputSignal(BlockState,
  Level, BlockPos, Direction)` does get both, but it is only consulted once `hasAnalogOutputSignal`
  has already said yes. `DetectorRailBlock` is vanilla's own worked example of the shape: it answers
  `true` unconditionally (`:136`) and does the entity lookup in `getAnalogOutputSignal`
  (`:140-155`), gated on `POWERED` — which its `checkPressed` maintains on a 20-tick reschedule
  while a cart sits on it, calling `level.updateNeighbourForOutputSignal(pos, this)` on the way
  past.
- `EntitySelector.CONTAINER_ENTITY_SELECTOR` is `entity instanceof Container && entity.isAlive()`
  (`EntitySelector.java:13`) — the filter a detector rail uses to find a cart whose fullness it
  should report, which any `AbstractMinecartContainer` subclass passes for free.
- Level events 3003/3004/3005 are `LevelEvent.PARTICLES_AND_SOUND_WAX_ON`, `PARTICLES_WAX_OFF`,
  `PARTICLES_SCRAPE`. Only the first plays a sound of its own client-side
  (`LevelEventHandler.java:426-434`); the other two are particles, which is why vanilla's axe path
  plays `AXE_WAX_OFF` and `AXE_SCRAPE` itself. `ServerLevel#levelEvent(Entity, …)` excludes its
  source from the broadcast only when that source is a `Player`, so passing null sends to everyone.
