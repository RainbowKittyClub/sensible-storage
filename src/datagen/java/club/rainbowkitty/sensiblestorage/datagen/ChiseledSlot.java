package club.rainbowkitty.sensiblestorage.datagen;

// The six slots of a chiseled bookshelf's front, in vanilla's slot_<n>_occupied order: the name
// vanilla's slot templates use, where vanilla's occupied sheet draws the slot's book (u, v in
// texels), and which of the 18 book sprites that book is. Measured from vanilla's own sheets;
// docs/plan/planked-bookshelves.md has the table.
enum ChiseledSlot {
    TOP_LEFT("top_left", 2, 1, 1),
    TOP_MID("top_mid", 1, 6, 1),
    TOP_RIGHT("top_right", 5, 11, 1),
    BOTTOM_LEFT("bottom_left", 6, 1, 9),
    BOTTOM_MID("bottom_mid", 3, 6, 9),
    BOTTOM_RIGHT("bottom_right", 4, 11, 9);

    // Every book sprite's size, and how many there are.
    static final int BOOK_WIDTH = 4;
    static final int BOOK_HEIGHT = 6;
    static final int BOOKS = 18;

    private final String position;
    private final int vanillaBook;
    private final int bookU;
    private final int bookV;

    ChiseledSlot(String position, int vanillaBook, int bookU, int bookV) {
        this.position = position;
        this.vanillaBook = vanillaBook;
        this.bookU = bookU;
        this.bookV = bookV;
    }

    // The slot's name in vanilla's model ids, e.g. top_left.
    String position() {
        return position;
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
