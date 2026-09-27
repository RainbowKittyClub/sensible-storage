package club.rainbowkitty.sensiblestorage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * How often each of the 18 book sprites is drawn in each slot of a chiseled bookshelf — the one
 * definition every blockstate carrying the random books is generated from, so every client that
 * draws them agrees on which book a slot shows.
 *
 * <p>A block state says nothing about the books: each client picks them while meshing the chunk,
 * from a random source seeded by the block's position. A multipart model draws one seed per block
 * and resets to it before every part ({@code MultiPartModel#collectParts}), so all six slots read
 * the same random number, and a weighted list picks with {@code nextInt(totalWeight)}. Identical
 * lists would therefore put the same book in every slot. Each slot's weights instead sum to a
 * different prime near 1000, which leaves the six choices independent of one another and every
 * book within 1.7% of equally likely; {@code docs/plan/planked-bookshelves.md} has the
 * measurements.
 *
 * <p>The one place books are sent is the resource-pack tier, whose display entities draw a planked
 * shelf's books over its frame; {@link #book} works out the number each shows by repeating the
 * client's own pick, so they match terrain's.
 *
 * <p>Changing any weight, or the order of the books, reshuffles every shelf for every client at
 * once, so treat the numbers here as part of the look.
 */
public final class ChiseledBooks {
    /** The number of book sprites, numbered from 1. */
    public static final int BOOKS = 18;

    // Each slot's total weight, slot 0 (top left) first: six distinct primes.
    private static final int[] TOTALS = {1009, 1013, 1019, 1021, 1031, 1033};

    private ChiseledBooks() {}

    /**
     * The weight of one book in one slot: the slot's total spread as evenly as whole numbers allow,
     * the first few books taking one more.
     *
     * @param slot the slot, 0 to 5, in {@code slot_<n>_occupied} order
     * @param book the book sprite, 1 to {@link #BOOKS}
     */
    public static int weight(int slot, int book) {
        int total = TOTALS[slot];
        return total / BOOKS + (book <= total % BOOKS ? 1 : 0);
    }

    /**
     * The book a client draws in one slot of a chiseled bookshelf at a position, worked out the
     * way the client's renderer works it out, for display entities that must show the same book
     * terrain would: {@code ModelBlockRenderer#tesselateBlock} seeds a
     * {@code SingleThreadedRandomSource} with the position's seed, {@code MultiPartModel} draws one
     * long and reseeds with it before every part, and the slot's weighted list draws
     * {@code nextInt} of its total and walks its weights in order.
     *
     * @param pos the shelf's position
     * @param slot the slot, 0 to 5
     * @return the book sprite, 1 to {@link #BOOKS}
     */
    public static int book(BlockPos pos, int slot) {
        RandomSource random = RandomSource.createThreadLocalInstance(Mth.getSeed(pos));
        random.setSeed(random.nextLong());
        int pick = random.nextInt(TOTALS[slot]);
        for (int book = 1; ; book++) {
            pick -= weight(slot, book);
            if (pick < 0) {
                return book;
            }
        }
    }
}
