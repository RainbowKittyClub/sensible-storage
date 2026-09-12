package club.rainbowkitty.plankedchests.cart;

import com.mojang.math.Axis;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import club.rainbowkitty.plankedchests.display.ShulkerModels;
import club.rainbowkitty.rkcore.common.display.ItemDisplays;
import club.rainbowkitty.rkcore.common.display.LidTween;
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

    // Vanilla's own lid motion, from ShulkerBoxRenderer$ShulkerBoxModel#setupAnim:
    // lid.setPos(0, 24 - progress * 0.5 * 16, 0) and lid.yRot = 270° * progress.
    private static final float LID_RISE = 0.5f;
    private static final float LID_TURN = 270.0f;

    private final PlankedChestMinecart cart;
    private final ItemDisplayElement base;
    private final ItemDisplayElement lid;



    // ShulkerBoxBlockEntity#updateAnimation applies no easing curve over the ramp, unlike a
    // chest, so this progress is drawn raw.
    private final LidTween lidTween = new LidTween();

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
        if (lidTween.advance(cart.isCargoOpen())) {
            // The lid's rise and turn both live in the pose, and the cart may not have moved.
            markPoseDirty();
        }
        super.onTick();
    }

    @Override
    protected void applyPose(float yaw, float pitch, boolean turned) {
        lid.setInterpolationDuration(turned ? 0 : PART_INTERPOLATION);

        Quaternionf cartFrame = cartFrame(yaw, pitch);
        Vector3f origin = cargoOrigin(cartFrame, CARGO_CENTRE_Y);
        base.setTranslation(origin);

        Quaternionf orientation =
                new Quaternionf(cartFrame).mul(Axis.YP.rotationDegrees(CARGO_TURN));
        base.setLeftRotation(orientation);
        base.startInterpolationIfDirty();

        // The lid lifts along the *cart's* up rather than the world's, so on a slope it leaves the
        // box square instead of shearing out of it — the same reason the centre is measured through
        // cartFrame. Scaled with the cargo, since half a block of a 0.75-scale box is 0.375.
        Vector3f lift = cartFrame.transform(
                new Vector3f(0.0f, lidTween.progress() * LID_RISE * CARGO_SCALE, 0.0f));
        lid.setTranslation(new Vector3f(origin).add(lift));

        // A right rotation is the lid's own, inside the box's frame. Y is the one axis the
        // renderer's baked 180° Y turn cannot disturb: conjugating a Y rotation by a Y rotation
        // leaves it alone, so unlike the chest's hinge there is no sign to correct for here.
        lid.setLeftRotation(orientation);
        lid.setRightRotation(Axis.YP.rotationDegrees(LID_TURN * lidTween.progress()));
        lid.startInterpolationIfDirty();
    }

    // How far a block model's top ends up above the point its block centre is placed at, once the
    // cargo transform has scaled it. Both halves of CARGO_CENTRE_Y's levelling are this.
    private static float topAboveCentre(float modelTop, float scale) {
        return (modelTop - MODEL_CENTRE) / PIXELS_PER_BLOCK * scale;
    }

    // Creates a display element for one of the box's two models, base or lid.
    private static ItemDisplayElement element(Identifier model) {
        ItemDisplayElement element = ItemDisplays.element();
        element.setItem(ItemDisplays.modelStack(Items.SHULKER_BOX, model));
        return element;
    }
}
