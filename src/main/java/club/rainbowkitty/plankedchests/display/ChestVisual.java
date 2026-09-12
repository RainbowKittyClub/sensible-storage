package club.rainbowkitty.plankedchests.display;

import com.mojang.math.Axis;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.ChestType;

import club.rainbowkitty.rkcore.common.display.ItemDisplays;
import club.rainbowkitty.rkcore.common.display.LidTween;

/**
 * The two item-display elements a visible chest is made of — a static base and a lid rotated about
 * its hinge — with the model naming and the hinge geometry they share.
 *
 * <p>Two holders use this: {@link ChestElementHolder} for a placed chest, and the cart-bound one
 * for a chest riding a minecart. They differ in where the yaw comes from, where "is it open" comes
 * from, and how their elements are positioned — a placed chest's elements are moved by the server,
 * a cart's ride as passengers and are positioned by the client, so only the transformation's own
 * translation is ours to set. That last difference is why the hinge is handed back to the holder
 * through {@link HingePlacement} rather than applied here.
 */
public final class ChestVisual {
    /** How a holder puts the lid's rotation pivot where {@link #applyTransforms} says it goes. */
    @FunctionalInterface
    public interface HingePlacement {
        /**
         * @param hinge the hinge's displacement from the chest's centre, already turned to face
         *     the way the chest faces; world axes, and at the model's authored scale
         */
        void place(Vector3f hinge);
    }

    // Hinge position relative to the block's centre: a display entity's Transformation always
    // rotates about the model cube's own centre (block coordinate (8,8,8)), never its authored
    // origin, regardless of ItemDisplayContext — the lid's geometry (ChestElementModelProvider) is
    // authored so ChestModel's (0,9,1) pivot sits exactly there. Y is the plain
    // block-centre-relative form (9/16 - 0.5); Z carries the opposite sign, because every
    // orientation includes ItemDisplayPose.RENDERER_YAW_OFFSET, which rotates this vector along
    // with the visible geometry.
    private static final Vector3f HINGE = new Vector3f(0.0f, 1.0f / 16.0f, 7.0f / 16.0f);

    private final String chestId;
    private final ItemDisplayElement base = ItemDisplays.element();
    private final ItemDisplayElement lid = ItemDisplays.element();
    private final HingePlacement hingePlacement;

    // The lid's own progress, which the server has to run itself; see LidTween. Eased here
    // continuously rather than quantised to a baked model, as vanilla's renderer would.
    private final LidTween tween = new LidTween();

    // Last chest type whose models were pushed, so applyItems only re-sends on a real change.
    private @Nullable ChestType shownType;

    /**
     * @param chestId {@code wood.chestId()} or {@code wood.trappedChestId()}, which names the
     *     per-wood base and lid models
     * @param hingePlacement how this holder positions the lid's pivot
     */
    public ChestVisual(String chestId, HingePlacement hingePlacement) {
        this.chestId = chestId;
        this.hingePlacement = hingePlacement;
    }

    /** The static lower half of the chest. */
    public ItemDisplayElement base() {
        return base;
    }

    /** The lid, rotated about its hinge as the chest opens. */
    public ItemDisplayElement lid() {
        return lid;
    }

    /** Swaps in the models for a chest half, if they are not already showing. */
    public void applyItems(ChestType type) {
        if (type == shownType) {
            return;
        }
        shownType = type;
        base.setItem(ItemDisplays.modelStack(Items.CHEST,
                ChestModels.baseModel(chestId, type)));
        lid.setItem(ItemDisplays.modelStack(Items.CHEST,
                ChestModels.lidModel(chestId, type)));
    }

    /**
     * Advances the lid one tick toward open or closed and returns how far open to draw it.
     *
     * @param open whether anything currently has the chest open
     * @return the eased openness, 0 closed to 1 fully open, for {@link #applyTransforms}
     */
    public float tweenOpenness(boolean open) {
        tween.advance(open);
        return LidTween.easeOut(tween.progress());
    }

    /**
     * Poses both halves. The base's geometry is authored around the model cube's centre already
     * (see {@code HINGE} for why that is where rotation pivots); the lid's hinge is placed by the
     * holder instead. Left and right rotations are split only for readability (facing versus the
     * local open angle) — with a uniform scale, composing them either way is the same rotation.
     *
     * @param orientation the whole chest's rotation, from ItemDisplayPose.orientation and
     *     whatever tilt the holder adds
     * @param easedOpenness the value {@link #tweenOpenness} returned this tick
     */
    public void applyTransforms(Quaternionfc orientation, float easedOpenness) {
        base.setLeftRotation(orientation);
        base.startInterpolationIfDirty();

        hingePlacement.place(orientation.transform(new Vector3f(HINGE)));
        lid.setLeftRotation(orientation);
        // ItemDisplayPose.RENDERER_YAW_OFFSET (see HINGE) is also why this reads as the opposite
        // sign from vanilla's own lid.xRot = -(open * pi/2).
        lid.setRightRotation(Axis.XP.rotationDegrees(easedOpenness * 90.0f));
        lid.startInterpolationIfDirty();
    }
}
