package club.rainbowkitty.sensiblestorage.block;

import java.util.Map;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.kalic0re.common.mining.VanillaCreativeBreak;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.display.ChiseledBooksHolder;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * A wooden chiseled bookshelf. Server-side it is vanilla's in every respect, down to the block
 * entity: vanilla's {@code ChiseledBookShelfBlockEntity}, under vanilla's type, which
 * {@code BookshelfBlocks} widens to accept these blocks.
 *
 * <p>Three tiers of client. One that also runs this mod gets the real state. One with the resource
 * pack gets a set-aside state per facing, drawn as the wood's frame with every slot empty, and a
 * {@link ChiseledBooksHolder} display drawing the occupied slots over it — the frame cannot carry
 * them, there being far too few set-aside states for every facing and fill. Every other client, and
 * a pack client when Polymer had too few states for this wood, gets vanilla's chiseled bookshelf
 * with the same facing and slots filled, so what it shows is never a wrong slot.
 */
public class PlankedChiseledBookshelfBlock extends ChiseledBookShelfBlock
        implements PolymerTexturedBlock, BlockWithElementHolder, PolymerClientDecoded,
        VanillaCreativeBreak {
    private final WoodType wood;
    private final @Nullable Map<Direction, BlockState> packStates;

    /**
     * @param wood the wood this shelf is made of
     * @param packStates the state a client with this server's resource pack is sent, per facing,
     *     from {@code PolymerBlockResourceUtils.requestBlock}; null if Polymer could not give all
     *     four, in which case such a client is treated like one without the pack
     */
    public PlankedChiseledBookshelfBlock(WoodType wood,
            @Nullable Map<Direction, BlockState> packStates,
            BlockBehaviour.Properties properties) {
        super(properties);
        this.wood = wood;
        this.packStates = packStates;
    }

    /** Returns the wood this shelf is made of. */
    public WoodType wood() {
        return wood;
    }

    /**
     * The real state for a client that also runs this mod <em>and registered this wood</em>; the
     * facing's set-aside state for one with the resource pack; vanilla's chiseled bookshelf, facing
     * and occupied slots copied across, for every other.
     */
    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        if (SensibleStorage.HANDSHAKE.supportsAll(context)) {
            return state;
        }
        if (packStates != null && context != null
                && PolymerResourcePackUtils.hasMainPack(context)) {
            return packStates.get(state.getValue(FACING));
        }
        return Blocks.CHISELED_BOOKSHELF.withPropertiesOf(state);
    }

    /** The books drawn over the set-aside frame, when this wood has one; none otherwise. */
    @Override
    public @Nullable ElementHolder createElementHolder(ServerLevel level, BlockPos pos,
            BlockState initialBlockState) {
        return packStates != null ? new ChiseledBooksHolder(pos, wood, initialBlockState) : null;
    }
}
