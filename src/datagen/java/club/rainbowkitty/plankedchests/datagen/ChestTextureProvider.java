package club.rainbowkitty.plankedchests.datagen;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.ChestType;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.display.ChestModels;
import club.rainbowkitty.plankedchests.wood.WoodType;
import club.rainbowkitty.rkcore.common.datagen.PngAssets;

/**
 * The per-wood chest textures: the wood's 16×16 plank texture tiled 4×4 to fill the 64×64 chest
 * entity unwrap, with the matching chest overlay composited on top. One image per wood, per variant
 * (plain / trapped), per {@link ChestType} (single / left / right of a double). Written under
 * {@code textures/block/chest/}, which the vanilla {@code minecraft:blocks} atlas scans across all
 * namespaces — the base/lid {@code elements} models reference them directly.
 */
public final class ChestTextureProvider implements DataProvider {
    private static final int PLANK = 16;
    private static final int SIZE = 64;

    // Both inputs live in the datagen-only source set: the overlay art so it never ships in the
    // jar or resource pack, and the 12 vanilla plank textures because they are client assets
    // (asset-index objects, not on the mod classpath) and cannot be read at runtime.
    private static final String PLANK_DIR = "plankedchests_planks/";
    private static final String OVERLAY_DIR = "plankedchests_overlay/";
    // Vanilla's entity/chest/normal.png, vendored for the same reason the planks are.
    private static final String VANILLA_DIR = "plankedchests_vanilla/";

    // Overlay file for a variant (false = plain, true = trapped) and chest type.
    private static String overlayFile(boolean trapped, ChestType type) {
        String base = trapped ? "chest_overlay_trapped" : "chest_overlay";
        return base + (type == ChestType.SINGLE ? "" : "_" + type.getSerializedName()) + ".png";
    }

    private final PackOutput.PathProvider textures;

    public ChestTextureProvider(FabricPackOutput output) {
        this.textures = output.createPathProvider(
                PackOutput.Target.RESOURCE_PACK, "textures/block/chest");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ClassLoader loader = ChestTextureProvider.class.getClassLoader();

        // 6 distinct overlays, reused across all 12 woods — read each once up front.
        BufferedImage[][] overlays = new BufferedImage[2][ChestType.values().length];
        for (int trapped = 0; trapped < 2; trapped++) {
            for (ChestType type : ChestType.values()) {
                overlays[trapped][type.ordinal()] =
                        PngAssets.read(loader, OVERLAY_DIR + overlayFile(trapped == 1, type));
            }
        }

        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (WoodType wood : WoodType.values()) {
            BufferedImage planks =
                    tile(PngAssets.read(loader, PLANK_DIR + wood.id() + "_planks.png"));
            for (boolean trapped : new boolean[] {false, true}) {
                String variantBase = trapped ? wood.id() + "_trapped" : wood.id();
                for (ChestType type : ChestType.values()) {
                    BufferedImage overlay = overlays[trapped ? 1 : 0][type.ordinal()];
                    BufferedImage image = composite(planks, overlay);
                    Identifier id = PlankedChests.id(ChestModels.sprite(variantBase, type));
                    writes.add(PngAssets.save(
                            cache, this.textures.file(id, "png"), image, "plankedChestsTextures"));
                }
            }
        }

        // Vanilla's own chest sheet, copied through rather than composited. It is already in this
        // 64x64 layout, and it is the one chest here that is not made of planks — see
        // ChestModels#VANILLA.
        writes.add(PngAssets.save(cache,
                this.textures.file(PlankedChests.id(ChestModels.VANILLA), "png"),
                PngAssets.read(loader, VANILLA_DIR + "chest.png"), "plankedChestsTextures"));

        // The four copper chest sheets, the same way. Only the weathering half of the collection is
        // walked: a waxed chest draws from its twin's sheet, so the other four would be duplicates.
        // Each file is named after its variant, which is also vanilla's own name for the sheet.
        ChestModels.COPPER.weathering().forEach(variant -> writes.add(PngAssets.save(cache,
                this.textures.file(PlankedChests.id(variant), "png"),
                PngAssets.read(loader, VANILLA_DIR + variant + ".png"), "plankedChestsTextures")));

        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Planked Chests Textures";
    }

    // 16×16 plank → 64×64 by tiling it 4×4, no scaling.
    private static BufferedImage tile(BufferedImage plank) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (int y = 0; y < SIZE; y += PLANK) {
            for (int x = 0; x < SIZE; x += PLANK) {
                g.drawImage(plank, x, y, null);
            }
        }
        g.dispose();
        return out;
    }

    // Draws the overlay over the tiled planks with normal alpha compositing.
    private static BufferedImage composite(BufferedImage planks, BufferedImage overlay) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(planks, 0, 0, null);
        g.drawImage(overlay, 0, 0, null);
        g.dispose();
        return out;
    }
}
