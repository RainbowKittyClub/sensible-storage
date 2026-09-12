package club.rainbowkitty.plankedchests.display;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.block.PlankedChestBlock;

/**
 * The visible chest for a placed block: a {@link ChestVisual} bound to the (client-invisible) chest
 * block. A display entity's transform interpolates client-side, so the lid animates continuously
 * rather than stepping between baked models.
 *
 * <p>Nothing here reads {@code level.getBlockState}/{@code getBlockEntity} until {@link #onTick()}:
 * the holder is constructed from inside {@code LevelChunk}'s constructor during chunk load, and
 * touching the level there deadlocks on the chunk that is still loading.
 */
public class ChestElementHolder extends ElementHolder {
    private final ServerLevel level;
    private final BlockPos pos;
    private final ChestVisual visual;

    // Last values pushed to the client, so onTick only re-sends on a real change. Set for real by
    // the constructor's applyTransforms call before onTick ever reads them, so their defaults here
    // never matter.
    private float shownYaw;
    private float shownOpenness;

    /**
     * Constructs a chest display with rendering elements bound to a block position.
     *
     * @param level the server level containing this chest
     * @param pos the block position of the chest
     * @param block the chest block definition
     * @param initialState the initial block state
     */
    public ChestElementHolder(ServerLevel level, BlockPos pos, PlankedChestBlock block,
            BlockState initialState) {
        this.level = level;
        this.pos = pos.immutable();
        this.visual = new ChestVisual(block.registryPath(), this::placeHinge);

        addElement(visual.base());
        addElement(visual.lid());

        visual.applyItems(typeOf(initialState));
        applyTransforms(yawFor(initialState), 0.0f);
    }

    // A client that runs this mod and registered this wood renders the real chest block itself, so
    // it must not also get the display entity — skip it entirely for those connections. A client
    // missing this wood is sent the barrier state instead, so it still needs the display.
    @Override
    public boolean startWatching(ServerGamePacketListenerImpl player) {
        return !PlankedChests.HANDSHAKE.supportsAll(player) && super.startWatching(player);
    }

    @Override
    protected void onTick() {
        // The bound block's current state (kept current on BLOCK_STATE_UPDATE); bail if the block
        // is gone or mid-replacement.
        if (!(getAttachment() instanceof BlockBoundAttachment bound)
                || !(bound.getBlockState().getBlock() instanceof PlankedChestBlock)) {
            return;
        }
        BlockState state = bound.getBlockState();

        visual.applyItems(typeOf(state));

        float yaw = yawFor(state);
        float easedOpenness = visual.tweenOpenness(ChestBlockEntity.getOpenCount(level, pos) > 0);
        if (yaw != shownYaw || easedOpenness != shownOpenness) {
            applyTransforms(yaw, easedOpenness);
        }
    }

    // A placed chest's elements are positioned by the server, so the lid's pivot is a plain element
    // offset — nothing here is anyone else's passenger.
    private void placeHinge(Vector3f hinge) {
        visual.lid().setOffset(new Vec3(hinge.x, hinge.y, hinge.z));
    }

    // Get the chest type (SINGLE, LEFT, or RIGHT) from a block state.
    private static ChestType typeOf(BlockState state) {
        return state.hasProperty(ChestBlock.TYPE)
                ? state.getValue(ChestBlock.TYPE)
                : ChestType.SINGLE;
    }

    // Converts block facing to the Y-axis yaw (degrees) a display transform expects.
    private static float yawFor(BlockState state) {
        return -state.getValue(ChestBlock.FACING).toYRot();
    }

    // Apply rotation and openness to the visual, tracking sent values for change detection.
    private void applyTransforms(float yaw, float easedOpenness) {
        shownYaw = yaw;
        shownOpenness = easedOpenness;

        Quaternionf orientation = ChestVisual.orientation(yaw);
        visual.applyTransforms(orientation, easedOpenness);
    }
}
