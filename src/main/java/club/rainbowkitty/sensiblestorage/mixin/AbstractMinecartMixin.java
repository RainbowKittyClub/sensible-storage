package club.rainbowkitty.sensiblestorage.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

import club.rainbowkitty.sensiblestorage.cart.VanillaCartDisplay;
import club.rainbowkitty.sensiblestorage.cart.VanillaCarts;

/** Gives every cart a slot for a drawn vanilla chest, ticked from the cart's own tick. */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin implements VanillaCarts.Carrier {
    // The chest drawn on this cart, if it reaches clients as a vanilla chest minecart.
    @Unique
    private @Nullable VanillaCartDisplay sensiblestorage$display;

    @Override
    public @Nullable VanillaCartDisplay sensiblestorage$display() {
        return sensiblestorage$display;
    }

    @Override
    public void sensiblestorage$setDisplay(@Nullable VanillaCartDisplay display) {
        sensiblestorage$display = display;
    }

    // A cart riding a vehicle ticks through rideTick, which calls this too.
    @Inject(method = "tick", at = @At("TAIL"))
    private void sensiblestorage$tickDisplay(CallbackInfo ci) {
        if (sensiblestorage$display != null) {
            VanillaCarts.cartTicked((AbstractMinecart) (Object) this);
        }
    }
}
