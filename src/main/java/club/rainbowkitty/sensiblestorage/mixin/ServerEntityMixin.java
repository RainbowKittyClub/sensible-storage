package club.rainbowkitty.sensiblestorage.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

/**
 * Suppresses vanilla's own nametag over a framed item so {@link
 * club.rainbowkitty.sensiblestorage.frame.FrameLabels} can draw its styled replacement.
 *
 * <p>Vanilla draws that nametag whenever the framed item's name is non-null, unstyled and through
 * walls. The client decides it from the copy of the item it was sent, so the only way to stop it is
 * to send a copy with nothing to name.
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @ModifyArg(
            method = {"sendPairingData", "sendDirtyEntityData"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundSetEntityDataPacket;"
                            + "<init>(ILjava/util/List;)V"),
            index = 1,
            require = 2)
    private List<SynchedEntityData.DataValue<?>> sensiblestorage$hideFramedName(
            List<SynchedEntityData.DataValue<?>> values) {
        if (!(this.entity instanceof ItemFrame)) {
            return values;
        }

        List<SynchedEntityData.DataValue<?>> hidden = null;
        for (int i = 0; i < values.size(); i++) {
            SynchedEntityData.DataValue<?> value = values.get(i);
            if (!(value.value() instanceof ItemStack framed) || framed.getCustomName() == null) {
                continue;
            }

            if (hidden == null) {
                hidden = new ArrayList<>(values);
            }
            hidden.set(i, new SynchedEntityData.DataValue<>(value.id(),
                    EntityDataSerializers.ITEM_STACK, anonymous(framed)));
        }

        return hidden == null ? values : hidden;
    }

    // Both components have to go: getCustomName falls back to a written book's own title.
    private static ItemStack anonymous(ItemStack framed) {
        ItemStack copy = framed.copy();
        copy.remove(DataComponents.CUSTOM_NAME);
        copy.remove(DataComponents.WRITTEN_BOOK_CONTENT);
        return copy;
    }
}
