package club.rainbowkitty.sensiblestorage.datagen;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import club.rainbowkitty.kalic0re.common.datagen.PackJson;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Block model, blockstate and item definition for every crafting table — vanilla's own three
 * files for its crafting table, pointed at one wood's sprites from
 * {@link CraftingTableTextureProvider}. A table has real geometry, so unlike a chest it needs no
 * particle-only model: a client resolving the real block state draws it from this model directly.
 */
public final class CraftingTableModelProvider implements DataProvider {
    // Vanilla's full-cube parent, which samples one texture key per face.
    private static final Identifier CUBE = Identifier.withDefaultNamespace("block/cube");

    private final PackOutput.PathProvider blockStates;
    private final PackOutput.PathProvider models;
    private final PackOutput.PathProvider items;

    /**
     * Creates a provider for crafting table blockstates, models and item definitions.
     *
     * @param output the pack output providing paths for all three
     */
    public CraftingTableModelProvider(FabricPackOutput output) {
        this.blockStates =
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        this.items = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<Path, JsonObject> files = new HashMap<>();
        for (WoodType wood : CraftingTableBlocks.tables().keySet()) {
            Identifier id = SensibleStorage.id(wood.craftingTableId());
            Identifier model = SensibleStorage.id("block/" + wood.craftingTableId());
            files.put(this.models.json(model), PackJson.model(CUBE, faces(wood)));
            files.put(this.blockStates.json(id), PackJson.singleVariantBlockState(model));
            files.put(this.items.json(id), PackJson.itemDefinition(model));
        }
        return DataProvider.saveAll(cache, json -> json, path -> path, files);
    }

    @Override
    public String getName() {
        return "Sensible Storage Crafting Table Models";
    }

    // Vanilla's own key layout for block/crafting_table: the front on north and west, the side on
    // east and south, the front again for particles.
    private static Map<String, Identifier> faces(WoodType wood) {
        Identifier front = sprite(wood, "front");
        Identifier side = sprite(wood, "side");
        Map<String, Identifier> faces = new LinkedHashMap<>();
        faces.put("down", sprite(wood, CraftingTableTextureProvider.BOTTOM));
        faces.put("east", side);
        faces.put("north", front);
        faces.put("particle", front);
        faces.put("south", side);
        faces.put("up", sprite(wood, "top"));
        faces.put("west", front);
        return faces;
    }

    // One wood's generated sprite for a face.
    private static Identifier sprite(WoodType wood, String face) {
        return SensibleStorage.id(CraftingTableTextureProvider.sprite(wood, face));
    }
}
