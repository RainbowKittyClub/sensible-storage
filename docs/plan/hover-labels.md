# Hover labels (bookshelves, item frames, armor stands)

Per-player floating labels naming whatever sits under the crosshair: the book in a chiseled
bookshelf slot, the item in an item frame, the gear in an armor stand slot. All three are Polymer
virtual entities, so vanilla clients need no install. The features were built in `kalic0re` and
moved here on 2026-09-13, leaving `kalic0re` a library-only mod.

Verified by building all five workspace mods against `kalic0re` 0.13.0, reading the relocated
package names out of the shipped jar with `unzip`/`javap`, and reproducing `src/main/generated`
byte-for-byte against a pre-change SHA-1 manifest. The in-world behaviour of the labels themselves
is unchanged code carried across intact, so it is re-tested rather than re-derived — the detailed
design notes behind it stay in `../bookshelf-hover-titles.md` and `../armor-stand-hover-labels.md`.

Stage order: S1 (rk-tweaks) is independent and can be built at any time. S2 must land before S3,
because S3 pins the version S2 publishes. S4 depends on S2 only. S5 and S6 close the work; the
feature is complete at S3.

---

## Findings that reshape the work

**Mixins cannot be shared code, so the label machinery and the mixins that feed it had to split.**
`processResources` emits one `*.mixins.json` shared by `jar` and `shadowJar`, while `runServer`
executes un-relocated classes off the compile classpath. A relocated mixin package name can
therefore be correct in the shipped jar or correct in dev, never both; shading it un-relocated
instead reintroduces duplicate class names across consumer jars. So `common/label` moves as shaded
library code while `ArmorStandMixin` and `ServerEntityMixin` are copied into this mod outright —
the same registry-in-`kalic0re`, `@Mixin`-in-consumer shape `common/death` already uses.

**`common/label` is a runtime package, unlike every other one this mod shades.** `common/datagen`
and `common/client` ship unused on a dedicated server, so a broken relocation in them is invisible.
The label classes run on every player tick, and `runServer` never exercises the relocation at all.
That makes the jar-level `unzip`/`javap` check in S6 a real test rather than a formality.

**`ArmorStandMixin` was doing two unrelated jobs.** Its `getClickedSlot` override keeps the label
and the interaction agreeing on which slot a point resolves to — that belongs with the labels. Its
two `ShowArms` injections are a gameplay default with no connection to labels at all, and they
reference nothing but a game rule, so the split costs no shared constants.

## How these stages get tested

Three checks, in ascending cost, because the cheap ones are blind to different things.

`./gradlew build` proves compilation and nothing about mixins: every `@Inject(method = …)` string
can be wrong and the build still passes. Mixin targets surface only as `Mixin apply failed` /
`InvalidInjectionException` on a booted server, so each mod carrying a new or moved mixin gets a
dev-server boot and a log grep.

Neither of those tests the Shadow relocation, since dev mode runs the un-relocated classes off the
compile classpath. Only the shipped jar does, so S6 reads the relocated names out of it directly.

Datagen is a regression check, not a feature check: nothing here touches the providers, so
`src/main/generated` must come out byte-identical to a manifest captured before the work started.

A stage is not finished until its "Done when" is observed.

## S1 — rk-tweaks gains the armor-stand arms default — **done**

**Build.** `rule/ModGameRules` gains
`ARMOR_STANDS_SHOW_ARMS_BY_DEFAULT = registerBoolean("armor_stands_show_arms_by_default", true)`,
flat in the `rktweaks:` namespace like every other rule there. `module/ArmorStandsShowArms` holds
the one `shouldShowArms(Level)` read behind an `instanceof ServerLevel` guard. `mixin/ArmorStandMixin`
carries the two injections lifted from `kalic0re`, using the `rktweaks$` member prefix. Register the
mixin in `mixins.json` (this mod's config is `mixins.json`, not `<mod_id>.mixins.json`) and add the
two `gamerule.rktweaks.armor_stands_show_arms_by_default` lang keys.

**Done when.** A placed stand has arms; `/gamerule rktweaks:armor_stands_show_arms_by_default false`
then a fresh stand has none; a `/summon`ed stand and a stand surviving a world reload both follow
the rule.

The default has to be swapped *inside* `readAdditionalSaveData` rather than only in the constructor:
item placement runs the constructor alone, but `/summon` and world load then run the read, which
overwrites it. `ordinal = 2` is the `ShowArms` read of that method's four `getBooleanOr` calls.

### As built

`mod_version` was **not** bumped. The plan assumed HEAD was 0.5.0; it is 0.3.0, and 0.5.0 is an
uncommitted in-flight bump for unrelated hostile-mob work. 0.5.0 is unpublished, so this tweak
belongs in it rather than claiming 0.6.0 on top.

Not committed, for the same reason: `ModGameRules.java`, `mixins.json`, `en_us.json` and
`gradle.properties` all carry uncommitted changes from that in-flight work, and committing those
paths would sweep it in.

rk-tweaks' Checkstyle gate is red independently of this — 208 violations across 22 files, 159 of
them `CustomImportOrder` from files written with `club.rainbowkitty` first and `java.*` last. The
two new files here are written in house order and add zero violations; the total held at exactly
208. Fixing the rest would mean reformatting nine files that the in-flight work is actively editing.

Verified headlessly on a dev server seeded at port 25605 (rk-tweaks ships no dev-server config, and
the live container holds 25565–25567). `ArmorStandMixin` mixed into `ArmorStand` at boot with both
injections resolved and zero errors. `/summon armor_stand` then `data get … ShowArms` gives `1b`
with the rule on and `0b` with it off, and `{ShowArms:1b}` in the summon still wins with the rule
off — the "a tag that already has the key wins" semantics the `@ModifyArg` is there to preserve.

## S2 — kalic0re sheds the features and publishes 0.13.0 — **done**

**Build.** Delete `books/`, `frame/`, `armorstands/`, `common/mixin/`, `Main.java` and
`kalic0re.mixins.json`. Drop the `entrypoints` block and `mixins` array from `fabric.mod.json`, and
the dangling `entrypoint=` from `gradle.properties`. `depends` stays as it is — the Polymer-facing
shared packages still need polymer-core / polymer-resource-pack / polymer-virtual-entity on the
compile classpath. Rewrite `mod_description` as a library description and bump to **0.13.0**:
packages are being removed, so republishing 0.12.0 in place would leave three consumer pins naming
bytes that no longer match.

`common/rule/ComponentGameRules` takes a caller-supplied `modId` instead of hardcoding
`"kalic0re." + component`. It has no consumers now, which is normal here, but a library helper that
stamps the library's own id onto a consumer's rule is a bug waiting for its first caller.

**Done when.** `unzip -l` on the published jar lists the twelve `common/*` packages and none of
`books`, `frame`, `armorstands`, `common/mixin`, `Main` or `kalic0re.mixins.json`.

### As built

As planned. The published jar carries exactly the twelve `common/` packages, and `fabric.mod.json`
is its only resource. The optional drift fix came along: `build.gradle` and `fabric.mod.json` both
hardcoded Java 25, now read from a new `java_version` property.

## S3 — sensible-storage absorbs the three features — **done**

**Build.** Add `include 'club/rainbowkitty/kalic0re/common/label/**'` to the `kalic0reSharedJar`
filter; the existing `relocate` already covers it. Copy the six feature classes under
`club.rainbowkitty.sensiblestorage.{books,frame,armorstands}`, rewriting only the `package` line —
their `club.rainbowkitty.kalic0re.common.label` imports stay written **un-relocated**, exactly as
`ChestVisual` already imports `common.display.ItemDisplays`, because Shadow rewrites them at package
time. Copy the two mixins in with the `sensiblestorage$` member prefix, register them in
`sensiblestorage.mixins.json`, and call the five `register()` entry points from
`SensibleStorage.onInitialize()`.

**Done when.** `./gradlew build` exits 0 and `./gradlew clean generateAssets` reproduces the
pre-change datagen manifest byte-for-byte.

Two things to get right by hand. `ArmorStandSlots` keeps its warning that its band constants track
vanilla's **private** `ArmorStand.getClickedSlot` and must be re-verified against the decompile on
any Minecraft bump. And `BookText.register()` installs an override into `ItemText`'s single static
slot — after relocation that static lives in this mod's own shaded copy, so it is isolated from any
other consumer that later shades `common/label`.

### As built

As planned. Datagen came out identical across all 1669 files. `ArmorStandMixin` kept only
`getClickedSlot`; its two `ShowArms` injections are S1's.

## S4 — Move the other two consumer pins — **done**

**Build.** `companions` and `artifacts` both pin `kalic0re_version=0.12.0`. Neither shades a moved
package, so this is a pin bump and a rebuild. Move both to 0.13.0 so one coordinate does not resolve
to two different jars across the workspace.

**Done when.** `./gradlew clean build` exits 0 in both, and each shipped jar still carries its
shaded packages.

### As built

artifacts: 36 shaded classes across `death`/`effect`/`enchant`/`item`. companions: 30 across
`armor`/`datagen`/`item`.

## S5 — Documentation — **done**

**Build.** `CLAUDE.md`'s kalic0re row, sensible-storage row, rk-tweaks section and the never-move
list under "Sharing code between mods" — that last sentence is the one this change invalidates.
`shared-datagen-helpers.md` repeats it verbatim. `kalic0re`'s `shared-code-candidates.md` has an
open decision on the `Kalic0re` re-scope that this change answers. `docs/TESTING.md` gains the point
that `common/label` is a runtime package. `scripts/lint/build.gradle` gains `kalic0re` in its `mods`
map.

**Done when.** No doc in the workspace still describes `kalic0re` as owning the three features, and
`grep -rn 'common/mixin\|kalic0re.books\|kalic0re.armorstands'` over the docs returns nothing.

## S6 — Verification — **in progress**

**Build.** Lint, build all five mods, read the relocation out of the shipped jar, boot both dev
servers carrying moved mixins, then the in-world legs.

**Done when.** `lintAll` green for the mods it covers; zero `Mixin apply failed` /
`InvalidInjectionException` on both dev servers; and in-world: each of a chiseled bookshelf's six
slots labels and clears, a named framed item labels with vanilla's own through-walls nametag gone,
an armor stand labels per slot and right-click still opens the slot the label named, and a chest,
a double chest and a chest cart still work since the shared jar changed under them.

`ArmorStand` is now targeted by two mods at once — rk-tweaks on `<init>`/`readAdditionalSaveData`,
this mod on `getClickedSlot`. Disjoint methods, so they should coexist; that is a claim to confirm
in a log rather than assume, and it is the first thing to check if either behaviour goes missing.

### As built

Everything but the in-world round is observed. Lint: `kalic0re`, `sensible-storage`, `artifacts`
and `minecart-tweaks` clean; `critters-mod` (2) and `rk-tweaks` (208) carry pre-existing violations
in files this work never touched. All five mods build. Relocation confirmed in
`sensiblestorage-0.4.0.jar`: ten `shaded/kalic0re/label/*.class`, no un-relocated
`club/rainbowkitty/kalic0re` path, and `javap` on `ArmorStandLabels` and `ShelfLabels` shows the
call sites bound to the relocated names. Datagen reproduced identically across 1669 files.

Both dev servers boot with zero `Mixin apply failed` / `InvalidInjectionException`.
`ArmorStandMixin` and `ComparatorBlockMixin` are confirmed applied on this mod's server, and
`ArmorStandMixin` on rk-tweaks'. The two mods' `ArmorStand` mixins were never loaded into the same
JVM, so their coexistence is still only inferred from the target methods being disjoint.

**`ServerEntityMixin` is unverified.** `ServerEntity` is instantiated only by
`ChunkMap.TrackedEntity`, which needs a player tracking an entity, so no headless session loads the
class and no mixin ever applies to it. Its `@ModifyArg` carries `require = 2` and will throw the
moment a player first joins if either target moved. That check is the in-world round's, not the
build's.

`mod_version` went to **0.4.0**, not 0.3.0 as planned: `sensiblestorage-0.3.0.jar` is already
deployed live (the rename build, confirmed to carry no label classes), so reusing it would put two
different jars behind one filename.

## Open decisions

- **The live deploy is out of scope, and it is a swap, not an add.** Live currently runs
  `rkcore-0.2.0.jar` — the old feature mod, still drawing all three labels — beside
  `sensiblestorage-0.3.0.jar`, which is the rename build and carries none of them. Deploying
  `sensiblestorage-0.4.0.jar` **must delete `rkcore-0.2.0.jar` in the same step**, or both jars draw
  a label over every bookshelf, frame and stand. The id migration is already done (2026-09-13), so
  `scripts/idmigrate` does not need re-running, and `level.dat` holds no `rkcore`/`kalic0re` key, so
  the retired `show_arms_by_default` rule leaves no stale entry and no saved value to preserve.
  Deploying the rk-tweaks side restores the arms default the retired rule used to provide.
- **rk-tweaks' Checkstyle backlog.** 208 pre-existing violations block its pre-commit hook. Settled
  by either a mechanical import-order pass over the 22 files once the in-flight hostile-mob work
  lands, or a decision that the hook may be bypassed there.
