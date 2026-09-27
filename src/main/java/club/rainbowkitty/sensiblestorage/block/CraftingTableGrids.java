package club.rainbowkitty.sensiblestorage.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What every crafting table with a grid does beyond vanilla's: vanilla's own through
 * {@code CraftingTableBlockMixin}, each planked one by inheriting from it. A crafting table from
 * another mod is left alone, its block not being one {@link CraftingTableBlocks#gridType()}
 * accepts.
 */
public final class CraftingTableGrids {
    private CraftingTableGrids() {
    }

    /** Whether this state's block keeps a grid. */
    public static boolean hasGrid(BlockState state) {
        BlockEntityType<CraftingTableBlockEntity> type = CraftingTableBlocks.gridType();
        return type != null && type.isValid(state);
    }

    /** A new grid for a crafting table, or null for a table from another mod. */
    public static @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return hasGrid(state) ? new CraftingTableBlockEntity(pos, state) : null;
    }

    /** The table's grid as its menu provider, or null to leave the table to vanilla's menu. */
    public static @Nullable MenuProvider menuProvider(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CraftingTableBlockEntity grid ? grid : null;
    }
}
