package club.rainbowkitty.plankedchests.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.plankedchests.cart.TrappedCartSignal;

/** Lets a comparator read an open trapped chest cart off the rail in front of it. */
@Mixin(ComparatorBlock.class)
public abstract class ComparatorBlockMixin {

    // At RETURN and only when there is something to say, so every reading vanilla already gives
    // survives untouched - including a detector rail's container fullness, which this would
    // otherwise overwrite with a zero for every cart that is not a trapped one.
    @Inject(method = "getInputSignal", at = @At("RETURN"), cancellable = true)
    private void plankedchests$readTrappedCart(Level level, BlockPos pos, BlockState state,
            CallbackInfoReturnable<Integer> cir) {
        int signal = TrappedCartSignal.read(
                level, pos.relative(state.getValue(HorizontalDirectionalBlock.FACING)));
        if (signal > 0) {
            cir.setReturnValue(signal);
        }
    }
}
