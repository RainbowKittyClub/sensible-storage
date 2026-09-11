package club.rainbowkitty.plankedchests.block;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.plankedchests.wood.WoodType;

/**
 * A wooden trapped chest. Reproduces {@link net.minecraft.world.level.block.TrappedChestBlock}'s
 * redstone behaviour — it cannot subclass it, because that class fixes the vanilla trapped-chest
 * block-entity type in its constructor.
 */
public class PlankedTrappedChestBlock extends PlankedChestBlock {
    /** See {@link PlankedChestBlock#PlankedChestBlock}. */
    public PlankedTrappedChestBlock(WoodType wood,
            Supplier<BlockEntityType<? extends ChestBlockEntity>> blockEntityType,
            BlockBehaviour.Properties properties) {
        super(wood, blockEntityType, properties);
    }

    @Override
    public String registryPath() {
        return wood().trappedChestId();
    }

    @Override
    public String spriteBase() {
        return wood().id() + "_trapped";
    }

    /** Returns a trapped chest block entity carrying this family's registered type. */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlankedTrappedChestBlockEntity(blockEntityType(), pos, state);
    }

    // TrappedChestBlock overrides, verbatim.

    @Override
    protected Stat<Identifier> getOpenChestStat() {
        return Stats.CUSTOM.get(Stats.TRIGGER_TRAPPED_CHEST);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
        return Mth.clamp(ChestBlockEntity.getOpenCount(level, pos), 0, 15);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos,
            Direction direction) {
        return direction == Direction.UP ? state.getSignal(level, pos, direction) : 0;
    }
}
