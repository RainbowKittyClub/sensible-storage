package club.rainbowkitty.sensiblestorage.display;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.world.level.block.state.properties.ChestType;

import club.rainbowkitty.sensiblestorage.SensibleStorage;

/**
 * The pack half of drawing vanilla's chests with display entities ({@link VanillaChestDisplays}).
 * At pack build it copies vanilla's plain, trapped and Christmas chest sheets, single and both
 * halves, from the client jar Polymer caches to this mod's {@code textures/block/chest/}, where the
 * display models read them, then replaces vanilla's with fully transparent images of the same
 * size. Vanilla draws every chest with an alpha-tested render type ({@code entity_cutout_cull}), so
 * a pack client's own chest block entities, chest blocks drawn outside one, and the chest item's
 * special model all draw nothing. The chest and trapped chest item definitions are replaced with
 * ones that draw the copies, keeping vanilla's Christmas switch.
 *
 * <p>None of it ships in the mod jar's assets, so a client running this mod without the server
 * pack keeps vanilla's chests. All of it or none goes in: a sheet that could not be copied is
 * never blanked.
 */
public final class VanillaChestPack {
    /**
     * Jar-root directory datagen writes the files this adds to the pack verbatim into, laid out as
     * in the pack; outside {@code assets/}, so they reach no client but through the pack.
     */
    public static final String OVERRIDES_ROOT = SensibleStorage.MOD_ID + "_pack";

    private static final String VANILLA_SHEETS = "assets/minecraft/textures/entity/chest/";
    private static final String COPIES =
            "assets/" + SensibleStorage.MOD_ID + "/textures/block/chest/";

    // Vanilla's sheet names (ChestSpecialRenderer) to the variant bases their copies go under.
    private static final Map<String, String> SHEETS = Map.of(
            "normal", ChestModels.VANILLA,
            "trapped", ChestModels.VANILLA_TRAPPED,
            "christmas", ChestModels.VANILLA_CHRISTMAS);

    // The item definitions that draw a chest item from the copies, as datagen wrote them.
    private static final List<String> ITEM_DEFINITIONS = List.of(
            "assets/minecraft/items/chest.json",
            "assets/minecraft/items/trapped_chest.json");

    // Whether the pack last built draws vanilla's chests transparent.
    private static volatile boolean blanked;

    private VanillaChestPack() {}

    /** Hooks pack building, after every mod's assets are in so a retextured sheet is copied. */
    public static void register() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT
                .register(VanillaChestPack::build);
    }

    /** Whether the pack draws vanilla's chests transparent, so the server has to draw them. */
    public static boolean blanked() {
        return blanked;
    }

    // Copies, blanks and overrides, or leaves the pack alone if any input is missing.
    private static void build(ResourcePackBuilder builder) {
        blanked = false;
        Map<String, byte[]> files = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> sheet : SHEETS.entrySet()) {
                for (ChestType type : ChestType.values()) {
                    String vanilla = VANILLA_SHEETS + ChestModels.sprite(sheet.getKey(), type)
                            + ".png";
                    byte[] png = builder.getDataOrSource(vanilla);
                    if (png == null) {
                        throw new IOException("no " + vanilla + " to copy");
                    }
                    files.put(COPIES + ChestModels.sprite(sheet.getValue(), type) + ".png", png);
                    files.put(vanilla, transparent(png));
                }
            }
            for (String path : ITEM_DEFINITIONS) {
                files.put(path, own(path));
            }
        } catch (IOException e) {
            SensibleStorage.LOGGER.error("Vanilla chests stay vanilla-drawn: {}", e.getMessage());
            return;
        }
        files.forEach(builder::addData);
        blanked = true;
    }

    // A fully transparent PNG the size of `png`.
    private static byte[] transparent(byte[] png) throws IOException {
        BufferedImage sheet = ImageIO.read(new ByteArrayInputStream(png));
        if (sheet == null) {
            throw new IOException("a chest sheet is not a readable PNG");
        }
        BufferedImage clear =
                new BufferedImage(sheet.getWidth(), sheet.getHeight(), BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(clear, "png", out);
        return out.toByteArray();
    }

    // One of this mod's OVERRIDES_ROOT files.
    private static byte[] own(String path) throws IOException {
        Path file = FabricLoader.getInstance().getModContainer(SensibleStorage.MOD_ID)
                .flatMap(mod -> mod.findPath(OVERRIDES_ROOT + "/" + path))
                .orElseThrow(() -> new IOException(
                        "no " + OVERRIDES_ROOT + "/" + path + "; run generateAssets"));
        return Files.readAllBytes(file);
    }
}
