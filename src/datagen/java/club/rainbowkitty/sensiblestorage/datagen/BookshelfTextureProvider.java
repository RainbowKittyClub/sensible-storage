package club.rainbowkitty.sensiblestorage.datagen;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import club.rainbowkitty.kalic0re.common.datagen.PngAssets;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * The per-wood bookshelf side, for every wood with a bookshelf (so not oak): vanilla's own side
 * with its frame recoloured to the wood and its books kept. Written under
 * {@code textures/block/bookshelf/}; the ends are {@link PlankSpriteProvider}'s.
 *
 * <p>The overlay's gray pixels are the frame, drawn as the luma of vanilla's own oak frame, so
 * {@link PngAssets#colourise} with the wood's median plank colour recolours it at the same
 * brightness relationships. Its {@link PngAssets#CUTOUT} magenta marks vanilla's books: the tinted
 * frame is cut there and laid over vanilla's side, which fills the cut back in — the crafting
 * table's two-composite pattern.
 */
public final class BookshelfTextureProvider implements DataProvider {
    private static final String OVERLAY = "sensiblestorage_overlay/block/bookshelf.png";
    private static final String VANILLA = "sensiblestorage_vanilla/bookshelf.png";

    private final PackOutput.PathProvider textures;

    /**
     * Constructs a texture provider for generated bookshelf sides.
     *
     * @param output the Fabric datagen output
     */
    public BookshelfTextureProvider(FabricPackOutput output) {
        this.textures = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    /** The generated side sprite for a wood, e.g. {@code sensiblestorage:block/bookshelf/birch}. */
    public static Identifier sprite(WoodType wood) {
        return SensibleStorage.id("block/bookshelf/" + wood.id());
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ClassLoader loader = BookshelfTextureProvider.class.getClassLoader();
        BufferedImage overlay = PngAssets.sized(PngAssets.read(loader, OVERLAY), 16, OVERLAY);
        BufferedImage vanilla = PngAssets.sized(PngAssets.read(loader, VANILLA), 16, VANILLA);
        BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int reference = WoodTints.referenceGray(loader);

        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (WoodType wood : BookshelfBlocks.bookshelves().keySet()) {
            int median = PngAssets.medianColour(WoodTints.planks(loader, wood));
            BufferedImage frame = PngAssets.composite(blank,
                    PngAssets.colourise(overlay, median, reference));
            writes.add(PngAssets.save(cache, this.textures.file(sprite(wood), "png"),
                    PngAssets.composite(vanilla, frame), "sensibleStorageBookshelfTextures"));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Sensible Storage Bookshelf Textures";
    }
}
