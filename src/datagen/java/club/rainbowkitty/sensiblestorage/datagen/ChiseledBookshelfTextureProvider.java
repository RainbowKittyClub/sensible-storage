package club.rainbowkitty.sensiblestorage.datagen;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import club.rainbowkitty.kalic0re.common.datagen.PngAssets;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.block.ChiseledBooks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * The chiseled bookshelves' textures, under {@code textures/block/chiseled_bookshelf/}: per wood,
 * the top, side and empty front recoloured to the wood, and an occupied background; the same four
 * for vanilla's own shelf, untinted ({@link #VANILLA}); and once, a sheet per book, drawn over an
 * occupied slot by {@link ChiseledBookshelfModelProvider}.
 *
 * <p>The three overlays are gray end to end, the luma of vanilla's own oak sheets, so each is
 * {@link PngAssets#colourise}d whole with the wood's median plank colour. There is no overlay for
 * the occupied background: vanilla's occupied sheet differs from its empty one, outside the books,
 * only in a one-pixel shadow right of each left and middle book. That shadow is found by comparing
 * vanilla's two sheets with the books' own footprints, and written into a copy of the empty overlay
 * as the luma of vanilla's shadowed colour before colourising, so the shadow follows the wood too.
 */
public final class ChiseledBookshelfTextureProvider implements DataProvider {
    /** The sheets drawn from an overlay of the same name, per wood. */
    public static final List<String> SHEETS = List.of("top", "side", "empty");

    /** The per-wood occupied background's sheet name. */
    public static final String OCCUPIED = "occupied";

    /**
     * The per-wood sheet of the books' shadows alone, transparent elsewhere, which the pack tier's
     * display draws beside its books; terrain has the same pixels in the occupied background.
     */
    public static final String SHADOW = "shadow";

    /**
     * The name vanilla's own sheets go under, for the override of vanilla's chiseled bookshelf that
     * gives it the random books too: copied untinted, so the override draws vanilla's look from
     * this mod's namespace, and a texture pack's version no longer reaches it.
     */
    public static final String VANILLA = "vanilla";

    private static final String OVERLAY_DIR = "sensiblestorage_overlay/block/chiseled_bookshelf/";
    private static final String VANILLA_DIR = "sensiblestorage_vanilla/";

    private final PackOutput.PathProvider textures;

    /**
     * Constructs a texture provider for generated chiseled bookshelf textures.
     *
     * @param output the Fabric datagen output
     */
    public ChiseledBookshelfTextureProvider(FabricPackOutput output) {
        this.textures = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    /** One wood's sheet, e.g. {@code sensiblestorage:block/chiseled_bookshelf/birch_empty}. */
    public static Identifier sprite(WoodType wood, String sheet) {
        return sprite(wood.id(), sheet);
    }

    /** A named set's sheet, e.g. {@code sensiblestorage:block/chiseled_bookshelf/birch_top}. */
    public static Identifier sprite(String name, String sheet) {
        return SensibleStorage.id("block/chiseled_bookshelf/" + name + "_" + sheet);
    }

    /**
     * Book sheet {@code n}, 1 to 18, e.g. {@code sensiblestorage:block/chiseled_bookshelf/book_7}:
     * a 16x16 front, transparent but for book {@code n} at each slot's book position.
     */
    public static Identifier book(int n) {
        return SensibleStorage.id("block/chiseled_bookshelf/book_" + n);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ClassLoader loader = ChiseledBookshelfTextureProvider.class.getClassLoader();
        List<BufferedImage> books = new ArrayList<>();
        for (int n = 1; n <= ChiseledBooks.BOOKS; n++) {
            books.add(PngAssets.read(loader, OVERLAY_DIR + "book" + n + ".png"));
        }
        BufferedImage[] overlays = new BufferedImage[SHEETS.size()];
        for (int i = 0; i < overlays.length; i++) {
            overlays[i] =
                    read(loader, OVERLAY_DIR + "chiseled_bookshelf_" + SHEETS.get(i) + ".png");
        }
        BufferedImage vanillaEmpty = read(loader, VANILLA_DIR + "chiseled_bookshelf_empty.png");
        Map<Integer, Integer> shadow = shadow(vanillaEmpty,
                read(loader, VANILLA_DIR + "chiseled_bookshelf_occupied.png"), books);
        BufferedImage occupied = withShadow(overlays[SHEETS.indexOf("empty")], shadow);
        BufferedImage shadowOnly = withShadow(new BufferedImage(16, 16,
                BufferedImage.TYPE_INT_ARGB), shadow);
        int reference = WoodTints.referenceGray(loader);

        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (String sheet : SHEETS) {
            writes.add(save(cache, sprite(VANILLA, sheet),
                    read(loader, VANILLA_DIR + "chiseled_bookshelf_" + sheet + ".png")));
        }
        BufferedImage vanillaOccupied = copy(vanillaEmpty);
        shadow.forEach((at, colour) -> vanillaOccupied.setRGB(at % 16, at / 16, colour));
        writes.add(save(cache, sprite(VANILLA, OCCUPIED), vanillaOccupied));
        for (WoodType wood : BookshelfBlocks.chiseledBookshelves().keySet()) {
            int median = PngAssets.medianColour(WoodTints.planks(loader, wood));
            for (int i = 0; i < overlays.length; i++) {
                writes.add(save(cache, sprite(wood, SHEETS.get(i)),
                        PngAssets.colourise(overlays[i], median, reference)));
            }
            writes.add(save(cache, sprite(wood, OCCUPIED),
                    PngAssets.colourise(occupied, median, reference)));
            writes.add(save(cache, sprite(wood, SHADOW),
                    PngAssets.colourise(shadowOnly, median, reference)));
        }
        for (int n = 1; n <= ChiseledBooks.BOOKS; n++) {
            writes.add(save(cache, book(n), bookSheet(books.get(n - 1))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Sensible Storage Chiseled Bookshelf Textures";
    }

    // Where vanilla's occupied sheet differs from its empty one outside the books - the shadow each
    // book casts - by pixel index, mapped to vanilla's occupied colour there. Books go where
    // vanilla draws them (ChiseledSlot); books holds all 18 sprites, book 1 first.
    static Map<Integer, Integer> shadow(BufferedImage empty, BufferedImage occupied,
            List<BufferedImage> books) {
        Set<Integer> footprint = new HashSet<>();
        for (ChiseledSlot slot : ChiseledSlot.values()) {
            BufferedImage book = books.get(slot.vanillaBook() - 1);
            for (int y = 0; y < ChiseledSlot.BOOK_HEIGHT; y++) {
                for (int x = 0; x < ChiseledSlot.BOOK_WIDTH; x++) {
                    if (book.getRGB(x, y) >>> 24 != 0) {
                        footprint.add(index(slot.bookU() + x, slot.bookV() + y));
                    }
                }
            }
        }
        Map<Integer, Integer> shadow = new LinkedHashMap<>();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (!footprint.contains(index(x, y))
                        && empty.getRGB(x, y) != occupied.getRGB(x, y)) {
                    shadow.put(index(x, y), occupied.getRGB(x, y));
                }
            }
        }
        if (shadow.isEmpty()) {
            throw new IllegalStateException("Vanilla's occupied sheet has no shadow to take");
        }
        return shadow;
    }

    // A copy of a gray overlay with each shadow pixel set to its colour's luma, as overlays are.
    private static BufferedImage withShadow(BufferedImage gray,
            Map<Integer, Integer> shadow) {
        BufferedImage out = copy(gray);
        shadow.forEach((at, colour) -> {
            int luma = WoodTints.luma(colour);
            out.setRGB(at % 16, at / 16, 0xFF000000 | luma << 16 | luma << 8 | luma);
        });
        return out;
    }

    // A transparent 16x16 front with one book drawn at every slot's book position, so each slot's
    // quad, sampling its own region as vanilla's slot templates do, finds the book where it goes.
    private static BufferedImage bookSheet(BufferedImage book) {
        BufferedImage sheet = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (ChiseledSlot slot : ChiseledSlot.values()) {
            sheet.getGraphics().drawImage(book, slot.bookU(), slot.bookV(), null);
        }
        return sheet;
    }

    // A 16x16 ARGB copy of an image.
    private static BufferedImage copy(BufferedImage image) {
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(image, 0, 0, null);
        return out;
    }

    // A pixel's position as one int, row-major.
    private static int index(int x, int y) {
        return y * 16 + x;
    }

    // Reads one 16x16 input, naming the file if it is any other size.
    private static BufferedImage read(ClassLoader loader, String path) {
        return PngAssets.sized(PngAssets.read(loader, path), 16, path);
    }

    // Writes one image under its sprite id.
    private CompletableFuture<?> save(CachedOutput cache, Identifier sprite, BufferedImage image) {
        return PngAssets.save(cache, this.textures.file(sprite, "png"), image,
                "sensibleStorageChiseledTextures");
    }
}
