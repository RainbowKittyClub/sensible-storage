# Chest Boats

A chest boat that carries a planked chest, built the way `PlankedChestMinecart` carries one: a
mod-owned entity, Polymer-disguised to every client as the matching vanilla chest boat, with the
actual chest drawn by a display entity riding it. The disguise entity's own texture is a per-hull-
wood derivative of vanilla's own `entity/chest_boat/<wood>.png`, with the chest region's alpha set to
0 — a genuinely separate asset from vanilla's, not vanilla's own texture reused. A client without
the mod is told this is a real `<wood>_chest_boat` and renders it with *vanilla's own* texture, chest
and all, since the modified one only exists in this mod's own resources; a client with the mod draws
the modified hull, chest-shaped hole and all, with the display entity's cargo filling the hole. This
supersedes this doc's own first draft, which reached for a data component and a mixin into vanilla's
`AbstractBoatRenderer` — reasonable from the outside, but the cart's own code already contains part
of the answer for why that path is harder than it looks, cited below. Every API cited is checked
against `libs/decompiledjava/minecraft-26.2/` or this mod's/kalic0re's own source at the given
file:line, except the two items still flagged unverified in Findings.

This reopens the "no new boats" decision from the first draft. Building it the cart's way means a
mod-owned entity type and item per hull wood, the same way `sensiblestorage:chest_minecart` exists
alongside vanilla's own chest minecart — not a data component bolted onto vanilla's own items.
Confirm that trade is still wanted before S1: it buys a proven rendering path and costs one
registered item per hull wood instead of zero.

Stage order: S1 (entity + disguise) is complete on its own for a client without the mod, who sees
nothing different from vanilla — but for a client with it, S1 alone is a boat with a chest-shaped
hole in it, since nothing is riding it yet to fill the gap. S2 (cargo display) is what fills that
gap and is this feature's actual completion point for a modded client. S3 (recipes) can be built in
parallel with S1/S2 using a hand-summoned test entity, but nothing crafts a real one until it lands.
S4 (gating modded hull
woods) rides along with whichever of S1/S3 first touches a modded wood, same as the first draft.

## Findings that reshape the work

**Disguise as the matching chest boat, not the plain one — a plain-boat disguise would make the
mod's own upgrade look like a downgrade to everyone without it.** The first draft of this doc
reasoned by analogy to the cart's own choice (`ChestCarts` presents as `EntityTypes.MINECART`, the
plainest cart, per `CargoMinecarts.java:150-163`) and recommended disguising as `EntityTypes.
<WOOD>_BOAT` instead of `<WOOD>_CHEST_BOAT`. That analogy does not transfer: a cart disguised as
plain still opens a 27-slot menu on right-click, so *some* mismatch between look and behaviour is
unavoidable for it either way, and vanilla's minecart display-block mechanism draws the default
cargo correctly regardless of which cart type is shown. A boat has no such fallback — if the disguise
target is a chest-less plain boat, a client without this mod (or without its resource pack) sees a
rowboat that opens a container, with no chest anywhere, for every single one of these entities. That
is strictly worse than what exists today. Disguising as the real `<wood>_chest_boat` instead costs
nothing for that client: it is genuinely one, rendered from vanilla's own unmodified assets, chest
included — the "invisible chest, filled in by a display entity" trick is purely a modded-client
enhancement layered on top, never visible to, or a downgrade for, anyone without it.

**The cart's blanking bug does not apply here, because it is not the same mechanism.** The cart's
problem — a plain vanilla chest minecart summoned with an air `DisplayState` still shows its chest,
"vanilla's behaviour rather than anything Polymer or a consumer does" (`CargoMinecarts.java:158-160`)
— is about a *reference* the renderer reads (which block a cart's `DisplayState` names) refusing to
go away when blanked. Zeroing a texture region's alpha is a different, much more ordinary mechanism:
an entity's texture is painted onto its mesh by an ordinary alpha-tested draw, the same way any
texture with transparent pixels already works on any Minecraft model. There is no equivalent
"vanilla insists on drawing it anyway" step to hit. This is not yet verified in-game — see S1 — but
there is no reason from the source to expect the cart's specific failure mode here.

**A mod-owned entity is not optional overhead here — it is the thing that avoids the mixin.**
`CargoMinecartChest`'s `createCargoDisplay()` hook (`CargoMinecartChest.java:148-150`) is what lets a
per-instance cargo visual exist without touching vanilla's renderer: it is a method on a class this
mod owns. Vanilla's own `ChestBoat` offers no such hook, and cannot be given one without a mixin —
the exact thing a mod-owned subclass avoids. So "build it the way minecarts are built" and "no new
boat item" are in direct tension; this doc assumes the former, reopening the latter (see the
preamble).

**One entity type per hull wood, not one shared type.** `CargoMinecarts#register`'s disguise target
is a fixed constant closed over the registration call (`(registered, context) -> clientType`,
`CargoMinecarts.java:180-186`) — one mod entity type, one disguise, forever. A cart can share one
type across every wood because a minecart has no wood of its own to disguise as; a boat's hull does,
so a `sensiblestorage`-owned boat entity needs one registration per hull wood, each closed over that
wood's own `EntityTypes.<WOOD>_CHEST_BOAT` as its disguise target. `ChestBoat`'s constructor is generic
enough for this (`EntityType<? extends ChestBoat>`, `ChestBoat.java:10`), the same shape
`MinecartChest`'s is for carts.

**The render-lag tuning that made the cart fiddly is very unlikely to recur, per the prior
investigation this doc already carries forward.** `AbstractBoat` uses the same generic
`InterpolationHandler(this, 3)` (`AbstractBoat.java:65`, `InterpolationHandler.java:11`) most
ordinary entities use, not a rail-specific batched-replay system like `NewMinecartBehavior`. Whether
a boat's continuous rocking introduces a smaller tuning problem of its own is still a "resolve by
building it" item — see S2.

**Terrestria's and WoodsAndMires' own boat woods are still unverified as hull candidates.**
WoodsAndMires registers `PINE_BOAT`/`PINE_CHEST_BOAT` through `TerraformBoatItemHelper`
(`WoodsAndMires/src/main/java/juuxel/woodsandmires/item/WamItems.java:21-22`); terrestria does the
same per-wood in `WoodItems`'s constructor (`terrestria-rkc/.../init/helpers/WoodItems.java:60-61`).
Neither mod's own entity type ids, nor how many of terrestria's woods actually get a boat, are
confirmed — pull the built jars before S4 assumes a specific list.

## How these stages get tested

Needs the dev server (`scripts/devserver.sh rkc/mods/sensible-storage restart`) and a real client —
same reasoning as the first draft: a wrong disguise target or a cargo posed one frame late both
"work" by every command-line check and only show up watching the water. S1 alone can be checked with
`/summon` and a spectator camera before any recipe exists to craft one normally.

## S1 — Cargo boat entity and disguise — **planned**

**Build.** In `kalic0re` `common/vehicle`: a `CargoBoats`-equivalent of `CargoMinecarts` (registration
helper, cargo component, dispense behaviour if boats have one worth replicating) and a
`CargoChestBoat`-equivalent of `CargoMinecartChest`, extending vanilla's `ChestBoat` the way that
class extends `MinecartChest`. In `sensiblestorage`: a `PlankedChestBoat` per hull wood this build
supports, each registered with `CargoBoats.register` closed over that wood's own
`EntityTypes.<WOOD>_CHEST_BOAT` as the Polymer disguise target — mirroring `ChestCarts.init` but keyed
on hull wood instead of being a single type. Each also needs a client-side renderer registered for a
handshake client, using a per-hull-wood texture datagen derives from vanilla's own
`entity/chest_boat/<wood>.png` (vendored the way `ChestTextureProvider` already vendors
`entity/chest/normal.png`, under `VANILLA_DIR`) with the chest region's alpha zeroed — a new provider
input, not the `sensiblestorage_overlay/entity/chest_boat.png`/`icon/chest_boat_icon.png` overlays
already authored for this feature, which are for the *cargo's* own look (S2) and may turn out to
already be redundant with the existing per-wood chest textures `ChestTextureProvider` generates —
worth settling once S2 is in front of you rather than guessing now.

**Done when.** A client without the mod sees no difference at all from vanilla's own
`<wood>_chest_boat`, chest included; a client with the mod, before S2 exists, sees the same boat with
a chest-shaped hole in it, and the real entity's container/interaction behaviour (opening a 27-slot
menu, comparator output while open) matches vanilla's chest boat either way.

## S2 — Cargo display — **planned**

**Build.** A `CargoDisplayHolder`-equivalent riding the boat, drawing the wood-specific planked
chest the way the cart's draws its cargo. Whether this can literally reuse `CargoDisplayHolder`
(generalized off `AbstractMinecart`) or needs its own boat-shaped sibling is a design call for
whoever starts this stage — the posing math (`applyPose`, `cartFrame`, `cargoOrigin`) is
vehicle-shape-specific in its comments even where the mechanism is generic. The lag-compensation
constant (`DEFAULT_RENDER_LAG = 2.8` for carts) needs its own boat-side value, found the same way the
first one was: by eye, on a moving boat, because there is no other way to find it — see Findings for
why it is expected to need a smaller correction, not zero.

**Done when.** A client running the mod sees a wood-specific chest sitting correctly posed on a
boat under way — turning, rocking, coming to rest — with no visible lag or lead against the hull's
own motion.

## S3 — Recipes — **planned**

**Build.** Redirect every relevant `chest_boat`/`chest_raft` recipe (this mod's ten vanilla
overrides, `bamboo_chest_raft`, and terrestria's/WoodsAndMires' own once S1 picks their hull woods)
to produce this mod's own per-hull-wood item instead of vanilla's, the way
`data/minecraft/recipe/chest_minecart.json` already redirects to `sensiblestorage:chest_minecart`.
The result needs the cargo component set from whichever chest ingredient actually matched —
`PlankFallbackRecipe` (`club.rainbowkitty.sensiblestorage.recipe.PlankFallbackRecipe`) is the
precedent for a custom recipe that inspects the real matched stack rather than trusting a static
JSON result.

**Done when.** Crafting a boat with a planked chest yields this mod's own item, carrying that
chest's identity, for every gated hull wood; a vanilla `minecraft:chest` still produces a sane
default-cargo result, matching `ChestCarts.defaultCargo()`'s precedent.

## S4 — Gate modded hull woods — **planned**

**Build.** Whichever hull wood needs a mod present (terrestria, woodsandmires) is gated the same way
`ChestBlocks#init` gates a modded chest wood at runtime (`ChestBlocks.java:106-110`) and
`ChestRecipeProvider` gates a modded wood's recipe output at datagen time
(`ChestRecipeProvider.java:83-86`) — applied here to which hull-wood entity types/items get
registered at all, a new axis alongside the existing chest-wood gating.

**Done when.** A build without terrestria/woodsandmires installed never registers or offers their
hull woods; a build with them installed does, with no missing-mod errors either way.

## Open decisions

- **Hull-wood scope for a first landing.** All of vanilla's ten plus terrestria's and WoodsAndMires'
  own (the user's stated preference) vs. shipping vanilla's ten first and adding the modded hulls
  once S1–S3 are proven — the latter shrinks the S1 registration list and the S4 gating surface for
  an initial test pass, at the cost of a second small wave of work later.
- **Cargo component reuse.** Whether a boat's cargo shares the cart's literal registered component
  (`CargoMinecarts.CARGO_COMPONENT_PATH`, `sensiblestorage:cart_cargo`) or gets its own — the path
  name is cart-flavoured even though the underlying `Holder<Block>` idea is vehicle-agnostic, and a
  boat's cargo is always a chest where a cart's can be a barrel or shulker box too.
- **Terrestria's and WoodsAndMires' actual hull-wood list and entity ids.** Unverified per Findings —
  needed before S1's registration loop and S4's gating can be written concretely.
