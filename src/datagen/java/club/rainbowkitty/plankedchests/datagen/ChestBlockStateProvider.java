package club.rainbowkitty.plankedchests.datagen;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.wood.WoodType;
import club.rainbowkitty.rkcore.common.datagen.PackJson;

/**
 * Blockstate + particle-only block model for every chest block, both shapes from
 * {@code PackJson}, which carries why a Polymer block needs them. The particle texture is the
 * wood's own planks, so break and landing particles match it (see {@code PlankedChests.HANDSHAKE}
 * for which clients resolve the real block state at all).
 */
public final class ChestBlockStateProvider implements DataProvider {
    private final PackOutput.PathProvider blockStates;
    private final PackOutput.PathProvider models;

    /**
     * Creates a provider for blockstate and model data generation.
     * @param output the pack output providing paths for blockstate and model files
     */
    public ChestBlockStateProvider(FabricPackOutput output) {
        this.blockStates =
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<Path, JsonObject> files = new HashMap<>();
        for (WoodType wood : WoodType.values()) {
            Identifier model = PlankedChests.id("block/" + wood.chestId());
            Identifier planks = Identifier.fromNamespaceAndPath(
                    wood.planksNamespace(), "block/" + wood.id() + "_planks");
            files.put(this.models.json(model), PackJson.particleOnlyModel(planks));
            files.put(this.blockStates.json(PlankedChests.id(wood.chestId())),
                    PackJson.singleVariantBlockState(model));
            files.put(this.blockStates.json(PlankedChests.id(wood.trappedChestId())),
                    PackJson.singleVariantBlockState(model));
        }

        return DataProvider.saveAll(cache, json -> json, path -> path, files);
    }

    @Override
    public String getName() {
        return "Planked Chests Blockstates";
    }
}
