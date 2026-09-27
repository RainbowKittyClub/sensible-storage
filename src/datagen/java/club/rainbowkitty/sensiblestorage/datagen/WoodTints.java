package club.rainbowkitty.sensiblestorage.datagen;

import java.awt.image.BufferedImage;

import club.rainbowkitty.kalic0re.common.datagen.PngAssets;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

// Reads each wood's planks and its median colour, and the gray the bookshelf overlays tint from.
// The overlays' grays are the Rec. 709 luma of vanilla's own colours, truncated, and vanilla's
// shelf frames are oak - so oak's median, at its own luma, is what every other gray scales from.
final class WoodTints {
    // Datagen-only copies of every wood's planks; see ChestTextureProvider for why they are copies.
    private static final String PLANK_DIR = "sensiblestorage_planks/";

    // The gray the overlays were drawn with for oak's median colour.
    private static final int OVERLAY_REFERENCE = 145;

    private WoodTints() {
    }

    // One wood's plank texture, which is 16x16 for every wood (modded-wood-types.md).
    static BufferedImage planks(ClassLoader loader, WoodType wood) {
        String path = PLANK_DIR + wood.id() + "_planks.png";
        return PngAssets.sized(PngAssets.read(loader, path), 16, path);
    }

    // The gray that stands for a wood's median in every overlay; fails if the oak planks changed.
    static int referenceGray(ClassLoader loader) {
        int luma = luma(PngAssets.medianColour(planks(loader, WoodType.OAK)));
        if (luma != OVERLAY_REFERENCE) {
            throw new IllegalStateException(
                    "Oak's median plank colour has luma %d, but the overlays were drawn against %d"
                            .formatted(luma, OVERLAY_REFERENCE));
        }
        return luma;
    }

    // A colour's Rec. 709 luma, truncated, as the overlays' grays were made.
    static int luma(int rgb) {
        return (int) (0.2126 * (rgb >> 16 & 0xFF) + 0.7152 * (rgb >> 8 & 0xFF)
                + 0.0722 * (rgb & 0xFF));
    }
}
