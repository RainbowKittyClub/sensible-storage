package club.rainbowkitty.sensiblestorage.display;

import java.util.function.Predicate;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.kalic0re.common.display.ItemDisplayPose;
import club.rainbowkitty.kalic0re.common.display.LidTween;
import club.rainbowkitty.sensiblestorage.SensibleStorage;

/**
 * The visible chest for a placed block: a {@link ChestVisual} bound to a chest block, drawn for the
 * clients its {@link Audience} names. A display entity's transform interpolates client-side, so
 * the lid animates continuously rather than stepping between baked models.
 *
 * <p>Polymer never ticks it: {@link ChestLids} does, and only while its lid moves or its block
 * changed, so a chest nobody touches costs nothing per tick.
 *
 * <p>A double chest's halves draw one angle, the larger of both lids' raw openness eased after, as
 * vanilla draws a pair ({@code ChestBlock.opennessCombiner}, {@code ChestRenderer.submit}): a
 * moving half takes its partner along, and a half placed beside an open chest spawns at its angle.
 *
 * <p>Nothing here reads {@code level.getBlockState}/{@code getBlockEntity} until {@link #onTick()}:
 * the holder is constructed from inside {@code LevelChunk}'s constructor during chunk load, and
 * touching the level there deadlocks on the chunk that is still loading.
 */
public class ChestElementHolder extends ElementHolder {
    /** Which clients a holder draws its chest for. */
    public enum Audience {
        /** A planked chest's: every client sent the vanilla stand-in, so without the handshake. */
        STAND_IN(player -> !SensibleStorage.HANDSHAKE.supportsAll(player)),
        /**
         * A vanilla chest's: every client holding this server's pack, which draws vanilla's own
         * chest invisible ({@link VanillaChestPack}), handshake or not.
         */
        PACK(player -> VanillaChestPack.blanked() && PolymerResourcePackUtils.hasMainPack(player));

        private final Predicate<ServerGamePacketListenerImpl> includes;

        Audience(Predicate<ServerGamePacketListenerImpl> includes) {
            this.includes = includes;
        }

        /** Whether this audience includes the player behind {@code connection}. */
        public boolean includes(ServerGamePacketListenerImpl connection) {
            return includes.test(connection);
        }
    }

    private final ServerLevel level;
    private final BlockPos pos;
    private final Audience audience;
    private final ChestVisual visual;

    // Last values pushed to the client, so a tick only re-sends on a real change. Set for real by
    // the constructor's applyTransforms call before anything reads them.
    private float shownYaw;
    private float shownOpenness;

    // The chest's opener count as its block entity last signalled it; none when the chunk loads.
    private int openCount;

    // Whether this tick's step moved the lid, and whether its pose or models changed; both false
    // means ChestLids can stop ticking it.
    private boolean stepped;
    private boolean changed;

    /**
     * Constructs a chest display bound to a block position.
     *
     * @param level the server level containing this chest
     * @param pos the block position of the chest
     * @param chestId names the base and lid models; see {@link ChestVisual#ChestVisual}
     * @param audience the clients this chest is drawn for
     * @param initialState the initial block state
     */
    public ChestElementHolder(ServerLevel level, BlockPos pos, String chestId, Audience audience,
            BlockState initialState) {
        this.level = level;
        this.pos = pos.immutable();
        this.audience = audience;
        this.visual = new ChestVisual(chestId, this::placeHinge);

        addElement(visual.base());
        addElement(visual.lid());

        visual.applyItems(typeOf(initialState));
        applyTransforms(yawFor(initialState), 0.0f);
    }

    // Only the audience gets the display. The pose is caught up first, so a half placed beside an
    // open chest spawns at the pair's angle.
    @Override
    public boolean startWatching(ServerGamePacketListenerImpl player) {
        if (!audience.includes(player)) {
            return false;
        }
        boolean first = getWatchingPlayers().isEmpty();
        catchUpPose();
        boolean started = super.startWatching(player);
        if (started && first) {
            // The spawn carried the pose; a keyframe of it would only restart the interpolation.
            visual.base().getSyncedData().getDirtyEntries();
            visual.lid().getSyncedData().getDirtyEntries();
        }
        return started;
    }

    // A changed block (paired, unpaired, turned) is re-drawn on the next tick.
    @Override
    public void notifyUpdate(HolderAttachment.UpdateType updateType) {
        super.notifyUpdate(updateType);
        if (updateType == BlockBoundAttachment.BLOCK_STATE_UPDATE) {
            ChestLids.wake(this);
        }
    }

    // Takes the chest's new opener count, from ChestLids.
    void openCount(int count) {
        openCount = count;
    }

    // Steps this half's own lid a tick from its opener count; ChestLids steps every half it ticks
    // before posing any, so a pair's two halves read each other's progress from the same tick.
    void step() {
        stepped = boundState() != null && visual.advanceLid(openCount > 0);
    }

    // Whether the last tick left nothing to send.
    boolean settled() {
        return !stepped && !changed;
    }

    // The other half's holder, if this one is bound and paired.
    @Nullable ChestElementHolder pairedHolder() {
        BlockState state = boundState();
        return state == null ? null : partner(state, false);
    }

    @Override
    protected void onTick() {
        changed = false;
        BlockState state = boundState();
        if (state == null) {
            return;
        }
        changed = visual.applyItems(typeOf(state));
        ChestElementHolder partner = partner(state, false);
        float progress = partner == null
                ? visual.lidProgress()
                : Math.max(visual.lidProgress(), partner.visual.lidProgress());
        changed |= pose(yawFor(state), LidTween.easeOut(progress));
    }

    // The bound block's state (kept current on BLOCK_STATE_UPDATE), or null mid-replacement.
    private @Nullable BlockState boundState() {
        return getAttachment() instanceof BlockBoundAttachment bound
                && bound.getBlockState().getBlock() instanceof ChestBlock
                ? bound.getBlockState()
                : null;
    }

    // Poses the lid at the pair's current angle without stepping either half.
    private void catchUpPose() {
        BlockState state = boundState();
        if (state == null) {
            return;
        }
        ChestElementHolder partner = partner(state, true);
        float progress = partner == null
                ? visual.lidProgress()
                : Math.max(visual.lidProgress(), partner.visual.lidProgress());
        pose(yawFor(state), LidTween.easeOut(progress));
    }

    // The other half's holder, paired as the client's DoubleBlockCombiner pairs them; `placing`
    // also takes a partner still reading single, as it does while this half is being placed.
    private @Nullable ChestElementHolder partner(BlockState state, boolean placing) {
        ChestType type = typeOf(state);
        if (type == ChestType.SINGLE) {
            return null;
        }
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(other.getX()),
                SectionPos.blockToSectionCoord(other.getZ()));
        BlockBoundAttachment attachment =
                chunk == null ? null : BlockBoundAttachment.get(chunk, other);
        if (attachment == null || !(attachment.holder() instanceof ChestElementHolder holder)) {
            return null;
        }
        BlockState otherState = attachment.getBlockState();
        if (!otherState.is(state.getBlock())
                || otherState.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)) {
            return null;
        }
        ChestType otherType = typeOf(otherState);
        boolean paired = otherType != ChestType.SINGLE && otherType != type;
        return paired || (placing && otherType == ChestType.SINGLE) ? holder : null;
    }

    // Poses this half at `easedOpenness`, pushing only a real change; whether that changed.
    private boolean pose(float yaw, float easedOpenness) {
        if (yaw == shownYaw && easedOpenness == shownOpenness) {
            return false;
        }
        applyTransforms(yaw, easedOpenness);
        return true;
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

        Quaternionf orientation = ItemDisplayPose.orientation(yaw);
        visual.applyTransforms(orientation, easedOpenness);
    }
}
