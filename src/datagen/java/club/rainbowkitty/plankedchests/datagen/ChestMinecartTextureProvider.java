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
import net.minecraft.resources.Identifier;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.display.ChestModels;
import club.rainbowkitty.plankedchests.wood.WoodType;
import club.rainbowkitty.rkcore.common.datagen.PackJson;
import club.rainbowkitty.rkcore.common.datagen.PngAssets;

/**
 * The per-wood cart icons: a 16×16 sprite, a {@code minecraft:item/generated} model and an item
 * definition per wood, plus one item definition for the cart item itself.
 *
 * <p>Nothing here is the model of a registered item — there is only one cart item, and
 * {@code ChestMinecartItem#cargoModel} picks one of these per stack from the cargo it carries.
 *
 * <p>The art is a hand-authored overlay over each wood's planks, the same pipeline
 * {@link CargoCartTextureProvider} uses and with the same conventions — see
 * {@link CartIcons#composite}.
 *
 * <p>This overlay was previously derived rather than drawn, by diffing vanilla's
 * {@code item/chest_minecart.png} against {@code item/minecart.png}: vanilla ships the same cart
 * twice, so the chest's pixels are exactly the ones the two differ in, and a wash could be computed
 * from vanilla's own shading. That is no longer how the icons are drawn — the overlays are authored
 * now, and a derived one carries no cut-out colour for {@code composite} to read — so neither
 * vanilla icon is vendored any more. The reasoning is recorded in
 * {@code docs/plan/chest-minecarts.md}.
 */
public final class ChestMinecartTextureProvider implements DataProvider {
    // Both inputs live in the datagen-only source set, for the reasons ChestTextureProvider gives:
    // the planks and the overlay alike are client assets, not on the mod classpath.
    private static final String PLANK_DIR = "plankedchests_planks/";
    private static final String OVERLAY = "plankedchests_overlay/chest_minecart.png";

    private final PackOutput.PathProvider textures;
    private final PackOutput.PathProvider models;
    private final PackOutput.PathProvider items;

    /**
     * Initializes path providers for cart texture, model, and item definition outputs.
     *
     * @param output the pack output configuration
     */
    public ChestMinecartTextureProvider(FabricPackOutput output) {
        this.textures = output.createPathProvider(
                PackOutput.Target.RESOURCE_PACK, "textures/item");
        this.models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
        this.items = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ClassLoader loader = ChestMinecartTextureProvider.class.getClassLoader();
        BufferedImage overlay = CartIcons.sized(PngAssets.read(loader, OVERLAY), OVERLAY);

        List<CompletableFuture<?>> writes = new ArrayList<>();
        Map<Path, JsonObject> json = new HashMap<>();
        CartIcons.IconSink sink = new CartIcons.IconSink(
                this.textures, this.models, this.items, cache, writes, json);
        for (WoodType wood : WoodType.values()) {
            String planksFile = PLANK_DIR + wood.id() + "_planks.png";
            BufferedImage planks = CartIcons.sized(PngAssets.read(loader, planksFile), planksFile);
            sink.write(ChestModels.cartModel(wood.id()), planks, overlay,
                    "plankedChestsCartIcons");
        }

        // The cart item's own definition, which is not a per-wood icon and is needed whatever the
        // cargo: a client running this mod is sent the real item id rather than a vanilla fallback,
        // and an item with no definition of its own draws as a missing model on one. Vanilla's cart
        // icon is the right one for it, since every cargo without an icon here is drawn by vanilla.
        json.put(this.items.json(PlankedChests.id("chest_minecart")),
                PackJson.itemDefinition(
                        Identifier.withDefaultNamespace("item/chest_minecart")));

        writes.add(DataProvider.saveAll(cache, file -> file, path -> path, json));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Planked Chests Cart Icons";
    }

}
