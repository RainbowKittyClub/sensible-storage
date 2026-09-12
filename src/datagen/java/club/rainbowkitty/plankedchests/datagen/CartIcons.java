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
 * The pixel and JSON work the cart-icon providers share: laying a wash overlay over the texture the
 * cargo is read through, and the two tiny resource files every icon needs beside its sprite.
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

    // The overlays' chroma key: magenta means "nothing here", and is how an icon gets the cart's
    // silhouette. It has to be a colour rather than transparency because transparency already means
    // something else — see composite — and magenta is the conventional choice for the job, being a
    // tone no plank, shulker shell or barrel end contains.
    private static final int CUTOUT = 0xFF00FF;

    private CartIcons() {
    }

    /**
     * Lays an overlay over the texture the cargo is read through.
     *
     * <p>Straight source-over, so the base shows wherever the overlay does not cover it: fully
     * transparent overlay is the cargo at its own colour, fully opaque is the overlay's own pixel
     * (the cart itself, and the cargo's trim), and anything between is shading over the cargo —
     * black or white at the strength that reproduces the intended contrast over whatever is
     * underneath.
     *
     * <p>The icon is not trimmed to the overlay's shape. Everything outside the cart comes from the
     * overlay painting {@link #CUTOUT} there, which is the one thing source-over does not express,
     * and is read off the overlay rather than off the result so that a base which happened to
     * contain the same magenta could not punch holes in itself.
     *
     * @param base the texture the cargo shows as, e.g. a wood's planks or a shulker box's shell
     * @param overlay the overlay, which must be {@link #SIZE} square like the base
     */
    static BufferedImage composite(BufferedImage base, BufferedImage overlay) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int top = overlay.getRGB(x, y);
                boolean cutout = (top >>> 24) != 0 && (top & 0xFFFFFF) == CUTOUT;
                out.setRGB(x, y, cutout ? 0 : over(base.getRGB(x, y), top));
            }
        }
        return out;
    }

    /**
     * Checks one composite input is the icon's own size.
     *
     * <p>Both inputs are indexed pixel by pixel against the icon's 16×16, so a source that is not
     * that size reads off the end of itself. Worth saying which file rather than letting an
     * {@link ArrayIndexOutOfBoundsException} stand in for it: the overlays are hand-edited, and
     * resaving one at the wrong size is the likeliest way this ever breaks.
     */
    static BufferedImage sized(BufferedImage image, String name) {
        if (image.getWidth() != SIZE || image.getHeight() != SIZE) {
            throw new IllegalStateException("%s must be %d×%d, but is %d×%d".formatted(
                    name, SIZE, SIZE, image.getWidth(), image.getHeight()));
        }
        return image;
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

    // One pixel of source-over: `top` composited onto `base`, both premultiplied out again so the
    // result is a plain ARGB pixel. Handles the fully transparent and fully opaque ends without
    // special cases, which is why composite has none.
    private static int over(int base, int top) {
        double topAlpha = (top >>> 24) / 255.0;
        double baseAlpha = (base >>> 24) / 255.0;
        double alpha = topAlpha + baseAlpha * (1.0 - topAlpha);
        if (alpha <= 0.0) {
            return 0;
        }
        int out = (int) Math.round(alpha * 255.0) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double mixed = ((top >> shift & 0xFF) * topAlpha
                    + (base >> shift & 0xFF) * baseAlpha * (1.0 - topAlpha)) / alpha;
            out |= (int) Math.round(mixed) << shift;
        }
        return out;
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
                    composite(base, overlay), saveName));
            this.json.put(this.models.json(sprite), generatedModel(sprite));
            this.json.put(this.items.json(itemDefId), ElementModels.itemDefinition(sprite));
        }
    }
}
