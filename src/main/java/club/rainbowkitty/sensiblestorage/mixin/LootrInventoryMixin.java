package club.rainbowkitty.sensiblestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.Container;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.Level;

import club.rainbowkitty.sensiblestorage.cart.VanillaCarts;

/**
 * Counts the menus open on a Lootr cart: a player's menu holds Lootr's per-player inventory, which
 * passes the open and close on to the cart. Skipped without Lootr; never required, so a changed
 * Lootr only stills the lid.
 */
@Pseudo
@Mixin(targets = "noobanidus.mods.lootr.common.data.LootrInventory")
public abstract class LootrInventoryMixin {
    // The block entity or cart this inventory belongs to, on the server.
    @Shadow
    public abstract Container getContainer(Level level);

    @Inject(method = "startOpen", at = @At("HEAD"), require = 0)
    private void sensiblestorage$countOpen(ContainerUser user, CallbackInfo ci) {
        VanillaCarts.openersChanged(getContainer(user.getLivingEntity().level()), 1);
    }

    @Inject(method = "stopOpen", at = @At("HEAD"), require = 0)
    private void sensiblestorage$countClose(ContainerUser user, CallbackInfo ci) {
        VanillaCarts.openersChanged(getContainer(user.getLivingEntity().level()), -1);
    }
}
