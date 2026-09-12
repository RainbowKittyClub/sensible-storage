package club.rainbowkitty.plankedchests.datagen;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopperCollection;

import club.rainbowkitty.plankedchests.display.ChestModels;
import club.rainbowkitty.rkcore.common.datagen.PngAssets;

/**
 * Cart icons for the cargoes drawn over a vanilla block's own texture: the seventeen shulker boxes,
 * the barrel and the eight copper chests. One 16×16 sprite, a {@code minecraft:item/generated}
 * model and an item definition apiece, picked per stack by {@code ChestMinecartItem#cargoModel}
 * from the cargo it carries.
 *
 * <p>The only thing that differs from {@link ChestMinecartTextureProvider} is what goes under the
 * overlay — a wood's planks there, a vanilla block texture here. Both read a hand-authored overlay
 * of the same cart from {@code plankedchests_overlay/} and composite it the same way, through
 * {@link CartIcons#composite}.
 *
 * <p>Taking the cargo's own texture rather than a flat colour is what keeps a cart's cargo the same
 * material as the block that made it: a shulker shell's rim and speckling come through the overlay,
 * and a resource pack that retextures shulker boxes or barrels retextures these carts with them.
 */
public final class CargoCartTextureProvider implements DataProvider {
    // Both inputs live in the datagen-only source set, for the reasons ChestTextureProvider gives:
    // vanilla's block textures and the hand-authored overlays alike are client assets, not on the
    // mod classpath.
    private static final String VANILLA_DIR = "plankedchests_vanilla/";
    private static final String OVERLAY_DIR = "plankedchests_overlay/";

    // The metal each copper chest shows, one per weather state. Paired with the chests through the
    // collection rather than by rewriting their names, so the eight cannot drift onto the wrong
    // texture; and built with `same` because a waxed chest is the same metal as its unwaxed twin,
    // which is why vanilla gives that pair one texture too.
    private static final WeatheringCopperCollection<String> COPPER =
            WeatheringCopperCollection.same(new WeatheringCopperCollection.ByState<>(
                    "copper_block.png", "exposed_copper.png",
                    "weathered_copper.png", "oxidized_copper.png"));

    private final PackOutput.PathProvider textures;
    private final PackOutput.PathProvider models;
    private final PackOutput.PathProvider items;

    public CargoCartTextureProvider(FabricPackOutput output) {
        this.textures = output.createPathProvider(
                PackOutput.Target.RESOURCE_PACK, "textures/item");
        this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        this.items = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ClassLoader loader = CargoCartTextureProvider.class.getClassLoader();

        List<Icon> icons = new ArrayList<>();
        // The undyed box and the sixteen dyed ones, taken from the registry rather than from a list
        // of colour names, so this cannot fall out of step with the recipes ChestRecipeProvider
        // writes from the same two collections. Each shows its own top shell, the flat 16×16
        // vanilla already ships for the block's particles and top face.
        icons.add(shell(Blocks.SHULKER_BOX));
        Blocks.DYED_SHULKER_BOX.forEach(box -> icons.add(shell(box)));
        // The barrel shows its lid, which is the face a cart's barrel turns up — see
        // PlankedChestMinecart#cargoState for the turn. Deliberately not its underside: the cargo
        // region samples only the base's top rows, and the underside's plank gaps fall right where
        // the side's iron hoops do, so an icon built on it reads as a barrel lying on its side.
        icons.add(new Icon(Blocks.BARREL, "barrel_top.png", "barrel_minecart_overlay.png"));
        // The eight copper chests — four weather states, each waxed and not — zipped against their
        // metal through the same collection ChestRecipeProvider writes their recipes from.
        WeatheringCopperCollection.zipApply(Blocks.COPPER_CHEST, COPPER,
                (chest, metal) -> icons.add(new Icon(chest, metal, "copper_chest_minecart.png")));

        List<CompletableFuture<?>> writes = new ArrayList<>();
        Map<Path, JsonObject> json = new HashMap<>();
        CartIcons.IconSink sink = new CartIcons.IconSink(
                this.textures, this.models, this.items, cache, writes, json);
        icons.forEach(icon -> icon(icon, loader, sink));

        json.forEach((path, file) -> writes.add(DataProvider.saveStable(cache, file, path)));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Planked Chests Cargo Cart Icons";
    }

    // Reads one icon's two source textures and hands them to the sink to write.
    private void icon(Icon icon, ClassLoader loader, CartIcons.IconSink sink) {
        String baseFile = VANILLA_DIR + icon.base();
        String overlayFile = OVERLAY_DIR + icon.overlay();
        BufferedImage base = CartIcons.sized(PngAssets.read(loader, baseFile), baseFile);
        BufferedImage overlay = CartIcons.sized(PngAssets.read(loader, overlayFile), overlayFile);

        sink.write(ChestModels.cargoCartModel(Ids.blockPath(icon.cargo())), base, overlay,
                "plankedChestsCargoCartIcons");
    }

    // A shulker box, which shows the texture named after the block itself.
    private static Icon shell(Block box) {
        return new Icon(box, Ids.blockPath(box) + ".png", "shulker_minecart_overlay.png");
    }

    // One icon to draw: `cargo` names it through ChestModels#cargoCartModel, while `base` is
    // given rather than derived because a barrel shows one particular face rather than a
    // texture named after the block.
    private record Icon(Block cargo, String base, String overlay) {
    }
}
