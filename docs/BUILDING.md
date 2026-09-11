# Building

```
./gradlew build     # jar lands in build/libs/plankedchests-<version>.jar
```

The house build is documented once for every first-party mod in `rkc/STYLE.md` — versions in
`gradle.properties`, `processResources` templating, the benign `Failed to parse fabric.mod.json` and
refmap warnings, linting, datagen, and the dev-server workflow.

- **`./gradlew generateAssets` is required before `build` from a fresh clone.** It runs datagen
  (`runDatagen`), which derives every per-wood texture, model, item definition and the lang file
  into the gitignored `src/main/generated/`. `build` fails with a pointed message if that output is
  missing. Rerun it whenever the wood list, the overlay art, or a datagen provider changes.
  (Recipes, loot tables and the `mineable/axe` tag are static files under `src/main/resources/`, not
  datagen.)
- **Chest overlay art** lives in `src/datagen/resources/plankedchests_overlay/` — six 64×64 PNGs
  (`chest_overlay[_trapped][_left|_right].png`) laid out like the vanilla chest entity textures.
  Edit them there, then `generateAssets`. The datagen source set also vendors the 12 vanilla plank
  textures (`plankedchests_planks/`), since those are client assets unavailable at datagen time.
