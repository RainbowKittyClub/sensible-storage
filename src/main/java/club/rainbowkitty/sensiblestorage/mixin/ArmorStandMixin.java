package club.rainbowkitty.sensiblestorage.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.sensiblestorage.armorstands.ArmorStandSlots;

/** Resolves an armor stand's clicked slot through the same bands the hover labels anchor to. */
@Mixin(ArmorStand.class)
public abstract class ArmorStandMixin {

    // Delegates to ArmorStandSlots so this and ArmorStandLabels always agree on which slot a point
    // resolves to - see that class for the band/arm-side thresholds and their derivation.
    @Inject(method = "getClickedSlot", at = @At("HEAD"), cancellable = true)
    private void sensiblestorage$armSlotOverride(
            Vec3 location, CallbackInfoReturnable<EquipmentSlot> cir) {
        ArmorStand self = (ArmorStand) (Object) this;
        cir.setReturnValue(ArmorStandSlots.clickedSlot(self, location));
    }
}
