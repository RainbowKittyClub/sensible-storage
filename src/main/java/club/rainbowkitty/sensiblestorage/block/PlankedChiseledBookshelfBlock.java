package club.rainbowkitty.sensiblestorage.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * A wooden chiseled bookshelf. Server-side it is vanilla's in every respect, down to the block
 * entity: vanilla's {@code ChiseledBookShelfBlockEntity}, under vanilla's type, which
 * {@code BookshelfBlocks} widens to accept these blocks. A client that also runs this mod gets the
 * real state; every other client gets vanilla's chiseled bookshelf with the same facing and the
 * same slots filled, so what it shows is never a wrong slot.
 */
public class PlankedChiseledBookshelfBlock extends ChiseledBookShelfBlock
        implements PolymerBlock, PolymerClientDecoded {
    private final WoodType wood;

    /**
     * @param wood the wood this shelf is made of
     */
    public PlankedChiseledBookshelfBlock(WoodType wood, BlockBehaviour.Properties properties) {
        super(properties);
        this.wood = wood;
    }

    /** Returns the wood this shelf is made of. */
    public WoodType wood() {
        return wood;
    }

    /**
     * The real state for a client that also runs this mod <em>and registered this wood</em>;
     * vanilla's chiseled bookshelf, facing and occupied slots copied across, for every other.
     */
    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return SensibleStorage.HANDSHAKE.supportsAll(context)
                ? state : Blocks.CHISELED_BOOKSHELF.withPropertiesOf(state);
    }
}
