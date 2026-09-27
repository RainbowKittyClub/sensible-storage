package club.rainbowkitty.sensiblestorage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.kalic0re.common.craft.CraftingGridBlockEntity;

/**
 * The grid of vanilla's crafting table and of every planked one, under one block entity type
 * ({@link CraftingTableBlocks#gridType()}). No client is ever sent it.
 */
public class CraftingTableBlockEntity extends CraftingGridBlockEntity {
    /**
     * @param pos the table's position
     * @param state the table's block state
     */
    public CraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(CraftingTableBlocks.gridType(), pos, state);
    }

    @Override
    protected boolean holdsItems() {
        return CraftingTableRules.holdItems(level);
    }

    @Override
    protected boolean hoppersCanAccess() {
        return CraftingTableRules.hoppersCanAccess(level);
    }
}
