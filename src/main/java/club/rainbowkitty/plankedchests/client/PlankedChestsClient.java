package club.rainbowkitty.plankedchests.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.block.ChestBlocks;
import club.rainbowkitty.plankedchests.block.PlankedChestBlock;
import club.rainbowkitty.plankedchests.display.ChestModels;
import club.rainbowkitty.rkcore.common.client.PolymerChestRenderer;

/**
 * Client entrypoint. Registers {@code BlockEntityRenderer} for both chest types
 * so clients with the mod installed see real chests instead of Polymer display entities.
 */
@Environment(EnvType.CLIENT)
public final class PlankedChestsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(ChestBlocks.chestType,
                ctx -> new PolymerChestRenderer<>(ctx, PlankedChestsClient::sprite));
        BlockEntityRenderers.register(ChestBlocks.trappedType,
                ctx -> new PolymerChestRenderer<>(ctx, PlankedChestsClient::sprite));
    }

    // Resolve the sprite identifier for the given chest configuration.
    private static Identifier sprite(ChestBlockEntity blockEntity, ChestType type) {
        PlankedChestBlock chest = (PlankedChestBlock) blockEntity.getBlockState().getBlock();
        return PlankedChests.id("block/chest/" + ChestModels.sprite(chest.spriteBase(), type));
    }
}
