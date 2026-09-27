package club.rainbowkitty.sensiblestorage.block;

import net.minecraft.resources.Identifier;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * The ids of the models a chiseled bookshelf is drawn from, named once for the datagen that writes
 * them and the resource-pack tier that names them at runtime, so the two cannot drift. A shelf's
 * own models are named after its wood, or {@code vanilla} for vanilla's shelf as this mod redraws
 * it; the book models are shared by every shelf.
 */
public final class ChiseledBookshelfModels {
    // Each slot's name in vanilla's slot templates, slot 0 first.
    private static final String[] POSITIONS = {
        "top_left", "top_mid", "top_right", "bottom_left", "bottom_mid", "bottom_right"
    };

    private ChiseledBookshelfModels() {}

    /**
     * A slot's name in vanilla's model ids, e.g. {@code top_left}.
     *
     * @param slot the slot, 0 to 5, in {@code slot_<n>_occupied} order
     */
    public static String position(int slot) {
        return POSITIONS[slot];
    }

    /** A named shelf's body, which has no front. */
    public static Identifier body(String name) {
        return SensibleStorage.id("block/" + name + "_chiseled_bookshelf");
    }

    /** A named shelf's inventory model, with its empty front. */
    public static Identifier inventory(String name) {
        return SensibleStorage.id("block/" + name + "_chiseled_bookshelf_inventory");
    }

    /** A named shelf's quad for an empty slot. */
    public static Identifier emptySlot(String name, int slot) {
        return slot(name, "empty", slot);
    }

    /** A named shelf's quad behind an occupied slot's book, shadow included. */
    public static Identifier occupiedSlot(String name, int slot) {
        return slot(name, "occupied", slot);
    }

    /**
     * A named shelf's quad holding only the shadow an occupied slot's book casts, transparent
     * elsewhere; for the pack tier's display, which draws over the empty slot terrain draws.
     */
    public static Identifier shadowSlot(String name, int slot) {
        return slot(name, "shadow", slot);
    }

    /**
     * Book {@code book} drawn in a slot, on that slot's own quad.
     *
     * @param slot the slot, 0 to 5
     * @param book the book sprite, 1 to {@link ChiseledBooks#BOOKS}
     */
    public static Identifier book(int slot, int book) {
        return SensibleStorage.id(
                "block/chiseled_bookshelf_book_" + position(slot) + "_" + book);
    }

    /**
     * The item model a wood's shelf shows its books through for a client with the pack but not the
     * mod: an item definition drawing each occupied slot's book and its shadow, and nothing else,
     * picked by the stack's {@code custom_model_data} strings (see {@code ChiseledBooksHolder}).
     */
    public static Identifier books(WoodType wood) {
        return SensibleStorage.id(wood.id() + "_chiseled_bookshelf_books");
    }

    // A named shelf's quad for one slot: empty, occupied or shadow.
    private static Identifier slot(String name, String state, int slot) {
        return SensibleStorage.id(
                "block/" + name + "_chiseled_bookshelf_" + state + "_slot_" + position(slot));
    }
}
