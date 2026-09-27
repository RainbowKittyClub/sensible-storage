package club.rainbowkitty.sensiblestorage.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;

import club.rainbowkitty.kalic0re.common.craft.CraftingGridHandoff;

/** Builds a persistent crafting menu's slots on its table's grid rather than a fresh one. */
@Mixin(AbstractCraftingMenu.class)
public abstract class AbstractCraftingMenuMixin {
    @Shadow
    @Final
    @Mutable
    protected CraftingContainer craftSlots;

    // At the tail, which runs before CraftingMenu's own constructor builds any slot on the grid.
    @Inject(method = "<init>", at = @At("TAIL"))
    private void sensiblestorage$takeSharedGrid(MenuType<?> menuType, int containerId, int width,
            int height, CallbackInfo ci) {
        CraftingContainer shared = CraftingGridHandoff.take();
        if (shared != null) {
            craftSlots = shared;
        }
    }
}
