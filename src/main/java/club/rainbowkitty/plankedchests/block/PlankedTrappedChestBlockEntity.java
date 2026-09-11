package club.rainbowkitty.plankedchests.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Trapped chest block entity. Extends {@link PlankedChestBlockEntity} rather than the vanilla
 * {@code TrappedChestBlockEntity} — that class fixes the vanilla type — and reproduces its
 * neighbour-update behaviour on open-count change. The open-container title is inherited from
 * {@link PlankedChestBlockEntity#getDefaultName()}, which deliberately uses the plain-chest name.
 */
public class PlankedTrappedChestBlockEntity extends PlankedChestBlockEntity {
    /** @param type this mod's {@code plankedchests:trapped_chest} block-entity type */
    public PlankedTrappedChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // TrappedChestBlockEntity#signalOpenCount, verbatim: powers adjacent redstone while open.
    @Override
    protected void signalOpenCount(Level level, BlockPos pos, BlockState blockState, int previous,
            int current) {
        super.signalOpenCount(level, pos, blockState, previous, current);
        if (previous != current) {
            Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(
                    level, blockState.getValue(ChestBlock.FACING).getOpposite(), Direction.UP);
            Block block = blockState.getBlock();
            level.updateNeighborsAt(pos, block, orientation);
            level.updateNeighborsAt(pos.below(), block, orientation);
        }
    }
}
