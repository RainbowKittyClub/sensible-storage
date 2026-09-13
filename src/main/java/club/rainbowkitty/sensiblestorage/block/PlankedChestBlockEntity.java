package club.rainbowkitty.sensiblestorage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.sensiblestorage.SensibleStorage;

/** Chest block entity carrying this mod's shared chest type; names its menu after the wood. */
public class PlankedChestBlockEntity extends ChestBlockEntity {
    /**
     * Constructs a chest block entity for this mod.
     *
     * @param type this mod's {@code sensiblestorage:chest} block-entity type
     */
    public PlankedChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected Component getDefaultName() {
        if (getBlockState().getBlock() instanceof PlankedChestBlock chest) {
            return Component.translatable(
                    "block." + SensibleStorage.MOD_ID + "." + chest.wood().chestId());
        }
        return super.getDefaultName();
    }
}
