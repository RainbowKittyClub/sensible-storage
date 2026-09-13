package club.rainbowkitty.sensiblestorage.armorstands;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Reimplements the private {@code ArmorStand.getClickedSlot}, extended with a lateral arm check,
 * so {@code ArmorStandLabels} and {@code ArmorStandMixin} never disagree on which slot a point
 * resolves to.
 */
public final class ArmorStandSlots {
    // Mirrors the private band thresholds ArmorStand.getClickedSlot uses to turn a click into a
    // slot, so the labelled/clicked slot always matches the one a real click there would equip.
    // Re-check these against the decompile on any future Minecraft bump.
    static final double FEET_OFFSET = 0.1;
    static final double FEET_SPAN = 0.45;
    static final double SMALL_FEET_SPAN = 0.8;
    static final double CHEST_OFFSET = 0.9;
    static final double CHEST_LEAD = 0.0;
    static final double SMALL_CHEST_LEAD = 0.3;
    static final double CHEST_SPAN = 0.7;
    static final double SMALL_CHEST_SPAN = 1.0;
    static final double LEGS_OFFSET = 0.4;
    static final double LEGS_SPAN = 0.8;
    static final double SMALL_LEGS_SPAN = 1.0;
    static final double HEAD_OFFSET = 1.6;

    // Where the legs actually stop rendering (model y=23px). Vanilla's band starts at LEGS_OFFSET,
    // leaving the lower pants on no band at all and so on the mainhand default.
    static final double LEGS_BOTTOM = 0.0635;

    // The arm cube's own height band: model y 0..12px, flipped and translated by 1.501 the way
    // LivingEntityRenderer does, giving shoulder 1.501 down to wrist 0.751.
    static final double ARM_MIN_HEIGHT = 0.751;
    static final double ARM_MAX_HEIGHT = 1.501;

    // Arm cube's outer edge, model x=7px. The hitbox can't be widened to match: a real click's
    // hit-test runs on the unmodified vanilla client, which never sees this mod's mixins.
    static final double ARM_LATERAL_OFFSET = 0.4375;

    // Half the pick box's half-width (a flat 0.5 wide, getScale() never applies to it), so only the
    // box's outer half counts as the arm; the inner half still falls through to the torso bands.
    private static final double SIDE_THRESHOLD = 0.125;

    private ArmorStandSlots() {
    }

    /**
     * Which equipment slot a world-relative point (stand-position-subtracted, unscaled) sits on,
     * or null where it sits on nothing in particular and only vanilla's fall-back would name one.
     */
    public static EquipmentSlot hitSlot(ArmorStand stand, Vec3 location) {
        double scale = stand.getScale() * stand.getAgeScale();
        double localY = location.y / scale;

        EquipmentSlot armSlot = armSlotAt(stand, location, scale, localY);
        return armSlot != null ? armSlot : bandSlotAt(stand, localY, stand.isSmall());
    }

    /** The same, resolved the way vanilla does when the point is on nothing: a hand takes it. */
    public static EquipmentSlot clickedSlot(ArmorStand stand, Vec3 location) {
        EquipmentSlot slot = hitSlot(stand, location);
        if (slot != null) {
            return slot;
        }

        return !stand.hasItemInSlot(EquipmentSlot.MAINHAND)
                && stand.hasItemInSlot(EquipmentSlot.OFFHAND)
                ? EquipmentSlot.OFFHAND
                : EquipmentSlot.MAINHAND;
    }

    /** The unit vector pointing toward the stand's mainhand (right arm) side, in world space. */
    public static Vec3 mainHandDirection(ArmorStand stand) {
        double yawRad = Math.toRadians(stand.getYRot());
        return new Vec3(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
    }

    // Vanilla's click test is Y-only, so a chestplate's band claims the arms' whole height. Kept
    // strictly inside the arm band: the pick box outreaches the legs, so extending it below the
    // wrist would turn glancing hits on the pants' sides into hands.
    private static EquipmentSlot armSlotAt(ArmorStand stand, Vec3 location, double scale,
            double localY) {
        if (!stand.showArms() || localY < ARM_MIN_HEIGHT || localY > ARM_MAX_HEIGHT) {
            return null;
        }

        // The mainhand arm sits at model x=-5px and the offhand at +5px, so mainHandDirection has
        // to point the way local -x does at yaw 0.
        Vec3 right = mainHandDirection(stand);
        double side = (location.x / scale) * right.x + (location.z / scale) * right.z;
        if (Math.abs(side) < SIDE_THRESHOLD) {
            return null;
        }

        return side > 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    // The bands overlap, so this order has to match vanilla's exactly. Its two closing fall-backs
    // live in clickedSlot instead, so a caller that only wants a real hit can tell the two apart.
    private static EquipmentSlot bandSlotAt(ArmorStand stand, double localY, boolean small) {
        if (localY >= FEET_OFFSET && localY < FEET_OFFSET + (small ? SMALL_FEET_SPAN : FEET_SPAN)
                && stand.hasItemInSlot(EquipmentSlot.FEET)) {
            return EquipmentSlot.FEET;
        }
        if (localY >= CHEST_OFFSET + (small ? SMALL_CHEST_LEAD : CHEST_LEAD)
                && localY < CHEST_OFFSET + (small ? SMALL_CHEST_SPAN : CHEST_SPAN)
                && stand.hasItemInSlot(EquipmentSlot.CHEST)) {
            return EquipmentSlot.CHEST;
        }
        if (localY >= LEGS_BOTTOM && localY < LEGS_OFFSET + (small ? SMALL_LEGS_SPAN : LEGS_SPAN)
                && stand.hasItemInSlot(EquipmentSlot.LEGS)) {
            return EquipmentSlot.LEGS;
        }
        if (localY >= HEAD_OFFSET && stand.hasItemInSlot(EquipmentSlot.HEAD)) {
            return EquipmentSlot.HEAD;
        }
        return null;
    }
}
