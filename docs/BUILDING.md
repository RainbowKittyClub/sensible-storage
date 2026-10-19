# Building

```
./gradlew build     # versions/<v>/build/libs/sensiblestorage-<version>+<v>.jar, one per version
```

The house build is documented once for every first-party mod in the workspace's `docs/STYLE.md` —
versions in `gradle.properties`, `processResources` templating, the benign `Failed to parse
fabric.mod.json` and refmap warnings, linting, datagen, and the dev-server workflow.

- **Multi-version build** (Stonecutter): 26.2 and 26.3, with 26.2 committed. Each version's keys
  are in `versions/<v>/gradle.properties`.
  - 26.3 builds against Polymer 0.18.4 and kalic0re `0.31.0+26.3` from `~/.m2`; 26.2 stays on
    kalic0re 0.30.1.
  - 26.3 needs Fabric Loader 0.19.5: Polymer 0.18.4's client mixins crash the MixinExtras that
    0.19.3 bundles, which the datagen client run hits.
  - Dev servers are on ports 25604/25581 (26.2) and 25808/25810 (26.3), each with its own
    `versions/<v>/run/`.
  - A file in `versions/<v>/src/main/resources/` replaces the shared one at the same path, for
    data whose format differs by version (26.3's `read_power_of_chiseled_bookshelf` advancement).
- **`./gradlew generateAssets` is required before `build` from a fresh clone.** It runs datagen
  (`runDatagen`) for each version, which derives every per-wood texture, model, item definition,
  recipe, loot table and the lang file into the gitignored `versions/<v>/src/main/generated/`.
  `build` fails with a pointed message if that output is missing. Rerun it whenever the wood list,
  the overlay art, or a datagen provider changes. Run it and `build` as two invocations.
  - Output is per version because 26.3 writes loot tables and recipe advancements in a new
    format.
  - A root `src/main/generated/` left by the single-version build is ignored.
- **Chest overlay art** lives in `src/datagen/resources/sensiblestorage_overlay/` — six 64×64 PNGs
  (`chest_overlay[_trapped][_left|_right].png`) laid out like the vanilla chest entity textures.
  Edit them there, then `generateAssets`. The datagen source set also vendors the 12 vanilla plank
  textures (`sensiblestorage_planks/`), since those are client assets unavailable at datagen time.
