# Bookshelf hover titles

Design notes for the bookshelf label feature. The label and raycast machinery is
`kalic0re`'s `common/label`, shaded in; the shelf placement and book styling are in
`club.rainbowkitty.sensiblestorage.books`.

## Behaviour

While a player looks at the front face of a chiseled bookshelf, a single floating
text label shows the title of the book in the slot under their crosshair. The label
is visible only to that player, follows the crosshair from slot to slot, and
disappears when they look away. Empty slots show nothing.

Server-side only — the labels are Polymer virtual entities, so vanilla clients need
no install.

## Why one label and not six

Text displays render at **0.025 blocks per font pixel**, line height 10px, i.e.
**0.25 blocks per line** at scale 1 and roughly 0.15 blocks per character
(`DisplayRenderer.java:262`, `:265` in the 26.2 decompile).

A chiseled bookshelf slot is 1/3 block wide. A 20-character title at scale 1 is
~3.0 blocks wide — nine times its cell. Shrinking to fit needs scale ≈ 0.11, which
puts line height at 0.028 blocks: rendered, but unreadable. Six in-place per-slot
labels are geometrically impossible at a legible size, so the crosshair picks one.

## Reading the shelf

Class names use `BookShelf` with a capital S; `ChiseledBookshelfBlock` does not
exist. The registry field is one word, and the block-entity type holder is plural.

| Thing | Name |
| --- | --- |
| Block | `net.minecraft.world.level.block.ChiseledBookShelfBlock` |
| Block entity | `net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity` |
| Block instance | `Blocks.CHISELED_BOOKSHELF` |
| Block entity type | `BlockEntityTypes.CHISELED_BOOKSHELF` (plural class) |

`ChiseledBookShelfBlock implements SelectableSlotContainer`, a 26.2 interface that
already does hit→slot mapping:

```java
default OptionalInt getHitSlot(BlockHitResult hitResult, Direction blockFacing)
```

It returns empty unless `hitResult.getDirection() == blockFacing`, so "player is
looking at the readable face" falls out for free — no separate face check needed.
`getRows()` is 2, `getColumns()` is 3, slot 0 is top-left, row-major:

```
top     0  1  2
bottom  3  4  5
```

`FACING` is `HorizontalDirectionalBlock.FACING` and stores the **outward normal of
the front face**, not the placing player's direction (`getStateForPlacement` applies
`.getOpposite()`). Labels therefore offset along `+FACING`.

Contents come from `ListBackedContainer`: `getItems()` / `getItem(int)`. Occupancy is
also mirrored in the `SLOT_0_OCCUPIED`..`SLOT_5_OCCUPIED` blockstate booleans, but
reading the block entity directly is simpler.

Titles come from `ItemStack.getHoverName()`. Vanilla already routes book titles
through it — `getCustomName()` falls back to `WRITTEN_BOOK_CONTENT.title().raw()` —
so there is no reason to read the data component directly. Per item:

| Item | Heading | Style |
| --- | --- | --- |
| `written_book` | the title, or `CUSTOM_NAME` if set (that wins) | unstyled |
| `enchanted_book` | the plain item name | yellow |
| `writable_book` | `CUSTOM_NAME`, else "Book and Quill" | grey, italic once named |
| `knowledge_book` | the plain item name | magenta |
| `book` | the plain item name | grey |

The lower plate takes `STORED_ENCHANTMENTS` one enchantment per line in yellow italic,
then `LORE`. Lore comes from `ItemLore.styledLines()` rather than `lines()`, so it
arrives with the same dark purple italic base a tooltip would give it, and an author's
own colour still wins because that style is merged as a base rather than applied over.

## Styling

Only two of those rows are keyed off the item. Grey marks a stack whose
`getCustomName()` is null — one with nothing to show but its own item type — so the
label reads as a description rather than a title. Magenta is not a knowledge-book case
either: it is `getRarity().color()`, and knowledge books are `Rarity.EPIC`. Rarity is
the item's own statement about itself, so it outranks a grey that only means "untitled".

Book-and-quill is the exception that is genuinely per-item, because a draft stays dim
whether or not it has been titled. Italic is then added once it *has* a name, marking
that title as the author's working one rather than a published one.

Tints go on an empty parent that the name is appended to, never on the name itself: a
child only inherits a style field it left unset, so a `CUSTOM_NAME` carrying its own
colour keeps it. Legacy `§` codes inside a `written_book` title work for the same
reason — the title is a bare `Component.literal` of the raw string, and
`StringDecomposer.iterateFormatted` applies the codes over the inherited style as the
client lays the line out.

Curses are red. `EnchantmentTags.CURSE` is the source of that, not a list of ids, so a
datapack-added curse is coloured without a code change.

`Enchantment.getFullname` cannot be reused despite doing exactly this, because it
merges the colour into the component with `ComponentUtils.mergeStyles`, which then beats
whatever the label sets. Rebuild the name from `Enchantment.description()` plus
`enchantment.level.<n>`, appending the numeral on the same condition vanilla does
(`level != 1 || getMaxLevel() != 1`).

## Detecting the look

```java
BlockHitResult hit = (BlockHitResult) player.pick(player.blockInteractionRange(), 1.0F, false);
```

`Entity.pick` (`Entity.java:2012`) always returns a `BlockHitResult` on both the hit
and miss paths, so the cast is safe; no player subclass overrides it. Pass `1.0F` for
partial ticks server-side.

`Player.blockInteractionRange()` reads the `BLOCK_INTERACTION_RANGE` attribute (base
4.5) and already includes the creative-mode modifier, so do not hardcode a range.
Mirror the client's `LocalPlayer.filterHitResult` distance check or the server will
register hits the player's own crosshair does not highlight.

Drive it from `ServerTickEvents.END_SERVER_TICK` and iterate
`server.getPlayerList().getPlayers()`. The `END_LEVEL_TICK` variant is the closer
in-workspace precedent (`SpawnEngine.java:44` in critters-mod), but state here is keyed
per player rather than per level, and the player list already contains each player
exactly once — so the server-wide hook avoids reconciling a player who changed dimension
mid-scan. Neither rk-tweaks nor mob-overhaul has a per-tick scan to piggyback on.

Cost is not a concern. `BlockGetter.traverseBlocks` is a 3D-DDA that steps one block
at a time with a single reused `MutableBlockPos` and no allocation in the loop; at
4.5 blocks that is ~5–8 steps of two chunk lookups each. Air short-circuits on
`VoxelShape.isEmpty()`, and `ClipContext.Fluid.NONE` makes the fluid branch free.
Skipping the raycast when a player's position and rotation are unchanged since last
tick removes most of the remaining work for idle players.

## Rendering

One `ElementHolder` per **player**, holding two `TextDisplayElement`s: a heading plate
and a subheading plate under it, so a name reads as a name rather than as the first
line of whatever the item has to say about itself. The gap between them is half a line
height, derived from the scale so it reads the same at any size.

Per-player is the right unit here specifically because the label is hovered-slot
only: two players at the same shelf can hover different slots, so both the text and
the position differ per viewer. A shared per-shelf holder would only work if every
viewer saw identical content.

An item with no lore still has both elements; the lower one is blanked by setting its
text empty **and** its background to 0. `Display.TextDisplay.INITIAL_BACKGROUND` is
`0x40000000`, not transparent, so empty text alone leaves a small box hanging there.
Removing the element instead is not an option worth taking: `GenericEntityElement`'s
`stopWatching` is a no-op, so per-element hiding never despawns anything.

Relevant setters — note `setBackground` takes a packed ARGB int (pass `0` for fully
transparent; `setDefaultBackground(true)` instead forces the client's configured
default), and text drop-shadow is `setShadow(boolean)`, not the `setShadowRadius` /
`setShadowStrength` pair inherited from `DisplayElement`, which is the ground blob.

```java
setText(Component) / setBillboardMode(Display.BillboardConstraints.FIXED)
setBackground(int) / setTextOpacity(byte) / setTextAlignment(...) / setShadow(boolean)
setViewRange(float) / setBrightness(Brightness) / setScale(Vector3fc)
```

**Scale is 0.33.** `setScale` feeds the `Display` transformation, which ships in tracked
data like any other field, so text size is entirely server-controlled and has nothing to
do with the client's nametag scale setting. At a third size roughly 20 characters fit
across the block before the line wraps.

Line height, per-character width and the wrap width are all measured in blocks at scale
1, so all three have to be *derived* from the scale constant rather than written out.
Hard-coding any of them leaves the wrap width and the multi-line centring describing a
label of a different size to the one on screen.

**Orientation is `FIXED`, not billboarded.** A billboard pivots about the element's own
centre, so a label wider than the block swings its ends through the shelf as soon as the
viewer is off-square — clearance in front of the face does not fix it, because the
overlap grows with viewing angle.

Pin the yaw to the shelf instead: `setRotation(0.0F, facing.toYRot())`. That is exactly
the orientation a billboard would choose for a viewer stood square to the face —
`DisplayRenderer.calculateOrientation` renders `FIXED` at `-entityYRot` and `CENTER` at
`-(cameraYRot - 180)`, and a camera in front sits at `facing.getOpposite().toYRot()`,
which reduces to `entityYRot = facing.toYRot()`. The text plane is then parallel to the
face and cannot intersect it at any angle. The trade is that the label foreshortens at
grazing angles, which is harmless because targeting a slot requires facing the front.

**Per-player visibility requires overriding the holder, not just filtering the
element.** Polymer pushes watchers from two places outside our control —
`ChunkDataSenderMixin` on chunk send, and `ChunkAttachment.updateCurrentlyTracking` —
so a holder that is merely never shown still spawns for everyone in the chunk:

```java
new ElementHolder() {
    @Override
    public boolean startWatching(ServerGamePacketListenerImpl net) {
        return net.player == owner && super.startWatching(net);
    }
}
```

`AbstractElement.setVisibilityPredicate` exists too, but it short-circuits on
reference identity, so a mutable predicate never re-diffs — a new instance must be
passed each time the viewer changes. The holder override is the clearer mechanism.

Attach with `ChunkAttachment.of(holder, level, pos)` and call `holder.tick()` from
the scan. The non-ticking variant means `setText` is otherwise never flushed to
clients. (`ofTicking` would also work, since a 4.5-block reach puts the player well
inside simulation distance, but self-driving keeps the update in one place.)

**Placement goes in the transformation, not the entity position.** The client rebuilds
a display's render state in its own `tick()` (`Display.java:173`) after the data packet
arrives, but a move packet takes effect the moment it lands. Setting text and position
together therefore shows the previous target's text at the new target's place for
exactly one tick. `DisplayElement.setTranslation` writes into `syncedData`, so the
placement travels in the same `ClientboundSetEntityDataPacket` as the text and the
client applies the pair in one pass.

That makes offsets **label-space**, since `DisplayRenderer` applies the transformation
after the billboard quaternion (`:85` then `:87`). For a `FIXED` label rotated to
`facing.toYRot()` the local axes work out as world up and `facing` — local `+Z` maps to
`(-sin ψ, 0, cos ψ)`, which is exactly the direction vector of yaw ψ — so the face
clearance is a plain `+Z` push. For a billboard the axes follow the camera, which is
what keeps a frame's label sat over its item on screen from any angle. Translation is
composed before scale (`Transformation.compose`), so it stays in unscaled blocks.

The anchor is `Vec3.atCenterOf(pos)`; the vertical comes from the hovered row, whose
visual centres are `y + 0.75` for the top row and `y + 0.25` for the bottom.

**Wrapping replaces collision avoidance.** `setLineWidth` takes font pixels, so setting
it to one block's worth — `1.0 / (0.025 × scale)` — makes the client wrap every label to
the width of the shelf it labels. Nothing can then reach into a neighbouring block
horizontally, whatever is built around it.

That is worth preferring over probing the surroundings. An earlier version measured the
blocks diagonally adjacent to the front face and slid the label toward whichever side was
open, which works but has to answer questions wrapping never raises: what counts as
solid, what to do when both sides are walled, and how far to slide given the server
cannot measure a string. Wrapping is a constant, no block lookups, and correct by
construction.

A text display is **bottom-anchored, not centred**: the renderer translates the whole
block by `-height` before the negative Y scale flips it (`DisplayRenderer.java:268`), so
lines stack upward from the element's position. Left alone, a wrapped or multi-line label
climbs off the top of the shelf. Subtract `(height - lineHeight) / 2` from the vertical
offset to balance the whole label — both plates and the gap — on the point a single line
would occupy.

That requires predicting the line count the client will arrive at, since the client is
what wraps. Estimate per character — `Font` is client-only — using the default font's 6px
advance, which is its widest common glyph, so the estimate errs toward too few lines
rather than splitting a line that would have fitted.

Lifecycle: create the holder on first hover, reattach when the targeted **shelf**
changes, and only update text plus vertical offset when the hovered **slot** changes
within the same shelf. Keep it alive through a short grace period on look-away so a
crosshair drifting over the shelf edge does not thrash element construction — that is
the one genuinely costly operation, since each element clones a tracked-data array
and allocates a UUID. Tear down on `ServerPlayerEvents.LEAVE` and on
`ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE`.

Contents are re-read while the label is visible, so no block-entity hook is needed.
This matters: `ChiseledBookShelfBlockEntity` overrides `setItem`/`removeItem` to
route through a private `updateState(int)` and never calls `setChanged()`, so the
obvious mixin target is both private and easy to get silently wrong at runtime.

`BlockWithElementHolder.registerOverlay` would give automatic lifecycle and
block-state-change notifications on vanilla blocks without a mixin, but it builds a
holder for every chiseled bookshelf in every loaded chunk plus a full section scan on
chunk load — the wrong trade for something invisible almost all the time.

## Decisions taken

- `fabric.mod.json` declares `polymer-virtual-entity` explicitly. `polymer-bundled` puts it on
  the compile classpath either way, so leaving it undeclared would only have surfaced at load time.
- **No game rule.** Unlike an rk-tweaks tweak, a label draws nothing until a player looks at a
  shelf, so there is no behaviour to switch off and nothing to gate.
- **Book-and-quill keeps the item name**, not first-page text — drawn dim as a draft, italic once
  it has been titled.
