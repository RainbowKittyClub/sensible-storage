package club.rainbowkitty.sensiblestorage.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.kalic0re.common.piston.PistonMovable;

/** Lets pistons move a block with a block entity when a {@link PistonMovable} rule allows it. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
    // isPushable's last line, which refuses any block with a block entity. Wrapped rather than
    // overriding the result, so every earlier refusal (obsidian, the world's edge) still stands.
    @WrapOperation(method = "isPushable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;hasBlockEntity()Z"))
    private static boolean sensiblestorage$letMovableThrough(BlockState state,
            Operation<Boolean> original, @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockPos pos) {
        return original.call(state) && !PistonMovable.allows(state, level, pos);
    }
}
