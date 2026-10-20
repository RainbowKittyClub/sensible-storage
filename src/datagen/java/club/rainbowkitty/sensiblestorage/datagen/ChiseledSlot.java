package club.rainbowkitty.sensiblestorage.datagen;

import club.rainbowkitty.sensiblestorage.block.ChiseledBookshelfModels;

// The six slots of a chiseled bookshelf's front, in vanilla's slot_<n>_occupied order: where
// vanilla's occupied sheet draws the slot's book (u, v in texels), and which of the 18 book sprites
// that book is. Measured from vanilla's own sheets; rkc/docs/sensible-storage/plan/planked-bookshelves.md has the table.
// Each slot's name is ChiseledBookshelfModels', which the runtime shares.
enum ChiseledSlot {
    TOP_LEFT(2, 1, 1),
    TOP_MID(1, 6, 1),
    TOP_RIGHT(5, 11, 1),
    BOTTOM_LEFT(6, 1, 9),
    BOTTOM_MID(3, 6, 9),
    BOTTOM_RIGHT(4, 11, 9);

    // Every book sprite's size.
    static final int BOOK_WIDTH = 4;
    static final int BOOK_HEIGHT = 6;

    private final int vanillaBook;
    private final int bookU;
    private final int bookV;

    ChiseledSlot(int vanillaBook, int bookU, int bookV) {
        this.vanillaBook = vanillaBook;
        this.bookU = bookU;
        this.bookV = bookV;
    }

    // The slot's name in vanilla's model ids, e.g. top_left.
    String position() {
        return ChiseledBookshelfModels.position(ordinal());
    }

    // The block state property this slot is drawn by, e.g. slot_0_occupied.
    String property() {
        return "slot_" + ordinal() + "_occupied";
    }

    // The book sprite, 1 to 18, vanilla's own sheet draws in this slot.
    int vanillaBook() {
        return vanillaBook;
    }

    // The book's left column on the front's texture.
    int bookU() {
        return bookU;
    }

    // The book's top row on the front's texture.
    int bookV() {
        return bookV;
    }
}
