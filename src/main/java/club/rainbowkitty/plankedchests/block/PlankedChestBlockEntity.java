package club.rainbowkitty.plankedchests.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.plankedchests.PlankedChests;

/** Chest block entity carrying this mod's shared chest type; names its menu after the wood. */
public class PlankedChestBlockEntity extends ChestBlockEntity {
    /** @param type this mod's {@code plankedchests:chest} block-entity type */
    public PlankedChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected Component getDefaultName() {
        if (getBlockState().getBlock() instanceof PlankedChestBlock chest) {
            return Component.translatable(
                    "block." + PlankedChests.MOD_ID + "." + chest.wood().chestId());
        }
        return super.getDefaultName();
    }
}
