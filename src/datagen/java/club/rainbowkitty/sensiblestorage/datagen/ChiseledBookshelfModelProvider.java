package club.rainbowkitty.sensiblestorage.datagen;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 * Models, blockstates and item definitions for the chiseled bookshelves, built the way vanilla
 * builds its own: a body with no front, and one quad per slot on the front, empty or occupied,
 * picked per slot by a multipart blockstate. Where vanilla's occupied quad shows a fixed book from
 * a sheet of six, an occupied slot here is two quads in one plane — the wood's occupied background
 * and a book on top — so the books are wood-independent: six book templates, one per slot, and a
 * child per slot and book, shared by every shelf.
 *
 * <p>A book quad sits at the texels vanilla's occupied sheet draws that slot's book at
 * ({@link ChiseledSlot}), so a slot showing vanilla's own book for it matches vanilla's sheet.
 */
public final class ChiseledBookshelfModelProvider implements DataProvider {
    // Vanilla's body (no north face) and its inventory model (with one), both over top/side keys.
    private static final Identifier BODY =
            Identifier.withDefaultNamespace("block/chiseled_bookshelf");
    private static final Identifier INVENTORY =
            Identifier.withDefaultNamespace("block/chiseled_bookshelf_inventory");
    // The blockstate's facings, each turned a further 90 degrees about y, as vanilla's.
    private static final String[] FACINGS = {"north", "east", "south", "west"};

    private final PackOutput.PathProvider blockStates;
    private final PackOutput.PathProvider models;
    private final PackOutput.PathProvider items;

    /**
     * Creates a provider for chiseled bookshelf blockstates, models and item definitions.
     *
     * @param output the pack output providing paths for all three
     */
    public ChiseledBookshelfModelProvider(FabricPackOutput output) {
        this.blockStates =
                output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        this.items = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<Path, JsonObject> files = new HashMap<>();
        for (ChiseledSlot slot : ChiseledSlot.values()) {
            files.put(this.models.json(bookTemplate(slot)), bookTemplateJson(slot));
            for (int n = 1; n <= ChiseledSlot.BOOKS; n++) {
                files.put(this.models.json(bookModel(slot, n)), PackJson.model(
                        bookTemplate(slot), "book", ChiseledBookshelfTextureProvider.book(n)));
            }
        }
        for (WoodType wood : BookshelfBlocks.chiseledBookshelves().keySet()) {
            Identifier id = SensibleStorage.id(wood.chiseledBookshelfId());
            putShelfModels(files, wood.id());
            files.put(this.blockStates.json(id), blockState(wood.id()));
            files.put(this.items.json(id), PackJson.itemDefinition(inventoryModel(wood.id())));
        }
        return DataProvider.saveAll(cache, json -> json, path -> path, files);
    }

    @Override
    public String getName() {
        return "Sensible Storage Chiseled Bookshelf Models";
    }

    // The body, inventory and twelve slot models of the shelf drawn from a named set of sheets.
    private void putShelfModels(Map<Path, JsonObject> files, String name) {
        Identifier top = ChiseledBookshelfTextureProvider.sprite(name, "top");
        Identifier side = ChiseledBookshelfTextureProvider.sprite(name, "side");
        Identifier empty = ChiseledBookshelfTextureProvider.sprite(name, "empty");
        Identifier occupied = ChiseledBookshelfTextureProvider.sprite(name,
                ChiseledBookshelfTextureProvider.OCCUPIED);
        files.put(this.models.json(bodyModel(name)),
                PackJson.model(BODY, Map.of("top", top, "side", side)));
        files.put(this.models.json(inventoryModel(name)),
                PackJson.model(INVENTORY, Map.of("top", top, "side", side, "front", empty)));
        for (ChiseledSlot slot : ChiseledSlot.values()) {
            files.put(this.models.json(slotModel(name, "empty", slot)),
                    PackJson.model(slotTemplate(slot), "texture", empty));
            files.put(this.models.json(slotModel(name, "occupied", slot)),
                    PackJson.model(slotTemplate(slot), "texture", occupied));
        }
    }

    // Vanilla's multipart over this shelf's models: per facing, the body, then per slot the empty
    // quad or the occupied background with its book.
    private static JsonObject blockState(String name) {
        JsonArray parts = new JsonArray();
        for (int i = 0; i < FACINGS.length; i++) {
            String facing = FACINGS[i];
            int y = 90 * i;
            parts.add(part(when(facing), apply(bodyModel(name), y, true)));
            for (ChiseledSlot slot : ChiseledSlot.values()) {
                parts.add(part(when(facing, slot, false),
                        apply(slotModel(name, "empty", slot), y, false)));
                parts.add(part(when(facing, slot, true),
                        apply(slotModel(name, "occupied", slot), y, false)));
                parts.add(part(when(facing, slot, true), book(slot, y)));
            }
        }
        JsonObject root = new JsonObject();
        root.add("multipart", parts);
        return root;
    }

    // The book drawn in an occupied slot: vanilla's own book for that slot.
    private static JsonElement book(ChiseledSlot slot, int y) {
        return apply(bookModel(slot, slot.vanillaBook()), y, false);
    }

    // One multipart entry.
    private static JsonObject part(JsonObject when, JsonElement apply) {
        JsonObject part = new JsonObject();
        part.add("when", when);
        part.add("apply", apply);
        return part;
    }

    // A condition on the facing alone.
    private static JsonObject when(String facing) {
        JsonObject when = new JsonObject();
        when.addProperty("facing", facing);
        return when;
    }

    // A condition on the facing and one slot's occupancy.
    private static JsonObject when(String facing, ChiseledSlot slot, boolean occupied) {
        JsonObject when = when(facing);
        when.addProperty(slot.property(), Boolean.toString(occupied));
        return when;
    }

    // One model variant, rotated to the facing.
    private static JsonObject apply(Identifier model, int y, boolean uvlock) {
        JsonObject apply = new JsonObject();
        apply.addProperty("model", model.toString());
        if (y != 0) {
            apply.addProperty("y", y);
        }
        if (uvlock) {
            apply.addProperty("uvlock", true);
        }
        return apply;
    }

    // A single north-facing quad at the book's texels, sampling the whole of its #book sprite.
    private static JsonObject bookTemplateJson(ChiseledSlot slot) {
        int x = 16 - slot.bookU();
        int y = 16 - slot.bookV();
        JsonObject face = new JsonObject();
        face.add("uv", ints(0, 0, 16, 16));
        face.addProperty("texture", "#book");
        face.addProperty("cullface", "north");
        JsonObject faces = new JsonObject();
        faces.add("north", face);
        JsonObject element = new JsonObject();
        element.add("from", ints(x - ChiseledSlot.BOOK_WIDTH, y - ChiseledSlot.BOOK_HEIGHT, 0));
        element.add("to", ints(x, y, 0));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "#book");
        JsonObject root = new JsonObject();
        root.add("textures", textures);
        root.add("elements", elements);
        return root;
    }

    // A JSON array of ints.
    private static JsonArray ints(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    // Vanilla's template for a slot's quad, over one #texture key.
    private static Identifier slotTemplate(ChiseledSlot slot) {
        return Identifier.withDefaultNamespace(
                "block/template_chiseled_bookshelf_slot_" + slot.position());
    }

    // This mod's template for a slot's book, over one #book key.
    private static Identifier bookTemplate(ChiseledSlot slot) {
        return SensibleStorage.id("block/template_chiseled_bookshelf_book_" + slot.position());
    }

    // Book n in a slot.
    private static Identifier bookModel(ChiseledSlot slot, int n) {
        return SensibleStorage.id("block/chiseled_bookshelf_book_" + slot.position() + "_" + n);
    }

    // A named shelf's body, with no front.
    private static Identifier bodyModel(String name) {
        return SensibleStorage.id("block/" + name + "_chiseled_bookshelf");
    }

    // A named shelf's inventory model, with its empty front.
    private static Identifier inventoryModel(String name) {
        return SensibleStorage.id("block/" + name + "_chiseled_bookshelf_inventory");
    }

    // A named shelf's quad for one slot, empty or occupied.
    private static Identifier slotModel(String name, String state, ChiseledSlot slot) {
        return SensibleStorage.id(
                "block/" + name + "_chiseled_bookshelf_" + state + "_slot_" + slot.position());
    }
}
