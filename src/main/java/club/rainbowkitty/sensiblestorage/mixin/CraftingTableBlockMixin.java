package club.rainbowkitty.sensiblestorage.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.sensiblestorage.block.CraftingTableGrids;

/**
 * Gives vanilla's crafting table, and every planked one by inheritance, a grid that keeps its
 * items and a comparator signal. {@link CraftingTableGrids} decides each.
 */
@Mixin(CraftingTableBlock.class)
public abstract class CraftingTableBlockMixin extends Block
        implements EntityBlock {
    // Never called: a mixin's constructor exists only to satisfy extending Block.
    private CraftingTableBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CraftingTableGrids.newBlockEntity(pos, state);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return CraftingTableGrids.hasGrid(state);
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos,
            Direction direction) {
        return CraftingTableGrids.signal(level, pos);
    }

    // The grid's own menu, which keeps its contents; vanilla's for a table from another mod.
    @Inject(method = "getMenuProvider", at = @At("HEAD"), cancellable = true)
    private void sensiblestorage$gridMenu(BlockState state, Level level, BlockPos pos,
            CallbackInfoReturnable<MenuProvider> cir) {
        MenuProvider grid = CraftingTableGrids.menuProvider(level, pos);
        if (grid != null) {
            cir.setReturnValue(grid);
        }
    }
}
