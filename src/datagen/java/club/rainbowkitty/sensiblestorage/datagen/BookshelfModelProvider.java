package club.rainbowkitty.sensiblestorage.datagen;

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

import club.rainbowkitty.kalic0re.common.datagen.PackJson;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Block model, blockstate and item definition for every bookshelf — vanilla's own three files for
 * its bookshelf, a {@code minecraft:block/cube_column} with the wood's planks on the ends and its
 * generated side all round.
 */
public final class BookshelfModelProvider implements DataProvider {
    // Vanilla's column parent: one texture for the two ends, one for the four sides.
    private static final Identifier CUBE_COLUMN =
            Identifier.withDefaultNamespace("block/cube_column");

    private final PackOutput.PathProvider blockStates;
    private final PackOutput.PathProvider models;
    private final PackOutput.PathProvider items;

    /**
     * Creates a provider for bookshelf blockstates, models and item definitions.
     *
     * @param output the pack output providing paths for all three
     */
    public BookshelfModelProvider(FabricPackOutput output) {
        this.blockStates =
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        this.items = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<Path, JsonObject> files = new HashMap<>();
        for (WoodType wood : BookshelfBlocks.bookshelves().keySet()) {
            Identifier id = SensibleStorage.id(wood.bookshelfId());
            Identifier model = SensibleStorage.id("block/" + wood.bookshelfId());
            files.put(this.models.json(model), PackJson.model(CUBE_COLUMN, Map.of(
                    "end", PlankSpriteProvider.sprite(wood),
                    "side", BookshelfTextureProvider.sprite(wood))));
            files.put(this.blockStates.json(id), PackJson.singleVariantBlockState(model));
            files.put(this.items.json(id), PackJson.itemDefinition(model));
        }
        return DataProvider.saveAll(cache, json -> json, path -> path, files);
    }

    @Override
    public String getName() {
        return "Sensible Storage Bookshelf Models";
    }
}
