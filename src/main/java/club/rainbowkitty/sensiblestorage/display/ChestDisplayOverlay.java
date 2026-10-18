package club.rainbowkitty.sensiblestorage.display;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.AbstractElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.kalic0re.common.display.ItemDisplayPose;
import club.rainbowkitty.sensiblestorage.mixin.DisplayAccessor;

/**
 * The chest drawn over a block display showing one, for every client holding the pack, which draws
 * the block display's own chest invisible. Its two elements ride the block display, so they follow
 * a teleport, its interpolation and any vehicle it rides, and they copy its transformation: a block
 * display draws its block from its corner, an item display centred, so each element's
 * transformation is the block display's, then half a block in, then the placed chest's own pose.
 * The lid stays shut; a block display has no opener.
 */
public final class ChestDisplayOverlay extends ElementHolder {
    private final Display.BlockDisplay display;
    private final BlockState state;
    private final ChestVisual visual;

    /**
     * @param display the block display drawn over
     * @param state the chest state it shows, which fixes this overlay's models and facing
     * @param chestId names the base and lid models; see {@link ChestVisual#ChestVisual}
     */
    public ChestDisplayOverlay(Display.BlockDisplay display, BlockState state, String chestId) {
        this.display = display;
        this.state = state;
        this.visual = new ChestVisual(chestId, hinge -> { });
        for (ItemDisplayElement element : new ItemDisplayElement[] {visual.base(), visual.lid()}) {
            // The client seats a rider itself; the server's own position updates would fight it.
            element.ignorePositionUpdates();
            element.setOffset(Vec3.ZERO);
            addPassengerElement(element);
        }
        visual.applyItems(state.hasProperty(ChestBlock.TYPE)
                ? state.getValue(ChestBlock.TYPE)
                : ChestType.SINGLE);
        copyDisplay();
    }

    /** The chest state this overlay was built for. */
    public BlockState state() {
        return state;
    }

    @Override
    public boolean startWatching(ServerGamePacketListenerImpl player) {
        if (!ChestElementHolder.Audience.PACK.includes(player)) {
            return false;
        }
        // A rider spawns where it was last sent, and the block display may have moved since.
        updatePosition();
        for (VirtualElement element : getElements()) {
            if (element instanceof AbstractElement rider) {
                rider.updateLastSyncedPos();
            }
        }
        return super.startWatching(player);
    }

    /**
     * Copies the block display's transformation, its interpolation and look onto both elements;
     * {@link ChestBlockDisplays} sends the change.
     */
    public void copyDisplay() {
        DisplayAccessor source = (DisplayAccessor) display;
        Matrix4f centred = new Matrix4f(DisplayAccessor.sensiblestorage$transformation(
                display.getEntityData()).getMatrix()).translate(0.5f, 0.5f, 0.5f);
        Quaternionf orientation = ItemDisplayPose.orientation(
                -state.getValue(ChestBlock.FACING).toYRot());
        visual.base().setTransformation(new Matrix4f(centred).rotate(orientation));
        visual.lid().setTransformation(new Matrix4f(centred)
                .translate(ChestVisual.hinge(orientation)).rotate(orientation));
        for (ItemDisplayElement element : new ItemDisplayElement[] {visual.base(), visual.lid()}) {
            element.setInterpolationDuration(source.sensiblestorage$interpolationDuration());
            element.startInterpolationIfDirty();
            element.setBrightness(source.sensiblestorage$brightness());
            element.setBillboardMode(source.sensiblestorage$billboard());
            element.setViewRange(source.sensiblestorage$viewRange());
            element.setYaw(display.getYRot());
            element.setPitch(display.getXRot());
        }
    }
}
