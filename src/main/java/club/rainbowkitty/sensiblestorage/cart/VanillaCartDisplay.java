package club.rainbowkitty.sensiblestorage.cart;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

import club.rainbowkitty.sensiblestorage.display.ChestElementHolder;

/**
 * The chest of a cart that reaches clients as vanilla's {@code minecraft:chest_minecart}, drawn
 * for every client holding the pack, which draws that cart's own chest invisible
 * ({@link club.rainbowkitty.sensiblestorage.display.VanillaChestPack}). Every other client draws
 * the cart's chest itself and is sent nothing.
 *
 * <p>Polymer never ticks it. {@link VanillaCarts} ticks it from the cart's own tick, and only
 * while the cart moves, its lid moves, or the lagged pose is still catching up with either.
 */
public final class VanillaCartDisplay extends ChestCartDisplay {
    // Ticks a still cart keeps ticking: its pose is read up to MAX_RENDER_LAG ticks back.
    private static final int SETTLE_TICKS = MAX_RENDER_LAG + 1;

    // Menus open on the cart, which vanilla's chest minecart does not count itself.
    private int openers;

    // Ticks since the cart or its lid last moved.
    private int quietTicks;

    // Where the cart was at its last tick, to tell a still cart from a moving one.
    private double lastX = Double.NaN;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;

    /**
     * @param cart the cart this chest rides
     * @param chestId the vanilla chest id naming the base and lid models
     */
    public VanillaCartDisplay(AbstractMinecart cart, String chestId) {
        super(cart, chestId);
    }

    @Override
    public boolean startWatching(ServerGamePacketListenerImpl player) {
        return ChestElementHolder.Audience.PACK.includes(player) && super.startWatching(player);
    }

    @Override
    protected boolean isOpen() {
        return openers > 0;
    }

    // A menu on the cart opened or closed; the lid follows from the next tick.
    void openersChanged(int delta) {
        openers = Math.max(0, openers + delta);
        quietTicks = 0;
    }

    // From the end of the cart's tick: ticks this holder while there is anything to show.
    void cartTicked() {
        AbstractMinecart cart = cart();
        if (cart.getX() != lastX || cart.getY() != lastY || cart.getZ() != lastZ
                || cart.getYRot() != lastYaw || cart.getXRot() != lastPitch) {
            lastX = cart.getX();
            lastY = cart.getY();
            lastZ = cart.getZ();
            lastYaw = cart.getYRot();
            lastPitch = cart.getXRot();
            quietTicks = 0;
        }
        if (quietTicks <= SETTLE_TICKS) {
            quietTicks++;
            tick();
            if (lidMoved()) {
                quietTicks = 0;
            }
        }
    }
}
