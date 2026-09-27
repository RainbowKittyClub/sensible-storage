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
import club.rainbowkitty.sensiblestorage.block.ChiseledBooks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Models, blockstates and item definitions for the chiseled bookshelves, built the way vanilla
 * builds its own: a body with no front, and one quad per slot on the front, empty or occupied,
 * picked per slot by a multipart blockstate. Where vanilla's occupied quad shows a fixed book from
 * a sheet of six, an occupied slot here is two quads on the same slot template - the wood's
 * occupied background, and over it a sheet holding one book - so the books are wood-independent:
 * one sheet per book, and a child of vanilla's slot template per slot and book, shared by every
 * shelf.
 *
 * <p>The book has to use the background's own quad, corners and all. Terrain's depth test passes on
 * equal ({@code DepthStencilState.DEFAULT}), and the book, being cut out, draws after the solid
 * background, so an identical quad lands exactly on it and wins; a smaller quad in the same plane
 * rounds its depth differently and z-fights. A sheet draws its book at the texels vanilla's
 * occupied sheet draws each slot's book ({@link ChiseledSlot}), so a slot showing vanilla's own
 * book matches vanilla's sheet. Which book a slot shows is a weighted list of all 18, the same in
 * every blockstate here ({@link ChiseledBooks}); that includes an override of vanilla's own
 * chiseled bookshelf, redrawn the same way from copies of its own sheets so it takes the random
 * books too.
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
            for (int n = 1; n <= ChiseledBooks.BOOKS; n++) {
                files.put(this.models.json(bookModel(slot, n)), PackJson.model(
                        slotTemplate(slot), "texture", ChiseledBookshelfTextureProvider.book(n)));
            }
        }
        for (WoodType wood : BookshelfBlocks.chiseledBookshelves().keySet()) {
            Identifier id = SensibleStorage.id(wood.chiseledBookshelfId());
            putShelfModels(files, wood.id());
            files.put(this.models.json(inventoryModel(wood.id())), inventoryModelJson(wood.id()));
            files.put(this.blockStates.json(id), blockState(wood.id()));
            files.put(this.items.json(id), PackJson.itemDefinition(inventoryModel(wood.id())));
        }
        // Vanilla's shelf, redrawn the same way from its own sheets so it takes the random books
        // too. Its item keeps vanilla's own definition and inventory model, so neither is written.
        String vanilla = ChiseledBookshelfTextureProvider.VANILLA;
        putShelfModels(files, vanilla);
        files.put(this.blockStates.json(Identifier.withDefaultNamespace("chiseled_bookshelf")),
                blockState(vanilla));
        return DataProvider.saveAll(cache, json -> json, path -> path, files);
    }

    @Override
    public String getName() {
        return "Sensible Storage Chiseled Bookshelf Models";
    }

    // The body and twelve slot models of the shelf drawn from a named set of sheets.
    private void putShelfModels(Map<Path, JsonObject> files, String name) {
        Identifier top = ChiseledBookshelfTextureProvider.sprite(name, "top");
        Identifier side = ChiseledBookshelfTextureProvider.sprite(name, "side");
        Identifier empty = ChiseledBookshelfTextureProvider.sprite(name, "empty");
        Identifier occupied = ChiseledBookshelfTextureProvider.sprite(name,
                ChiseledBookshelfTextureProvider.OCCUPIED);
        files.put(this.models.json(bodyModel(name)),
                PackJson.model(BODY, Map.of("top", top, "side", side)));
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

    // The book drawn in an occupied slot: any of the 18, weighted by ChiseledBooks - the same list,
    // in the same order, in every blockstate, which is what keeps every client's books in step.
    private static JsonElement book(ChiseledSlot slot, int y) {
        JsonArray books = new JsonArray();
        for (int n = 1; n <= ChiseledBooks.BOOKS; n++) {
            JsonObject book = apply(bookModel(slot, n), y, false);
            book.addProperty("weight", ChiseledBooks.weight(slot.ordinal(), n));
            books.add(book);
        }
        return books;
    }

    // The inventory model of a named shelf: vanilla's, with its sheets and the empty front.
    private static JsonObject inventoryModelJson(String name) {
        return PackJson.model(INVENTORY, Map.of(
                "top", ChiseledBookshelfTextureProvider.sprite(name, "top"),
                "side", ChiseledBookshelfTextureProvider.sprite(name, "side"),
                "front", ChiseledBookshelfTextureProvider.sprite(name, "empty")));
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

    // Vanilla's template for a slot's quad, over one #texture key.
    private static Identifier slotTemplate(ChiseledSlot slot) {
        return Identifier.withDefaultNamespace(
                "block/template_chiseled_bookshelf_slot_" + slot.position());
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
