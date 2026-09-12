package club.rainbowkitty.plankedchests.cart;

import com.mojang.math.Axis;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import club.rainbowkitty.plankedchests.display.ShulkerModels;
import club.rainbowkitty.rkcore.common.vehicle.CargoDisplayHolder;

/**
 * The visible shulker box riding a {@link PlankedChestMinecart} — a static base and a lid that
 * rises and turns, posed where vanilla draws a cart's cargo.
 *
 * <p>Drawn by us rather than by vanilla for the same reason the chest is: vanilla's cargo renderer
 * resolves a block state and has nowhere to put an openness, so a shulker box in a cart would sit
 * permanently shut. Everything else about the cargo's placement is {@link ChestCartDisplay}'s
 * problem solved again, and this reads that class's own figures rather than copying them: a shulker
 * box is a taller model than a chest, so both the scale and the ride height depart from vanilla's
 * transform, and each is derived from the chest cart it has to sit level with.
 */
public class ShulkerCartDisplay extends CargoDisplayHolder {
    // The extra turn AbstractMinecartRenderer#submit gives the cargo inside the cart's own rotated
    // frame, which is what leaves the box facing along the cart's body rather than across it.
    private static final float CARGO_TURN = 90.0f;

    // Block-model geometry the placement below is worked out in: the centre a display entity scales
    // and turns about, which is the middle of the block, and the top of a shulker box, which unlike
    // a chest is the top of the block.
    private static final float PIXELS_PER_BLOCK = 16.0f;
    private static final float MODEL_CENTRE = PIXELS_PER_BLOCK / 2.0f;
    private static final float CARGO_MODEL_TOP = PIXELS_PER_BLOCK;

    // A hair under vanilla's 0.75, and the only free number here. A chest's model is 14 wide inside
    // its 16-wide block, so vanilla's figure leaves it clear of the cart; a shulker box fills the
    // block edge to edge, and at 0.75 one side lands exactly on the cart's wall, where which of the
    // two draws is left to float rounding. Too small a trim to read as a size change, and enough to
    // break that tie.
    private static final float CARGO_SCALE = 0.745f;

    // Levelled with a chest cart's cargo rather than left at vanilla's 0.75. A shulker box fills
    // its block's full 16 where a chest model stops at 14, so at vanilla's height it rides two
    // model pixels proud of every chest cart beside it. Derived from the chest cart's own figures
    // instead of dialled in, so that changing either scale keeps the two tops level rather than
    // quietly drifting apart.
    private static final float CARGO_CENTRE_Y = ChestCartDisplay.CARGO_CENTRE_Y
            + topAboveCentre(ChestCartDisplay.CARGO_MODEL_TOP, ChestCartDisplay.CARGO_SCALE)
            - topAboveCentre(CARGO_MODEL_TOP, CARGO_SCALE);

    // Ticks the lid eases over on a tick where the cart itself has not moved, as the chest's does.
    private static final int LID_INTERPOLATION = 2;

    // Vanilla's own lid motion, from ShulkerBoxRenderer$ShulkerBoxModel#setupAnim:
    // lid.setPos(0, 24 - progress * 0.5 * 16, 0) and lid.yRot = 270° * progress.
    private static final float LID_RISE = 0.5f;
    private static final float LID_TURN = 270.0f;

    // ShulkerBoxBlockEntity#updateAnimation ramps progress by this much a tick, and — unlike a
    // chest — applies no easing curve on top, so the value is used raw.
    private static final float LID_STEP = 0.1f;

    private final PlankedChestMinecart cart;
    private final ItemDisplayElement base;
    private final ItemDisplayElement lid;

    // The rotation the last pose was built from, so applyPose can tell a tick where the cart turned
    // from one where only the lid moved. NaN so the first pose counts as a turn and snaps.
    private float posedYaw = Float.NaN;
    private float posedPitch = Float.NaN;

    // Where the cargo's centre sits relative to the passenger point, recomputed each pose because
    // it swings with the cart on a slope. Reused rather than reallocated; setTranslation copies.
    private final Vector3f origin = new Vector3f();

    private float progress;

    /**
     * @param cart the cart this box rides
     * @param boxPath the cargo block's registry path, naming its base and lid models
     */
    public ShulkerCartDisplay(PlankedChestMinecart cart, String boxPath) {
        super(cart);
        this.cart = cart;
        this.base = element(ShulkerModels.baseModel(boxPath));
        this.lid = element(ShulkerModels.lidModel(boxPath));

        addCargoElement(base);
        addCargoElement(lid);

        Vector3f scale = new Vector3f(CARGO_SCALE, CARGO_SCALE, CARGO_SCALE);
        base.setScale(scale);
        lid.setScale(scale);

        // The base never eases, for ChestCartDisplay's reason: a cart's rotation arrives in whole
        // tick steps, and a cargo easing between them slides around inside the cart.
        base.setInterpolationDuration(0);

        // Before the holder is attached, so no client is sent the untransformed box.
        poseNow();
    }

    @Override
    protected void onTick() {
        float next = Mth.approach(progress, cart.isCargoOpen() ? 1.0f : 0.0f, LID_STEP);
        if (next != progress) {
            progress = next;
            // The lid's rise and turn both live in the pose, and the cart may not have moved.
            markPoseDirty();
        }
        super.onTick();
    }

    @Override
    protected void applyPose(float yaw, float pitch) {
        boolean turned = yaw != posedYaw || pitch != posedPitch;
        posedYaw = yaw;
        posedPitch = pitch;
        lid.setInterpolationDuration(turned ? 0 : LID_INTERPOLATION);

        // The cart's own frame, as AbstractMinecartRenderer#submit builds it: turn by the yaw, then
        // roll by -xRot for the slope, with the cargo's own turn composed inside it.
        Quaternionf cartFrame = Axis.YP.rotationDegrees(yaw);
        cartFrame.mul(Axis.ZP.rotationDegrees(-pitch));

        cartFrame.transform(origin.set(0.0f, CARGO_CENTRE_Y, 0.0f))
                .sub(0.0f, PASSENGER_ATTACHMENT_Y, 0.0f);
        base.setTranslation(origin);

        Quaternionf orientation =
                new Quaternionf(cartFrame).mul(Axis.YP.rotationDegrees(CARGO_TURN));
        base.setLeftRotation(orientation);
        base.startInterpolationIfDirty();

        // The lid lifts along the *cart's* up rather than the world's, so on a slope it leaves the
        // box square instead of shearing out of it — the same reason the centre is measured through
        // cartFrame. Scaled with the cargo, since half a block of a 0.75-scale box is 0.375.
        Vector3f lift = cartFrame.transform(
                new Vector3f(0.0f, progress * LID_RISE * CARGO_SCALE, 0.0f));
        lid.setTranslation(new Vector3f(origin).add(lift));

        // A right rotation is the lid's own, inside the box's frame. Y is the one axis the
        // renderer's baked 180° Y turn cannot disturb: conjugating a Y rotation by a Y rotation
        // leaves it alone, so unlike the chest's hinge there is no sign to correct for here.
        lid.setLeftRotation(orientation);
        lid.setRightRotation(Axis.YP.rotationDegrees(LID_TURN * progress));
        lid.startInterpolationIfDirty();
    }

    // How far a block model's top ends up above the point its block centre is placed at, once the
    // cargo transform has scaled it. Both halves of CARGO_CENTRE_Y's levelling are this.
    private static float topAboveCentre(float modelTop, float scale) {
        return (modelTop - MODEL_CENTRE) / PIXELS_PER_BLOCK * scale;
    }

    // Creates a display element for one of the box's two models, base or lid.
    private static ItemDisplayElement element(Identifier model) {
        ItemDisplayElement element = new ItemDisplayElement();
        // Our models have no display block for any context to read, so HEAD renders identically to
        // FIXED/NONE here, as it does for the chest.
        element.setItemDisplayContext(ItemDisplayContext.HEAD);
        element.setInterpolationDuration(2);
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.set(DataComponents.ITEM_MODEL, model);
        element.setItem(stack);
        return element;
    }
}
