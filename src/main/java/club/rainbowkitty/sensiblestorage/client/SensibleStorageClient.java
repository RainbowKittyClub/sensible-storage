package club.rainbowkitty.sensiblestorage.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

import club.rainbowkitty.kalic0re.common.client.PolymerChestRenderer;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.ChestBlocks;
import club.rainbowkitty.sensiblestorage.block.PlankedChestBlock;
import club.rainbowkitty.sensiblestorage.display.ChestModels;

/**
 * Client entrypoint. Registers {@code BlockEntityRenderer} for both chest types
 * so clients with the mod installed see real chests instead of Polymer display entities.
 */
@Environment(EnvType.CLIENT)
public final class SensibleStorageClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(ChestBlocks.chestType,
                ctx -> new PolymerChestRenderer<>(ctx, SensibleStorageClient::sprite));
        BlockEntityRenderers.register(ChestBlocks.trappedType,
                ctx -> new PolymerChestRenderer<>(ctx, SensibleStorageClient::sprite));
    }

    // Resolve the sprite identifier for the given chest configuration.
    private static Identifier sprite(ChestBlockEntity blockEntity, ChestType type) {
        PlankedChestBlock chest = (PlankedChestBlock) blockEntity.getBlockState().getBlock();
        return SensibleStorage.id("block/chest/" + ChestModels.sprite(chest.spriteBase(), type));
    }
}
