package club.rainbowkitty.sensiblestorage.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;

import club.rainbowkitty.sensiblestorage.display.ChestBlockDisplays;
import club.rainbowkitty.sensiblestorage.display.ChestDisplayOverlay;

/** Gives every block display a slot for a drawn chest, refreshed when its data changes. */
@Mixin(Display.BlockDisplay.class)
public abstract class BlockDisplayMixin implements ChestBlockDisplays.Carrier {
    // The chest drawn over this block display, if it shows a chest.
    @Unique
    private @Nullable ChestDisplayOverlay sensiblestorage$overlay;

    @Override
    public @Nullable ChestDisplayOverlay sensiblestorage$overlay() {
        return sensiblestorage$overlay;
    }

    @Override
    public void sensiblestorage$setOverlay(@Nullable ChestDisplayOverlay overlay) {
        sensiblestorage$overlay = overlay;
    }

    // Its block, transformation, interpolation or look changed: redraw at the end of the tick.
    @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
    private void sensiblestorage$dataChanged(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        ChestBlockDisplays.dataChanged((Display.BlockDisplay) (Object) this);
    }
}
