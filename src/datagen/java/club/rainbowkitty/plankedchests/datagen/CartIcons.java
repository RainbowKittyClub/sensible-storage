package club.rainbowkitty.plankedchests.datagen;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.rkcore.common.datagen.PngAssets;

/**
 * What the cart-icon providers share: the icon's size, and the two tiny resource files every icon
 * needs beside the sprite composited for it.
 *
 * <p>Both providers draw the same cart carrying a different cargo, and both read a hand-authored
 * overlay from {@code plankedchests_overlay/}; all that differs is the texture underneath it —
 * a wood's planks for {@link ChestMinecartTextureProvider}, a vanilla block's own texture for
 * {@link CargoCartTextureProvider}. Everything after that point is identical, and lives here so
 * the two cannot drift.
 */
final class CartIcons {
    /** Edge of every cart icon, and of every texture composited into one. */
    static final int SIZE = 16;

    private CartIcons() {
    }

    /**
     * Checks one composite input is the icon's own size, by the file it was read from.
     *
     * <p>Magenta in an overlay is {@link PngAssets#CUTOUT} — how an icon gets the cart's silhouette
     * — which is worth knowing before editing one: no plank, shulker shell or barrel end contains
     * that tone, which is why it is free to mean "nothing here".
     */
    static BufferedImage sized(BufferedImage image, String name) {
        return PngAssets.sized(image, SIZE, name);
    }

    // {"parent":"minecraft:item/generated","textures":{"layer0":"<sprite>"}}
    static JsonObject generatedModel(Identifier sprite) {
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", sprite.toString());
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:item/generated");
        root.add("textures", textures);
        return root;
    }

    // Where one provider run puts its icons: the three pack paths it writes to, plus the
    // collections the run accumulates into. Built once per run, then handed each icon in turn.
    record IconSink(PackOutput.PathProvider textures, PackOutput.PathProvider models,
            PackOutput.PathProvider items, CachedOutput cache,
            List<CompletableFuture<?>> writes, Map<Path, JsonObject> json) {

        // Writes one icon: `base` with `overlay` laid over it as the texture, the generated model
        // pointing at that texture, and the item definition pointing at that model.
        void write(Identifier itemDefId, BufferedImage base, BufferedImage overlay,
                String saveName) {
            Identifier sprite = PlankedChests.id("item/" + itemDefId.getPath());
            this.writes.add(PngAssets.save(this.cache, this.textures.file(itemDefId, "png"),
                    PngAssets.composite(base, overlay), saveName));
            this.json.put(this.models.json(sprite), generatedModel(sprite));
            this.json.put(this.items.json(itemDefId), ElementModels.itemDefinition(sprite));
        }
    }
}
