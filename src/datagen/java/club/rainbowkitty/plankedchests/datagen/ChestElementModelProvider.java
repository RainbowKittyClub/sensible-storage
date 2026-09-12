package club.rainbowkitty.plankedchests.datagen;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.ChestType;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.display.ChestModels;
import club.rainbowkitty.plankedchests.wood.WoodType;

/**
 * Geometry and item-model definitions for the two-part (base + lid) display-entity chest. Base and
 * lid are plain {@code elements} models — vanilla's own chest cuboids, transcribed from
 * {@code ChestModel.createSingleBodyLayer}/{@code createDoubleBody*Layer} and box-UV-unwrapped by
 * hand — shared across every wood as a parent, with a thin per-wood child that only binds the
 * {@code #chest} texture variable to that wood's sprite. {@code ChestElementHolder} rotates the lid
 * about its hinge at runtime; nothing here bakes an openness value.
 */
public final class ChestElementModelProvider implements DataProvider {
    // The texture variable every chest model binds its wood's sheet to. The unwrap itself lives in
    // ElementModels, shared with the shulker boxes, which read the same kind of 64x64 entity sheet.
    private static final String TEXTURE_KEY = "chest";

    private final PackOutput.PathProvider models;
    private final PackOutput.PathProvider items;

    public ChestElementModelProvider(FabricPackOutput output) {
        this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        this.items = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<Path, JsonObject> files = new HashMap<>();
        for (ChestType type : ChestType.values()) {
            addGeometry(files, type);
        }
        addWholeGeometry(files);
        for (WoodType wood : WoodType.values()) {
            addVariant(files, wood.chestId(), wood.id());
            addVariant(files, wood.trappedChestId(), wood.id() + "_trapped");
        }
        addCargoVariant(files, ChestModels.VANILLA_CHEST_ID, ChestModels.VANILLA);
        // Four rather than eight: a waxed copper chest draws as its unwaxed twin.
        ChestModels.COPPER.weathering().forEach(
                variant -> addCargoVariant(files, ChestModels.chestId(variant), variant));
        return CompletableFuture.allOf(files.entrySet().stream()
                .map(e -> DataProvider.saveStable(cache, e.getValue(), e.getKey()))
                .toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Planked Chests Element Models";
    }

    // The shared, wood-independent base + lid geometry for one ChestType, in the animated display
    // entities' hinge-relative form (ChestElementHolder rotates the lid about the hinge). Every
    // ChestType gets one, unconditionally — unlike addWholeGeometry, which is a different consumer
    // entirely (the static item icon) and SINGLE-only.
    private void addGeometry(Map<Path, JsonObject> files, ChestType type) {
        Direction hidden = switch (type) {
            case LEFT -> Direction.WEST;
            case RIGHT -> Direction.EAST;
            case SINGLE -> null;
        };
        float width = type == ChestType.SINGLE ? 14 : 15;
        float bottomX = type == ChestType.LEFT ? 0 : 1;
        float lockX = switch (type) {
            case RIGHT -> 15;
            case LEFT -> 0;
            case SINGLE -> 7;
        };
        float lockWidth = type == ChestType.SINGLE ? 2 : 1;

        JsonObject bottom = box(bottomX, 0, 1, width, 10, 14, 0, 19, hidden);
        // The lid is authored so ChestModel's (0,9,1) pivot sits at model coordinate (8,8,8) — the
        // display entity's rotation always pivots at the model cube's centre (verified against
        // Display/DisplayRenderer/Transformation source), regardless of ItemDisplayContext. Offset
        // by (0,+8,+8) from the hinge-at-origin form.
        JsonObject lid = box(bottomX, 8, 8, width, 5, 14, 0, 0, hidden);
        JsonObject lock = box(lockX, 6, 22, lockWidth, 4, 1, 0, 0, hidden);

        files.put(this.models.json(geometryId("base", type)), model(bottom));
        files.put(this.models.json(geometryId("lid", type)), model(lid, lock));
    }

    // The static, closed-position combined model for the inventory/held item icon — a different
    // consumer than addGeometry's animated display-entity parts (the plain vanilla item pipeline,
    // via addVariant/ChestModels#wholeModel), and SINGLE-only: an item is always a whole chest,
    // never a half.
    private void addWholeGeometry(Map<Path, JsonObject> files) {
        // Vanilla ChestModel's own south-facing latch, matching the placed block. The earlier
        // per-axis rotation hunt was chasing the wrong lever: block/block's gui (Y=225) and
        // firstperson_righthand (Y=45) are only 180° apart, but showing one face on the same
        // visual side in both needs 270° — a mismatch real for any *asymmetric* model, which is
        // exactly why vanilla's own item/template_chest (below) overrides both to 45/315 instead
        // of moving the latch.
        JsonObject bottom = box(1, 0, 1, 14, 10, 14, 0, 19, null);
        JsonObject lid = box(1, 9, 1, 14, 5, 14, 0, 0, null);
        JsonObject lock = box(7, 7, 15, 2, 4, 1, 0, 0, null);
        JsonObject whole = model(bottom, lid, lock);
        whole.addProperty("parent", "minecraft:item/template_chest");
        files.put(this.models.json(geometryId("whole", ChestType.SINGLE)), whole);
    }

    private static Identifier geometryId(String part, ChestType type) {
        return PlankedChests.id("block/chest_" + part + "_" + type.getSerializedName());
    }

    // A variant that only ever rides in a minecart: the SINGLE base and lid, and nothing else. It
    // is never placed, so it is never half of a double chest, and it has no item of its own to want
    // a whole-chest icon.
    private void addCargoVariant(Map<Path, JsonObject> files, String chestId, String variantBase) {
        addPart(files, ChestModels.baseModel(chestId, ChestType.SINGLE),
                geometryId("base", ChestType.SINGLE), variantBase, ChestType.SINGLE);
        addPart(files, ChestModels.lidModel(chestId, ChestType.SINGLE),
                geometryId("lid", ChestType.SINGLE), variantBase, ChestType.SINGLE);
    }

    // One wood's thin base/lid children (one pair per ChestType) plus its single whole-chest icon.
    private void addVariant(Map<Path, JsonObject> files, String chestId, String variantBase) {
        for (ChestType type : ChestType.values()) {
            addPart(files, ChestModels.baseModel(chestId, type), geometryId("base", type),
                    variantBase, type);
            addPart(files, ChestModels.lidModel(chestId, type), geometryId("lid", type),
                    variantBase, type);
        }
        addPart(files, ChestModels.wholeModel(chestId), geometryId("whole", ChestType.SINGLE),
                variantBase, ChestType.SINGLE);
    }

    // Writes the thin per-wood model (parents the shared geometry, binds the #chest texture) and
    // the item definition pointing at it. The model lives under models/block/ alongside the shared
    // geometry it parents; the item def id (and so its items/ path) is the caller-supplied one.
    private void addPart(Map<Path, JsonObject> files, Identifier itemDefId, Identifier geometry,
            String variantBase, ChestType type) {
        Identifier sprite =
                PlankedChests.id("block/chest/" + ChestModels.sprite(variantBase, type));
        Identifier modelId = PlankedChests.id("block/" + itemDefId.getPath());
        files.put(this.models.json(modelId), variantModel(geometry, sprite));
        files.put(this.items.json(itemDefId), ElementModels.itemDefinition(modelId));
    }

    // Delegates to ElementModels to create a thin per-wood variant model.
    private static JsonObject variantModel(Identifier geometry, Identifier sprite) {
        return ElementModels.variantModel(geometry, TEXTURE_KEY, sprite);
    }

    // Delegates to ElementModels to create a model with the given element boxes.
    private static JsonObject model(JsonObject... elements) {
        return ElementModels.model(TEXTURE_KEY, elements);
    }

    // Delegates to ElementModels to create a textured box element for a model.
    private static JsonObject box(float x, float y, float z, float w, float h, float d,
            float texU, float texV, @Nullable Direction hidden) {
        return ElementModels.box(TEXTURE_KEY, x, y, z, w, h, d, texU, texV, hidden);
    }
}
