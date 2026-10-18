package club.rainbowkitty.sensiblestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.sensiblestorage.display.ChestLids;

/** Wakes a chest's display lid whenever its opener count changes or is rechecked. */
@Mixin(ChestBlockEntity.class)
public abstract class ChestBlockEntityMixin {
    // At HEAD of the base method, which every subclass override (trapped, planked) calls first,
    // and which Lootr's own opener counter calls too.
    @Inject(method = "signalOpenCount", at = @At("HEAD"))
    private void sensiblestorage$wakeLid(Level level, BlockPos pos, BlockState blockState,
            int previous, int current, CallbackInfo ci) {
        ChestLids.openCountChanged(level, pos, current);
    }
}
