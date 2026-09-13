package club.rainbowkitty.sensiblestorage.armorstands;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.kalic0re.common.label.EntityLabels;
import club.rainbowkitty.kalic0re.common.label.HoverScanner;
import club.rainbowkitty.kalic0re.common.label.ItemText;
import club.rainbowkitty.kalic0re.common.label.LabelMetrics;
import club.rainbowkitty.kalic0re.common.label.LabelStyle;
import club.rainbowkitty.kalic0re.common.label.LabelTarget;
import club.rainbowkitty.kalic0re.common.label.LabelText;

/** Names whatever an armor stand has equipped in the slot under a player's crosshair. */
public final class ArmorStandLabels implements EntityLabels {
    // VERTICAL, not CENTER: a stand's pitch never moves, so pinning pitch to it keeps the label
    // upright instead of letting the offset swing toward the camera as the viewer looks up or down.
    private static final LabelStyle STYLE = new LabelStyle(0.4F,
            Display.BillboardConstraints.VERTICAL, true, 2.5, Display.TextDisplay.Align.CENTER,
            true, Brightness.FULL_BRIGHT);

    // Head has no vanilla span to anchor on, so this is measured by eye, same as
    // ShelfLabels.ROW_OFFSET and FrameLabels.GAP.
    private static final double HEAD_MARGIN = 0.2;

    private ArmorStandLabels() {
    }

    /** Starts labelling armor stands. */
    public static void register() {
        HoverScanner.register(new ArmorStandLabels());
    }

    @Override
    public boolean handles(Entity entity) {
        // Markers are invisible utility anchors elsewhere in the pack, never display pieces, and
        // nothing upstream filters them out for us.
        return entity instanceof ArmorStand stand && !stand.isMarker();
    }

    @Override
    public LabelTarget target(ServerLevel level, EntityHitResult hit) {
        ArmorStand stand = (ArmorStand) hit.getEntity();
        boolean small = stand.isSmall();
        double scale = stand.getScale() * stand.getAgeScale();

        // The hit location is world-space; ArmorStandSlots wants it stand-relative and unscaled,
        // same as the pre-subtracted point the client sends when a player interacts.
        Vec3 relative = hit.getLocation().subtract(stand.position());

        // hitSlot, not clickedSlot: a point on no part of the stand at all is a click vanilla hands
        // to the mainhand for want of anywhere better, which is no reason to name a sword.
        EquipmentSlot slot = ArmorStandSlots.hitSlot(stand, relative);
        if (slot == null) {
            return null;
        }

        ItemStack item = stand.getItemBySlot(slot);
        if (item.isEmpty()) {
            return null;
        }

        LabelText text = ItemText.of(item);
        double dy = anchor(slot, small) * scale - LabelMetrics.centring(STYLE, text);
        Vec3 anchorPos = anchorPosition(stand, slot, scale);
        return new LabelTarget(level, anchorPos, 0.0F, text, new Vec3(0.0, dy, 0.0), STYLE);
    }

    // The hand shift has to move the anchor, not the offset: an offset is applied in the label's
    // post-rotation frame, so under a billboard it would orbit as the viewer circles the stand.
    private static Vec3 anchorPosition(ArmorStand stand, EquipmentSlot slot, double scale) {
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
            return stand.position();
        }
        Vec3 direction = ArmorStandSlots.mainHandDirection(stand);
        if (slot == EquipmentSlot.OFFHAND) {
            direction = direction.scale(-1.0);
        }
        return stand.position().add(direction.scale(ArmorStandSlots.ARM_LATERAL_OFFSET * scale));
    }

    // The midpoint of whichever band ArmorStandSlots just resolved, so the label sits centred on
    // the region a click there would target rather than on an arbitrary point.
    private static double anchor(EquipmentSlot slot, boolean small) {
        return switch (slot) {
            case FEET -> ArmorStandSlots.FEET_OFFSET
                    + (small ? ArmorStandSlots.SMALL_FEET_SPAN : ArmorStandSlots.FEET_SPAN) / 2.0;
            case LEGS -> (ArmorStandSlots.LEGS_BOTTOM + ArmorStandSlots.LEGS_OFFSET
                    + (small ? ArmorStandSlots.SMALL_LEGS_SPAN : ArmorStandSlots.LEGS_SPAN)) / 2.0;
            case CHEST -> ArmorStandSlots.CHEST_OFFSET
                    + (small ? ArmorStandSlots.SMALL_CHEST_LEAD : ArmorStandSlots.CHEST_LEAD)
                    + (small ? ArmorStandSlots.SMALL_CHEST_SPAN : ArmorStandSlots.CHEST_SPAN) / 2.0;
            case HEAD -> ArmorStandSlots.HEAD_OFFSET + HEAD_MARGIN;
            case MAINHAND, OFFHAND -> ArmorStandSlots.ARM_MIN_HEIGHT;
            default -> throw new IllegalStateException("Not a wearable slot: " + slot);
        };
    }
}
