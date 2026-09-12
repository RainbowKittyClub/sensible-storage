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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.display.ShulkerModels;
import club.rainbowkitty.rkcore.common.datagen.EntityModelBoxes;
import club.rainbowkitty.rkcore.common.datagen.PackJson;

/**
 * Geometry and item-model definitions for the two-part (base + lid) display-entity shulker box that
 * a cart carries, one pair per colour.
 *
 * <p>Shaped like {@link ChestElementModelProvider} and simpler in the one way that matters. Both
 * transcribe a vanilla entity model into block-model {@code elements} — here
 * {@code ShulkerModel.createShellMesh}, which is exactly two cubes:
 *
 * <ul>
 *   <li>base, 16×8×16, block Y 0–8, {@code texOffs (0,28)}
 *   <li>lid, 16×12×16, block Y 4–16, {@code texOffs (0,0)}
 * </ul>
 *
 * <p>They overlap through the middle four pixels, which is why a closed box reads as a full cube.
 * Each is emitted twice — once as vanilla authored it and once inverted, for the reason
 * {@link EntityModelBoxes#invertedTwin} gives.
 *
 * <p>The lid needs none of the chest's hinge arithmetic. A display entity's transformation always
 * rotates about the model cube's centre, and a shulker lid turns about the box's <em>vertical</em>
 * axis — for which only x and z matter, and both are already centred at 8. So the lid is authored
 * in its plain closed position and {@code ShulkerCartDisplay} turns it where it stands.
 *
 * <p>The sheets are vanilla's own, referenced rather than copied; {@link ShulkerAtlasProvider} is
 * what makes them reachable from an item model.
 */
public final class ShulkerElementModelProvider implements DataProvider {
    // The texture variable every shulker model binds its vanilla sheet to.
    private static final String TEXTURE_KEY = "shulker";

    private final PackOutput.PathProvider models;
    private final PackOutput.PathProvider items;

    /**
     * Constructs a provider for shulker element model and item definition data.
     *
     * @param output the pack output to write model and item definition files to
     */
    public ShulkerElementModelProvider(FabricPackOutput output) {
        this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        this.items = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<Path, JsonObject> files = new HashMap<>();
        addGeometry(files);
        addVariant(files, Blocks.SHULKER_BOX);
        Blocks.DYED_SHULKER_BOX.forEach(box -> addVariant(files, box));
        return DataProvider.saveAll(cache, json -> json, path -> path, files);
    }

    @Override
    public String getName() {
        return "Planked Chests Shulker Element Models";
    }

    /**
     * The shared, colour-independent geometry both parts parent. One pair for all seventeen, since
     * nothing about the shape varies with the dye — only the sheet bound to {@code #shulker} does.
     *
     * <p>Each part is vanilla's own cube and nothing else, plus the inward-facing twin that
     * {@link EntityModelBoxes#invertedTwin} explains: the shulker renderer draws without backface
     * culling and a block model cannot, so the twin is what puts the interior back. Between them
     * they reproduce exactly the surfaces vanilla draws, at exactly vanilla's depth, which is why
     * there is no bespoke UV arithmetic left here.
     */
    private void addGeometry(Map<Path, JsonObject> files) {
        // flippedY: ShulkerModel is authored in the mob convention (PartPose.offset(0,24,0), boxes
        // from -16, Y downward), so these block coordinates are already 24 - y of vanilla's.
        JsonObject base = EntityModelBoxes.box(TEXTURE_KEY, 0, 0, 0, 16, 8, 16, 0, 28, null, true);
        JsonObject lid = EntityModelBoxes.box(TEXTURE_KEY, 0, 4, 0, 16, 12, 16, 0, 0, null, true);

        files.put(this.models.json(geometryId("base")),
                EntityModelBoxes.model(TEXTURE_KEY, base, EntityModelBoxes.invertedTwin(base)));
        files.put(this.models.json(geometryId("lid")),
                EntityModelBoxes.model(TEXTURE_KEY, lid, EntityModelBoxes.invertedTwin(lid)));
    }

    // One colour's thin base/lid children: parent the shared geometry, bind that colour's sheet.
    private void addVariant(Map<Path, JsonObject> files, Block box) {
        String boxPath = Ids.blockPath(box);
        Identifier sheet = ShulkerModels.sheet(boxPath);
        addPart(files, ShulkerModels.baseModel(boxPath), geometryId("base"), sheet);
        addPart(files, ShulkerModels.lidModel(boxPath), geometryId("lid"), sheet);
    }

    // Emits a model variant and its item definition for one shulker part and colour.
    private void addPart(Map<Path, JsonObject> files, Identifier itemDefId, Identifier geometry,
            Identifier sheet) {
        Identifier modelId = PlankedChests.id("block/" + itemDefId.getPath());
        files.put(this.models.json(modelId), PackJson.model(geometry, TEXTURE_KEY, sheet));
        files.put(this.items.json(itemDefId), PackJson.itemDefinition(modelId));
    }

    private static Identifier geometryId(String part) {
        return PlankedChests.id("block/shulker_" + part);
    }
}
