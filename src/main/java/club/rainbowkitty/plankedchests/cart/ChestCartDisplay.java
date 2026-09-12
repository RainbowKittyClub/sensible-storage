package club.rainbowkitty.plankedchests.cart;

import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.world.level.block.state.properties.ChestType;

import club.rainbowkitty.plankedchests.display.ChestVisual;
import club.rainbowkitty.rkcore.common.vehicle.CargoDisplayHolder;

/**
 * The visible chest riding a {@link PlankedChestMinecart} — the same two elements a placed chest
 * uses, mounted as cart passengers and posed where vanilla draws a cart's cargo.
 *
 * <p>Unlike the placed chest's holder this one is sent to <em>every</em> connection, including a
 * client running this mod. That client renders a placed planked chest for real, but it still cannot
 * render one inside a cart: {@code BuiltInBlockModels} maps blocks to cart-cargo models from a
 * table built at bootstrap, and nothing in this mod is in it.
 */
public class ChestCartDisplay extends CargoDisplayHolder {
    // Vanilla's cargo transform, from AbstractMinecartRenderer#submit: the cargo is scaled to 0.75
    // and turned a further 90° about Y inside the cart's own rotated frame.
    static final float CARGO_SCALE = 0.75f;

    // Where that transform leaves the cargo block's centre, as a height above the cart's position:
    // the renderer lifts its frame 0.375 (newRender) and then the cargo chain — scale 0.75,
    // translate (-0.5, (displayOffset - 8)/16, 0.5), rotate Y 90°, with a chest cart's
    // displayOffset of 8 — puts the block's centre another 0.375 above that.
    static final float CARGO_CENTRE_Y = 0.75f;

    // The top of a chest model in block pixels: its lid stops at 14 rather than filling the block
    // (ChestElementModelProvider's single chest is a 0-10 bottom under a 9-14 lid).
    //
    // This and the two above are package-private rather than private because ShulkerCartDisplay
    // levels its own taller cargo against them, and reading them beats copying them.
    static final float CARGO_MODEL_TOP = 14.0f;

    private final PlankedChestMinecart cart;
    private final ChestVisual visual;

    private float openness;

    /**
     * @param cart the cart this chest rides
     * @param chestId {@code wood.chestId()}, naming the per-wood base and lid models
     */
    public ChestCartDisplay(PlankedChestMinecart cart, String chestId) {
        super(cart);
        this.cart = cart;
        this.visual = new ChestVisual(chestId, this::placeHinge);

        addCargoElement(visual.base());
        addCargoElement(visual.lid());

        // A cart chest is never half of a double chest, so its models never change after this.
        visual.applyItems(ChestType.SINGLE);
        Vector3f scale = new Vector3f(CARGO_SCALE, CARGO_SCALE, CARGO_SCALE);
        visual.base().setScale(scale);
        visual.lid().setScale(scale);

        // The base never eases. A cart's rotation arrives at the client in whole-tick steps, and a
        // cargo easing between those steps slides around inside the cart instead of sitting in it.
        // The lid's duration is decided per tick in applyPose instead.
        visual.base().setInterpolationDuration(0);

        // Before the holder is attached, so no client is ever sent the untransformed chest — an
        // element is spawned with no transform at all, which for this one is unscaled and sunk into
        // the cart's floor.
        poseNow();
    }

    @Override
    protected void onTick() {
        float easedOpenness = visual.tweenOpenness(cart.isCargoOpen());
        if (easedOpenness != openness) {
            openness = easedOpenness;
            // The lid angle lives in the pose, and the cart may not have turned this tick.
            markPoseDirty();
        }
        super.onTick();
    }

    @Override
    protected void applyPose(float yaw, float pitch, boolean turned) {
        visual.lid().setInterpolationDuration(turned ? 0 : PART_INTERPOLATION);

        Quaternionf cartFrame = cartFrame(yaw, pitch);
        visual.base().setTranslation(cargoOrigin(cartFrame, CARGO_CENTRE_Y));

        visual.applyTransforms(
                new Quaternionf(cartFrame).mul(Axis.YP.rotationDegrees(CARGO_TURN)), openness);
    }

    // The hinge arrives at the model's authored scale, so it has to be scaled down with the rest of
    // the chest before it is added to the cargo's own origin.
    private void placeHinge(Vector3f hinge) {
        visual.lid().setTranslation(hinge.mul(CARGO_SCALE).add(cargoOrigin()));
    }
}
