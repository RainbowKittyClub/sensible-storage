# Modded wood types

Extends the existing 12 vanilla-wood chest/trapped-chest set with 15 wood types from four
content mods already running on the live server, each gated on that mod's presence so one
jar adapts to whatever a given server has installed. Wood-id lists, texture paths/sizes,
Modrinth maven coordinates and the Fabric resource-conditions API surface were verified this
session directly against the four target jars, `fabric-api-0.159.0+26.2`'s nested jars, and
Modrinth's maven — see Reference. `ChestBlocks.java`, `WoodType.java` and the datagen
provider line numbers are as of the current `planked-chests` tree; recheck before editing if
this doc is stale.

S1 is foundational (no other stage compiles meaningfully without it) and independent of S2.
S2 and S4 are independent of each other and of S1, and can be built in any order. S3 depends
on both S1 (new `WoodType` accessors) and S2 (mods loaded so `runDatagen`'s registry lookups
resolve). S5 is cosmetic and can happen any time after S1. S6 is the feature-complete stage
— nothing here works end-to-end until `generateAssets` succeeds against all four mods and a
two-pass dev-server check confirms per-server adaptation.

## How these stages get tested

`./gradlew generateAssets` (alias for `runDatagen`) is the harness that proves the datagen
side of every stage from S2 onward — it must complete without exceptions before any stage
touching `ChestRecipeProvider`/`ChestLootProvider`/`ChestBlockStateProvider` can be called
done. In-world behavior (per-server gating, craftability, rendering) is checked via
`scripts/devserver.sh rkc/mods/planked-chests <cmd>` against two `run/mods` configurations —
see S6. A stage is not finished until its "Done when" is observed; a failure at
`generateAssets` time implicates whichever stage last touched the datagen providers, not the
ones before it.

## Findings that reshape the work

**There is no existing mod-presence-gating pattern anywhere in `rkc/mods`.** A full-repo
grep found zero uses of `FabricLoader.isModLoaded`; the only cross-mod-interop precedent
(`rk-tweaks`'s `"suggests": {"flamingo": "*"}` plus a default-off compat datapack) never
checks presence in Java — it just ships a tag entry that's harmless if the target item is
absent. This work introduces `isModLoaded` to the codebase for the first time (S1); keep the
check minimal and local rather than building an abstraction around it.

**Static per-wood recipe/loot JSON cannot ship unconditionally.** Because one jar must adapt
to whatever mods a given server has, and registration (S1) skips a wood whose mod is absent,
a datagen'd recipe or loot table still referencing that wood's chest item would fail to
resolve at every boot on a server missing that mod — the same class of problem this project
already tolerates for stale `level.dat` keys, but avoidable here.
`fabric-resource-conditions-api-v1`, bundled in the already-pinned `fabric-api-0.159.0+26.2`
(confirmed present via `javap` against the actual jar, not assumed), solves this: both
`FabricRecipeProvider` (which `ChestRecipeProvider` already extends) and the
`FabricBlockLootSubProvider` mixin (applied onto vanilla `BlockLootSubProvider`, which
`ChestLootProvider` already extends) expose a `withConditions(ResourceCondition...)` that
gates the emitted JSON on `ResourceConditions.allModsLoaded(modId)`. Blockstate/model/item
JSON is untouched by these mixins and is inert when orphaned — no conditioning needed there.

**WoodsAndMires has no public Modrinth coordinate at the version running on live
(`2.1.3-rkc`); Terrestria, Enderscape and Cinderscapes do.** Fetching Modrinth's maven
(`https://api.modrinth.com/maven`) at the live server's exact versions and sha1-comparing
against the live jars confirmed Terrestria and Cinderscapes are byte-identical, and
Enderscape matches by size under a different artifact filename (`enderscape-3.0.2.jar`
rather than `enderscape-fabric-3.0.2+mc26.2.jar`). WoodsAndMires is the RKC fork with no
such coordinate, but its source is already checked out at `rkc/forks/WoodsAndMires` — the
same "no public coordinate, build from local source into `mavenLocal()`" situation this
project already documents and solves for `libs/blockbench-import-library` (`bil`).

## S1 — `WoodType` gains a required-mod id, `ChestBlocks` gates on it — **done**

**Build.** `WoodType.java` becomes a constructor-parameterized enum: every constant takes a
nullable `requiredModId` (the 12 existing constants pass `null`; the 15 new constants pass
their source mod id — see Reference for the full list). Add `requiredModId()` and
`planksNamespace()` (`requiredModId() == null ? "minecraft" : requiredModId()`, since all
four target mods' block namespace equals their mod id). `displayName()`'s existing
underscore-split + title-case logic needs no change — verified it already produces correct
names for all 15 new ids. In `ChestBlocks.java:86`, the first line of the
`for (WoodType wood : WoodType.values())` loop in `init()` becomes a guard:
`if (wood.requiredModId() != null && !FabricLoader.getInstance().isModLoaded(wood.requiredModId())) continue;`.
Nothing else in `ChestBlocks` changes — the `CHESTS`/`TRAPPED` maps, the creative tab, and
the `BlockEntityType`'s block set all already derive purely from what got registered.

**Done when.** `./gradlew build` compiles with the expanded enum and the new import
(`net.fabricmc.loader.api.FabricLoader`); a unit-free sanity check — temporarily dropping
one target mod's jar into `run/mods` and confirming via `/polymer creative` or RCON that
exactly that wood's chest appears alongside the 12 vanilla ones — is deferred to S6's full
two-pass check rather than repeated here.

### As built

One addition the plan didn't anticipate: `ChestBlocks.init()` also checks
`System.getProperty("fabric-api.datagen") != null` (a private `DATAGEN` constant, verified against
`FabricDataGenHelper`'s bytecode — no public API exposes it) and registers every `WoodType`
unconditionally when it's set, regardless of `isModLoaded`. This exists because of S2's outcome
(below) — none of the four target mods are ever a real dependency of this mod, so `isModLoaded`
would otherwise be `false` for all 15 modded woods during `runDatagen` too, and
`ChestLanguageProvider`/`ChestRecipeProvider`/`ChestLootProvider` all read `ChestBlocks.chests()` /
`.trappedChests()` expecting every `WoodType` to have a real `Block`.

**The `isModLoaded` guard later turned out to be a client/server hazard, and no longer stands alone.**
Because it makes the registered set depend on what that side has installed, a client running this mod
with a different mix of the four wood mods registered a different set from the server's — a
divergence Polymer hides from Fabric's registry sync, so it surfaced as
`DecoderException: Failed to decode packet` during Polymer's item sync rather than as a readable
error. The guard is still here and still correct; what was missing is that its answer is now
advertised over the Polymer handshake, and real content is only sent to a connection whose client
registered every wood this side did. See `rkcore/docs/plan/handshake-capability-keys.md`, whose S4
also records why per-wood granularity does not work and connection-wide does.

## S2 — Datagen dependency wiring — **done, differently than planned**

**Build.** Uncomment the `mavenLocal()` line in `rkc/forks/WoodsAndMires/build.gradle`'s
`publishing.repositories` block and run `./gradlew publishToMavenLocal` there. In
`planked-chests/build.gradle`, add Modrinth's maven repo
(`https://api.modrinth.com/maven`) alongside the existing `mavenLocal()`/Nucleoid entries,
and add dependencies on WoodsAndMires' local coordinate plus
`maven.modrinth:terrestria:8.1.0-alpha.1`, `maven.modrinth:enderscape:3.0.2`,
`maven.modrinth:cinderscapes:6.1.0-alpha.2` (reconfirm the exact Modrinth group id against
the resolved POM, not from memory). Pin all four versions as new `gradle.properties`
properties per this project's "nothing hardcoded in `build.gradle`" rule. These four must
not appear in `fabric.mod.json`'s `depends`, must not be Loom-`include()`d, and must not
land in `shadowJar`'s output — keep them out of the `shade` configuration.

**Done when.** `./gradlew dependencies` resolves all four without error and none appear
under the `shade`/`rkcore` configurations.

Flagged unknown: whether Loom 1.17.19's dev-run mod discovery for the already-existing
`datagen` source set (`fabricApi.configureDataGeneration { createSourceSet = true }`)
requires `modImplementation` (or a datagen-scoped equivalent) rather than plain
`implementation` — every dependency in this mod today is consumed as a plain library, never
as "a mod that must be discovered as installed," so this is untested ground here. The
`modImplementation` keyword itself is confirmed still present and wired in the pinned Loom
jar. Resolve empirically: add one dependency, print
`FabricLoader.getInstance().isModLoaded("terrestria")` from the datagen entrypoint, run
`./gradlew runDatagen`, confirm `true` before wiring the rest.

### As built

The flagged unknown resolved cleanly (plain `implementation` is enough — this Loom setup doesn't
even define `modImplementation`, consistent with there being no mappings to remap in 26.x), but
wiring the four mods as real dependencies turned out to be the wrong move entirely, caught before
committing to it: Enderscape's own `fabric.mod.json` hard-`depends`s on three more mods
(`lithostitched`, `trimpatcher`, `yet_another_config_lib_v3`), none of which resolve from
Modrinth's maven under the coordinate the live server actually runs (a `version_number`/`version_id`
collision — `maven.modrinth:enderscape:3.0.2` silently serves a different, `mc26.1`-targeted file
than the live jar). Tracing what the four dependencies were actually *for* — one line in
`ChestRecipeProvider`, resolving a plank `Item` by `Identifier` for the recipe ingredient — showed
none of them were needed at all: an item tag with a single, `required: false` entry does the same
job without ever needing the source mod present at build or datagen time (see S3). **All four
Gradle dependencies, the Modrinth/MinecraftForge/TerraformersMC repos, and publishing WoodsAndMires
to `mavenLocal` were reverted.** This mod has zero build-time dependency on any of the four target
mods — S1's `DATAGEN` override (above) is what makes `generateAssets` still produce output for all
27 woods.

## S3 — Namespace-correct, mod-gated datagen output — **done, differently than planned**

**Build.** `ChestRecipeProvider.java:42-44` — replace
`Identifier.withDefaultNamespace(wood.id() + "_planks")` with
`Identifier.fromNamespaceAndPath(wood.planksNamespace(), wood.id() + "_planks")` (the same
factory already used at `PlankedChests.java:23`, no new API surface). `ChestBlockStateProvider.java:41`
(via `particleModel`, lines 64-66) — drop the hardcoded `"minecraft:block/"` prefix and
build `wood.planksNamespace() + ":block/" + wood.id() + "_planks"` at the call site instead;
this one needs no registry lookup, it's a plain string. In both `ChestRecipeProvider` and
`ChestLootProvider`, wrap output for any wood with `requiredModId() != null` through
`withConditions(output, ResourceConditions.allModsLoaded(wood.requiredModId()))` (see
Findings); vanilla woods keep using the plain output.

**Done when.** `./gradlew generateAssets` completes with all four S2 dependencies present
and produces recipe/loot JSON for all 27 woods, each modded entry's JSON carrying a
`fabric:load_conditions` block naming its mod id (inspect a generated
`data/plankedchests/recipe/pine_chest.json` directly to confirm).

Flagged unknown: confirm `FabricBlockLootSubProvider.withConditions` (the interface default
method mixed onto vanilla `BlockLootSubProvider`) compiles cleanly as an inherited call on
`ChestLootProvider` without an explicit cast — should just work since it's inherited through
the same supertype chain `ChestLootProvider` already extends, but verify by compiling rather
than assuming.

### As built

Confirmed as planned: `withConditions` is a plain inherited call on `ChestLootProvider`, no cast
needed. The one real deviation is the recipe's planks ingredient (S2's As built): for a modded
wood, `ChestRecipeProvider` defines `#` against a `TagKey<Item>` (`ShapedRecipeBuilder.define`
and `RecipeProvider.has` both have a `TagKey<Item>` overload in 26.2 — verified via `javap`) rather
than resolving an `Item` from the registry, backed by 15 hand-written, static (not datagen'd —
`fabric-data-generation-api-v1` 25.5.1 has no plain `FabricTagProvider`, only the differently-named
`FabricTagsProvider`, and 15 one-entry tags don't earn a whole provider) resource files at
`data/plankedchests/tags/item/<wood>_planks.json`, each a single `{"id": "<mod>:<wood>_planks",
"required": false}` entry — the same pattern `minecart-tweaks` already uses for its item tags. The
recipe's *output* (the chest/trapped block itself) still needs `withConditions` exactly as planned;
only the ingredient side changed.

## S4 — Texture vendoring — **done**

**Build.** Extract the 15 already-confirmed-16×16 plank PNGs (paths in Reference) from the
four jars into `src/datagen/resources/plankedchests_planks/<wood.id()>_planks.png`,
mirroring the 12 vanilla files already there — `ChestTextureProvider.java:36,68` reads
`PLANK_DIR + wood.id() + "_planks.png"` off the classpath by filename only, no namespace
involved.

**Done when.** `ls src/datagen/resources/plankedchests_planks/` lists 27 files.

### As built

Went as planned, except the source jars: with S2's dependencies gone, all 15 were extracted
directly from the live server's actual jars (`/mnt/WORKSPACE/minecraft-server-data/data/mods/`) —
sha1-identical to what Modrinth's maven serves for WoodsAndMires, Terrestria and Cinderscapes
(reconfirmed), and the one jar that was *not* identical (Enderscape — see S2) never needed to be
resolved as a Gradle coordinate at all this way. All 27 confirmed 16×16 by reading each PNG's
`IHDR` chunk directly, not just the 15 new ones.

## S5 — Suggests, docs — **done**

**Build.** Add a `"suggests"` block to `fabric.mod.json` listing the four mod ids, mirroring
`rk-tweaks`'s existing `"suggests": {"flamingo": "*"}` precedent, even though nothing reads
it programmatically.

**Done when.** `validateFabricModJson` passes with the new block present in the generated
file.

## S6 — Full build and two-pass verification — **done**

**Build.** Nothing new — this stage runs the full pipeline (`generateAssets`, `build`,
`rkc-lint`'s `lintAll`) and exercises it in-world against two `run/mods` configurations.

**Done when.** Two `scripts/devserver.sh` passes both boot clean with zero
`Mixin apply failed`/recipe-or-loot-parse-error log lines:

- **Pass A — none of the four target mods in `run/mods`.** Creative tab (`/polymer
  creative` or in-game) shows exactly 12 chests + 12 trapped chests.
- **Pass B — all four target mods copied into `run/mods`.** Creative tab shows all 27 +
  27; craft at least one modded chest (e.g. pine) in survival to confirm the recipe
  resolves against the modded planks item and the item/block render correctly.

### As built

Both passes confirmed clean (0 mixin errors, 0 recipe/loot parse errors) via
`scripts/devserver.sh rkc/mods/planked-chests mixins` and a log grep. Pass A verified via
`/setblock` (no player connected to the dev server): the 12 vanilla chest/trapped-chest blocks
place normally; every modded one (`plankedchests:pine_chest`, `celestial_trapped_chest`, etc.)
correctly comes back `Unknown block type`. Pass B (all four target mods plus Enderscape's own
hard dependencies — `lithostitched`, `trimpatcher`, `yet_another_config_lib_v3` — copied from the
live server into `run/mods`, gitignored/throwaway) confirmed all 27 + 27 place, and that a vanilla
and a modded chest (`oak_chest` vs. `pine_chest`) accept the identical `[facing=…,type=…]`
blockstate and share the same block-entity type — the "same signature as vanilla" check, since
`ChestBlocks.init()` never branches on `requiredModId()` for how a block is built, only whether it
runs at all. Crafting itself was not exercised live (no connected player during this pass); the
recipe JSON parsing with zero boot errors, plus the tag resolving correctly once the source mod is
present, is the extent of headless verification — `run/mods` was left with those jars in briefly
for this check, then removed and the dev server restarted back to its normal (no target mods)
baseline, since Terrestria/Enderscape/Cinderscapes worldgen could otherwise alter the existing
dev world on further play.

## Reference: wood ids and sources

| Wood id | Required mod id | Source |
|---|---|---|
| pine | `woods_and_mires` | WoodsAndMires |
| redwood, hemlock, rubber, cypress, willow, japanese_maple, rainbow_eucalyptus, sakura, yucca_palm | `terrestria` | Terrestria |
| celestial, murublight, veiled | `enderscape` | Enderscape |
| scorched, umbral | `cinderscapes` | Cinderscapes |

No id collides with a vanilla `WoodType` constant or across these four mods. Every plank
texture lives at `assets/<modid>/textures/block/<wood>_planks.png` inside its jar and was
confirmed 16×16 by reading the PNG `IHDR` chunk directly out of the live-server jars
(`WoodsAndMires-2.1.3-rkc+26.2.jar`, `terrestria-8.1.0-alpha.1.jar`,
`enderscape-fabric-3.0.2+mc26.2.jar`, `cinderscapes-6.1.0-alpha.2.jar`) this session — the
size `ChestTextureProvider.tile()`'s 4×4 logic assumes.

`enderscape-polymer-patch-3.0.1.1+26.2.jar`, alongside the main Enderscape jar on the live
server, adds no wood/plank content — it's a Polymer compatibility shim for Enderscape's
other blocks and is irrelevant here.

## Reference: verified Fabric API surface (fabric-api-0.159.0+26.2)

`fabric-resource-conditions-api-v1` version `6.1.0+10e9a28b9e`, confirmed present as a
nested jar and `javap`'d directly rather than assumed from memory:

- `net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions.allModsLoaded(String...)`
- `FabricRecipeProvider.withConditions(RecipeOutput, ResourceCondition...)` (`protected`,
  already `ChestRecipeProvider`'s superclass)
- `net.fabricmc.fabric.api.datagen.v1.loot.FabricBlockLootSubProvider.withConditions(ResourceCondition...)`
  (`default`, mixed onto vanilla `BlockLootSubProvider` via `BlockLootSubProviderMixin`,
  which `ChestLootProvider` extends through `provider.FabricBlockLootSubProvider`)
