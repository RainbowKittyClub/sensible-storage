package club.rainbowkitty.sensiblestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Makes the server's correction of a client's cursor void any click the client already sent.
 *
 * <p>The correction, {@code ClientboundSetCursorItemPacket}, carries no state id. A click the
 * client predicted before the correction arrived still carries the current state id, so the server
 * finds nothing to resync after it, and the correction then lands on top of the prediction: the
 * cursor shows a copy of an item the click already put down. Since 26.2 handles queued packets
 * once per tick, two clicks within one tick are enough. Bumping the state id with each correction
 * makes that click's id stale, and the server answers a stale click with the whole menu, cursor
 * included.
 *
 * <p>Polymer items can meet this far more often than vanilla ones. The server checks the client's
 * hash of its cursor against the stack as Polymer translates it for that client, so any
 * disagreement between the two sends a correction after every pickup.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow
    public abstract int incrementStateId();

    // Only the server's menus have a synchronizer, so a client never reaches this.
    @Inject(method = "synchronizeCarriedToRemote", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ContainerSynchronizer;sendCarriedChange("
                    + "Lnet/minecraft/world/inventory/AbstractContainerMenu;"
                    + "Lnet/minecraft/world/item/ItemStack;)V", shift = At.Shift.AFTER))
    private void sensiblestorage$voidClicksInFlight(CallbackInfo ci) {
        incrementStateId();
    }
}
