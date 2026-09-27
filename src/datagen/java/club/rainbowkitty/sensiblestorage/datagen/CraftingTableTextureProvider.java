package club.rainbowkitty.sensiblestorage.datagen;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import club.rainbowkitty.kalic0re.common.datagen.PngAssets;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * The per-wood crafting table textures, for every wood with a table (so not oak): vanilla's own
 * three faces with every oak-plank pixel replaced by the same pixel of the wood's planks. Written
 * under {@code textures/block/crafting_table/}, one-to-one with the sprites
 * {@link CraftingTableModelProvider} binds; the bottom is {@link PlankSpriteProvider}'s.
 *
 * <p>An overlay here is a mask, not art: transparent where the wood shows, and
 * {@link PngAssets#CUTOUT} magenta wherever vanilla's frame, tools and worktop should stay. The
 * wood's planks are cut by it, then laid over vanilla's face, which fills the cut back in.
 */
public final class CraftingTableTextureProvider implements DataProvider {
    // Faces with an overlay, named by vanilla's own texture suffixes.
    static final List<String> FACES = List.of("front", "side", "top");

    // Same datagen-only directories ChestTextureProvider reads, for the same reasons.
    private static final String OVERLAY_DIR = "sensiblestorage_overlay/block/";
    private static final String VANILLA_DIR = "sensiblestorage_vanilla/";
    private static final int SIZE = 16;

    private final PackOutput.PathProvider textures;

    /**
     * Constructs a texture provider for generated crafting table textures.
     *
     * @param output the Fabric datagen output
     */
    public CraftingTableTextureProvider(FabricPackOutput output) {
        this.textures = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ClassLoader loader = CraftingTableTextureProvider.class.getClassLoader();

        // Three faces, each an overlay and the vanilla texture it masks, shared by every wood.
        Map<String, BufferedImage> overlays = new LinkedHashMap<>();
        Map<String, BufferedImage> vanilla = new LinkedHashMap<>();
        for (String face : FACES) {
            overlays.put(face, read(loader, OVERLAY_DIR + "crafting_table_" + face + ".png"));
            vanilla.put(face, read(loader, VANILLA_DIR + "crafting_table_" + face + ".png"));
        }

        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (WoodType wood : CraftingTableBlocks.tables().keySet()) {
            BufferedImage planks = WoodTints.planks(loader, wood);
            for (String face : FACES) {
                BufferedImage cut = PngAssets.composite(planks, overlays.get(face));
                writes.add(save(cache, wood, face, PngAssets.composite(vanilla.get(face), cut)));
            }
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Sensible Storage Crafting Table Textures";
    }

    // Sprite path of one wood's face, e.g. block/crafting_table/dark_oak_front.
    static String sprite(WoodType wood, String face) {
        return "block/crafting_table/" + wood.id() + "_" + face;
    }

    // Reads one 16×16 input, naming the file if it is any other size.
    private static BufferedImage read(ClassLoader loader, String path) {
        return PngAssets.sized(PngAssets.read(loader, path), SIZE, path);
    }

    // Writes one wood's face under its sprite path.
    private CompletableFuture<?> save(CachedOutput cache, WoodType wood, String face,
            BufferedImage image) {
        return PngAssets.save(cache,
                this.textures.file(SensibleStorage.id(sprite(wood, face)), "png"), image,
                "sensibleStorageTableTextures");
    }
}
