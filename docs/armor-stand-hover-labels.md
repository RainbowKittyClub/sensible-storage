# Armor stand hover labels

Design notes for `club.rainbowkitty.sensiblestorage.armorstands`. Behaviour mirrors the bookshelf
feature: one floating label follows the crosshair, showing whatever is equipped in the
slot under it. Unlike item frames, there is no custom-name gate — any equipped item gets
a label, empty slots show nothing, same as an empty bookshelf slot.

## Reimplementing `getClickedSlot`

`ArmorStand.getClickedSlot(Vec3)` (`ArmorStand.java:221-239`) is `private`, so it cannot
be called and has to be reproduced band-for-band:

```
feet:    [0.1,                        0.1 + (small ? 0.8 : 0.45))   — needs hasItemInSlot(FEET)
chest:   [0.9 + (small ? 0.3 : 0.0),  0.9 + (small ? 1.0 : 0.7))    — needs hasItemInSlot(CHEST)
legs:    [0.4,                        0.4 + (small ? 1.0 : 0.8))    — needs hasItemInSlot(LEGS)
head:    [1.6, +inf)                                                — needs hasItemInSlot(HEAD)
offhand: hasItemInSlot(OFFHAND) && !hasItemInSlot(MAINHAND)
mainhand: default
```

The bands overlap (chest's upper edge on a small stand reaches 1.9, past head's 1.6
floor), so the checks have to run in this exact if/else-if order, not as independent
range tests. `bandSlotAt` in `ArmorStandSlots` follows the vanilla order precisely.

`clickYPosition` in vanilla is `location.y / (getScale() * getAgeScale())` where
`location` already arrived entity-local — the client subtracts the stand's position
before sending the interact packet, and the server passes it through unchanged
(`ServerGamePacketListenerImpl.handleInteract` → `Player.interactOn` →
`Entity.interact`). `ArmorStandSlots` does that subtraction itself from the raycast hit:
`(hit.getLocation().y - stand.getY()) / scale`.

These constants track a private vanilla method with no compatibility guarantee —
re-verify them against the decompile on any future Minecraft bump.

## Why `EntityLabels.target()` needed the hit result

`FrameLabels` never needed more than the entity itself — a frame holds exactly one item.
Slot resolution here needs the precise Y coordinate the crosshair hit, so
`EntityLabels.target()` was widened from taking a bare `Entity` to a full
`EntityHitResult`, matching `BlockLabels.target()`, which already passed its whole
`BlockHitResult` rather than a bare position. Any future entity-label provider gets the
hit location for free.

## `CENTER` billboard, not `FIXED`

`ShelfLabels` uses `FIXED` because it is pinned flush against one static block face — a
billboard there would swing through the shelf at oblique angles. An armor stand, like an
item frame, is walked around from any side with no face to stay flush against, so it
gets the same billboard treatment as `FrameLabels`.

## Offsets are vertical-only

`LabelTarget`'s offset is in the label's own frame, applied after the style's rotation.
Under `CENTER` billboard that frame follows the *viewer's* camera, not the stand's
facing — a lateral offset would mean "the viewer's right" and visibly orbit as a player
circled the stand. Only ever offsetting on `y` (as `FrameLabels` already does) avoids
that; since one label is shown at a time, there is no need to fake left/right hand
separation.

## Small stands are the `isBaby()` path

`ArmorStand.isBaby()` (`:433-434`) just returns `isSmall()` — a baby-mob concept
repurposed for the small-stand flag, which is why `getAgeScale()` (normally scaling a
baby mob's model) also applies to small stands, and has to be folded into the same
scale divisor vanilla uses before comparing against the band constants.
