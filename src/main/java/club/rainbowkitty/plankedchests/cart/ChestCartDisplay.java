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

    // The extra turn AbstractMinecartRenderer gives the cargo inside the cart's own frame, which is
    // what leaves the chest facing along the cart's body rather than across it.
    private static final float CARGO_TURN = 90.0f;

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

    // Ticks the lid eases over on a tick where the cart itself has not moved. Matches the placed
    // chest, whose holder eases everything.
    private static final int LID_INTERPOLATION = 2;

    private final PlankedChestMinecart cart;
    private final ChestVisual visual;

    // The rotation the last pose was built from, so applyPose can tell a tick where the cart turned
    // from one where only the lid moved. NaN so the first pose counts as a turn and snaps.
    private float posedYaw = Float.NaN;
    private float posedPitch = Float.NaN;

    // Where the cargo's centre sits relative to the passenger point, recomputed each pose because
    // it swings with the cart on a slope. Reused rather than reallocated; setTranslation copies.
    private final Vector3f origin = new Vector3f();

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
    protected void applyPose(float yaw, float pitch) {
        // The lid and the cart's rotation share one transform, and one interpolation setting with
        // it, so they cannot be smoothed independently — but they can be smoothed at different
        // *times*. A tick where the cart has not turned is a tick where the only thing that moved
        // is the lid, and easing that is free: there is no rotation for it to slide against. A tick
        // where the cart turned snaps, like the base.
        boolean turned = yaw != posedYaw || pitch != posedPitch;
        posedYaw = yaw;
        posedPitch = pitch;
        visual.lid().setInterpolationDuration(turned ? 0 : LID_INTERPOLATION);

        // The cart's own frame, as AbstractMinecartRenderer#submit builds it: turn by the yaw, then
        // roll by -xRot for the slope. The cargo is drawn inside this, so the roll has to stay
        // *between* that turn and the cargo's own — composing it after both, which is what this
        // first did, leaves the roll about an axis 90° from the slope and tips the chest sideways.
        Quaternionf cartFrame = Axis.YP.rotationDegrees(yaw);
        cartFrame.mul(Axis.ZP.rotationDegrees(-pitch));

        // Vanilla's cargo rides inside that frame, so its centre swings with the cart rather than
        // staying vertically above it — on a slope an unturned offset walks the chest out over the
        // end of the cart. The passenger point it is measured from does not swing: an attachment of
        // (0, y, 0) is yaw-invariant, and pitch never reaches it at all.
        cartFrame.transform(origin.set(0.0f, CARGO_CENTRE_Y, 0.0f))
                .sub(0.0f, PASSENGER_ATTACHMENT_Y, 0.0f);
        visual.base().setTranslation(origin);

        visual.applyTransforms(
                new Quaternionf(cartFrame).mul(Axis.YP.rotationDegrees(CARGO_TURN)), openness);
    }

    // The hinge arrives at the model's authored scale, so it has to be scaled down with the rest of
    // the chest before it is added to the cargo's own origin.
    private void placeHinge(Vector3f hinge) {
        visual.lid().setTranslation(hinge.mul(CARGO_SCALE).add(origin));
    }
}
