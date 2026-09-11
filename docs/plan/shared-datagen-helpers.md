# Shared datagen helpers (rkcore ↔ companions ↔ planked-chests)

Records the shared-code state after the 2026-09-08 refactor that pulled duplicated datagen
plumbing into `rkcore` and moved `planked-chests`' recipes and loot to datagen. Not deployed to
the live server (the running jar predates it).

## What is shared, and how

`rkcore` carries two packages that `companions` and `planked-chests` both consume:

| Package | Contents | Used by |
| --- | --- | --- |
| `common/item` | `ItemRegistration`, `PolymerModelItem`, `PolymerModelBlockItem` | both mods' main source |
| `common/datagen` | `PngAssets` (classpath PNG read + hash-then-`writeIfNeeded` write) | both mods' datagen source |

Neither consumer takes a Fabric mod dependency on `rkcore` — both must install standalone. Each
shades the two packages into its own jar with the Shadow plugin and relocates them under
`club.rainbowkitty.<mod>.shaded.rkcore`, so two consumer jars never define the same class name
side by side. Only those two packages are shareable; `rkcore`'s own feature and machinery
packages (`common/label`, `common/mixin`, `common/rule`, `books`, `frame`, `armorstands`) must
never leak, so each `rkcoreSharedJar` filter lists `common/item/**` and `common/datagen/**`
explicitly rather than `common/**`.

### Build wiring (identical in both consumers)

- `plugins`: `id 'com.gradleup.shadow' version '9.6.1'`
- `repositories`: `mavenLocal()`
- `configurations { rkcore; shade; implementation.extendsFrom shade }`
- `rkcoreSharedJar` (`Jar` task): `zipTree` the `rkcore` config, `include 'club/rainbowkitty/rkcore/common/item/**'` and `'club/rainbowkitty/rkcore/common/datagen/**'`
- `dependencies`: `rkcore("club.rainbowkitty.rkcore:rkcore:${project.rkcore_version}") { transitive = false }`, `shade files(tasks.named('rkcoreSharedJar'))`
- `jar { archiveClassifier.set('unshaded') }`
- `shadowJar { archiveClassifier.set(''); configurations = [project.configurations.shade]; relocate 'club.rainbowkitty.rkcore.common', 'club.rainbowkitty.<mod>.shaded.rkcore' }`
- `tasks.named('assemble') { dependsOn shadowJar }`
- `processResources { exclude '.cache/**' }` — datagen's hash cache; `jar` excludes it, `shadowJar` would not
- `gradle.properties`: `rkcore_version=0.1.0`

The shipped artifact is the unclassified `<mod>-<version>.jar` (the `shadowJar` output), **not**
`-unshaded`. Dev mode (`runServer`) runs against the un-relocated classes off the compile
classpath and never exercises the relocation — only a real jar install does.

### Workflow consequence

Run `cd rkc/mods/rkcore && ./gradlew publishToMavenLocal` after any change to `common/item` or
`common/datagen`, or both consumers build against a stale copy.

## `PngAssets`

`final` utility, private ctor, two static methods extracted from what `ChestTextureProvider` and
`SweaterTextureProvider` each carried:

- `read(ClassLoader loader, String classpathPath)` — `getResourceAsStream`, `ImageIO.read`,
  redraw onto a fresh `TYPE_INT_ARGB` image, `UncheckedIOException` on failure.
- `save(CachedOutput cache, Path path, BufferedImage image, String executorName)` —
  `ByteArrayOutputStream` + `HashingOutputStream(Hashing.sha1())` + `ImageIO.write(…, "PNG", …)` +
  `cache.writeIfNeeded`, on `Util.backgroundExecutor().forName(executorName)`.

`rkcore` itself never calls it — like `ItemRegistration`, it ships in the runtime jar unused and
exists only to be shaded. Compiles on `rkcore`'s existing classpath (`net.minecraft.data.*` and
`com.google.common.hash` are in the merged MC jar, `javax.imageio` is JDK).

The two texture providers keep only their mod-specific `Graphics2D` step — `tile`/`composite` in
`ChestTextureProvider`, `tint` in `SweaterTextureProvider`.

## planked-chests recipes and loot → datagen

The 48 hand-written JSON files under `src/main/resources/data/plankedchests/{recipe,loot_table}/`
were deleted and replaced by two providers:

- **`ChestRecipeProvider extends FabricRecipeProvider`** — over `WoodType.values()`: a shaped
  plank ring → chest (`RecipeCategory.DECORATIONS` → `"category": "misc"`), and a shapeless
  chest + `minecraft:tripwire_hook` → trapped chest (`RecipeCategory.REDSTONE` → `"redstone"`).
- **`ChestLootProvider extends FabricBlockLootSubProvider`** — `createNameableBlockEntityTable`
  per block for every chest and trapped chest.

Diff vs. the deleted files:

- Recipe bodies match, except the generated shaped recipe drops the redundant explicit
  `"category": "misc"` (MISC is the codec default).
- Loot tables match, except Fabric's provider does not emit `random_sequence` — it never has;
  every Fabric mod's block loot omits it. The only effect: the `survives_explosion` roll is
  unseeded rather than tied to a named sequence.
- Datagen now also writes `data/plankedchests/advancement/recipes/**` (24×2) because
  `RecipeBuilder.save` requires a criterion. The recipes become recipe-book unlocks with a
  first-craft toast, matching vanilla chests; nothing changes about what is craftable.

**Stays hand-written:** `data/minecraft/recipe/chest.json` and `trapped_chest.json` (the
unsatisfiable `minecraft:barrier` overrides that suppress the vanilla plank-ring recipe) and
`data/minecraft/tags/block/mineable/axe.json`. These are `minecraft`-namespace overrides/merges,
not per-wood repetition, and there is no clean builder for an intentionally unsatisfiable recipe.

The `planked-chests` `shadowJar` datagen-output guard checks one generated recipe and one
generated loot path alongside the texture/model entries.

## Verified

- All three mods build (`./gradlew build`, EXIT 0).
- `companions` datagen output byte-identical to before.
- `planked-chests` datagen recipe/loot semantically identical to the deleted files (bodies
  diffed).
- Dev server boots with 0 mixin errors; `loot spawn plankedchests:blocks/oak_chest` drops an Oak
  Chest; no recipe/loot parse errors.
- `checkstyleTarget` clean for `rkcore`, `planked-chests`, `companions`.
