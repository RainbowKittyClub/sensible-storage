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

/**
 * Blockstate + particle-only block model for every chest block. Only a client that also runs this
 * mod ever resolves the real block state (see {@code PlankedChests.HANDSHAKE}); without these files
 * it would render a missing-model cube behind the real chest the {@code BlockEntityRenderer} draws.
 * Each model has only a {@code particle} texture and no {@code elements}, so the block itself draws
 * nothing while break/landing particles still match the wood.
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
            files.put(this.models.json(model), particleModel(planks));
            files.put(this.blockStates.json(PlankedChests.id(wood.chestId())), variants(model));
            files.put(this.blockStates.json(PlankedChests.id(wood.trappedChestId())),
                    variants(model));
        }

        return CompletableFuture.allOf(files.entrySet().stream()
                .map(e -> DataProvider.saveStable(cache, e.getValue(), e.getKey()))
                .toArray(CompletableFuture[]::new));
    }

    // {"variants":{"":{"model":"<model>"}}}
    private static JsonObject variants(Identifier model) {
        JsonObject entry = new JsonObject();
        entry.addProperty("model", model.toString());
        JsonObject variants = new JsonObject();
        variants.add("", entry);
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }

    // {"textures":{"particle":"<planks>"}}
    private static JsonObject particleModel(Identifier planks) {
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", planks.toString());
        JsonObject root = new JsonObject();
        root.add("textures", textures);
        return root;
    }

    @Override
    public String getName() {
        return "Planked Chests Blockstates";
    }
}
