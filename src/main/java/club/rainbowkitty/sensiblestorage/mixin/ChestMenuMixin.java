package club.rainbowkitty.sensiblestorage.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

import club.rainbowkitty.sensiblestorage.cart.VanillaCarts;

/** Counts the chest menus open on a vanilla chest minecart, which the cart does not. */
@Mixin(ChestMenu.class)
public abstract class ChestMenuMixin {
    @Shadow
    @Final
    private Container container;

    // The constructor that calls the container's startOpen.
    @Inject(method = "<init>(Lnet/minecraft/world/inventory/MenuType;"
            + "ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;I)V",
            at = @At("TAIL"))
    private void sensiblestorage$countOpen(MenuType<?> menuType, int containerId,
            Inventory inventory, Container container, int rows, CallbackInfo ci) {
        if (!inventory.player.level().isClientSide()) {
            VanillaCarts.openersChanged(container, 1);
        }
    }

    // Where the container's stopOpen is called.
    @Inject(method = "removed", at = @At("TAIL"))
    private void sensiblestorage$countClose(Player player, CallbackInfo ci) {
        if (!player.level().isClientSide()) {
            VanillaCarts.openersChanged(container, -1);
        }
    }
}
