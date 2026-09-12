package club.rainbowkitty.plankedchests.display;

import com.mojang.math.Axis;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * The two item-display elements a visible chest is made of — a static base and a lid rotated about
 * its hinge — with the model naming, the lid tween and the pose maths they share.
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

    // ItemDisplayRenderer#submitInner bakes this extra 180° Y turn into every rendered
    // Display$ItemDisplay; orientation() and HINGE's sign each have to account for it, so it gets
    // one name instead of two independent re-derivations.
    private static final float RENDERER_YAW_OFFSET = 180.0f;

    // Hinge position relative to the block's centre: a display entity's Transformation always
    // rotates about the model cube's own centre (block coordinate (8,8,8)), never its authored
    // origin, regardless of ItemDisplayContext — the lid's geometry (ChestElementModelProvider) is
    // authored so ChestModel's (0,9,1) pivot sits exactly there. Y is the plain
    // block-centre-relative form (9/16 - 0.5); Z carries the opposite sign, because every
    // orientation includes RENDERER_YAW_OFFSET, which rotates this vector along with the visible
    // geometry.
    private static final Vector3f HINGE = new Vector3f(0.0f, 1.0f / 16.0f, 7.0f / 16.0f);

    private final String chestId;
    private final ItemDisplayElement base = newElement();
    private final ItemDisplayElement lid = newElement();
    private final HingePlacement hingePlacement;

    // Lid openness, tweened here because the server never ticks ChestBlockEntity's client-only lid
    // controller (getOpenNess reads 0 server-side). Ramps ±0.1/tick toward open/closed exactly as
    // vanilla ChestLidController; tweenOpenness then applies vanilla's ease-out curve
    // (ChestRenderer#submit) continuously, not quantised to a baked model.
    private float openness;

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

    /**
     * The rotation to pose a chest turned {@code yaw} degrees about Y, corrected for the turn the
     * item-display renderer bakes in.
     *
     * @param yaw the chest's facing as a {@link Axis#YP} rotation — which is the opposite sign from
     *     a Minecraft yaw, so a block's facing arrives here as {@code -facing.toYRot()}
     */
    public static Quaternionf orientation(float yaw) {
        return Axis.YP.rotationDegrees(RENDERER_YAW_OFFSET + yaw);
    }

    /** Swaps in the models for a chest half, if they are not already showing. */
    public void applyItems(ChestType type) {
        if (type == shownType) {
            return;
        }
        shownType = type;
        base.setItem(itemFor(ChestModels.baseModel(chestId, type)));
        lid.setItem(itemFor(ChestModels.lidModel(chestId, type)));
    }

    /**
     * Advances the lid one tick toward open or closed and returns how far open to draw it.
     *
     * @param open whether anything currently has the chest open
     * @return the eased openness, 0 closed to 1 fully open, for {@link #applyTransforms}
     */
    public float tweenOpenness(boolean open) {
        openness = Mth.approach(openness, open ? 1.0f : 0.0f, 0.1f);
        float closed = 1.0f - openness;
        return 1.0f - closed * closed * closed;
    }

    /**
     * Poses both halves. The base's geometry is authored around the model cube's centre already
     * (see {@code HINGE} for why that is where rotation pivots); the lid's hinge is placed by the
     * holder instead. Left and right rotations are split only for readability (facing versus the
     * local open angle) — with a uniform scale, composing them either way is the same rotation.
     *
     * @param orientation the whole chest's rotation, from {@link #orientation} and whatever tilt
     *     the holder adds
     * @param easedOpenness the value {@link #tweenOpenness} returned this tick
     */
    public void applyTransforms(Quaternionfc orientation, float easedOpenness) {
        base.setLeftRotation(orientation);
        base.startInterpolationIfDirty();

        hingePlacement.place(orientation.transform(new Vector3f(HINGE)));
        lid.setLeftRotation(orientation);
        // RENDERER_YAW_OFFSET (see orientation, HINGE) is also why this reads as the opposite sign
        // from vanilla's own lid.xRot = -(open * pi/2).
        lid.setRightRotation(Axis.XP.rotationDegrees(easedOpenness * 90.0f));
        lid.startInterpolationIfDirty();
    }

    // Creates a fresh item-display element for one half of a chest.
    private static ItemDisplayElement newElement() {
        ItemDisplayElement element = new ItemDisplayElement();
        // Our elements' models have no display block for any context to read, so HEAD renders
        // identically to FIXED/NONE here.
        element.setItemDisplayContext(ItemDisplayContext.HEAD);
        element.setInterpolationDuration(2);
        return element;
    }

    // Wraps a model identifier in an ItemStack for display on the item-display entity.
    private static ItemStack itemFor(Identifier model) {
        ItemStack stack = new ItemStack(Items.CHEST);
        stack.set(DataComponents.ITEM_MODEL, model);
        return stack;
    }
}
