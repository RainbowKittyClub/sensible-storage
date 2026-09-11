package club.rainbowkitty.plankedchests.display;

import net.minecraft.resources.Identifier;

import club.rainbowkitty.plankedchests.PlankedChests;

/**
 * Naming for the two-part shulker box a cart draws, and for the vanilla sheet it is textured from.
 *
 * <p>The split is vanilla's, not an invention: {@code ShulkerModel.createShellMesh} is exactly two
 * cubes, a base and a lid, and {@code ShulkerBoxRenderer$ShulkerBoxModel.setupAnim} moves only the
 * lid. Splitting the display the same way is what lets a cart's box open.
 *
 * <p>Unlike a chest there is no per-type suffix — a shulker box is never half of anything — so a
 * cargo block's own path is the whole of the name.
 */
public final class ShulkerModels {
    /**
     * Vanilla's shulker sheets, {@code minecraft:entity/shulker/shulker[_<colour>]}.
     *
     * <p>These are the textures vanilla's own renderer uses, referenced rather than copied. They
     * live on the {@code shulker_boxes} atlas, which item models cannot read, so
     * {@code ShulkerAtlasProvider} adds each one to the block atlas as well.
     */
    private static final String SHEET_DIR = "entity/shulker/";

    private ShulkerModels() {
    }

    /** The static lower half, {@code plankedchests:<boxPath>_base}. */
    public static Identifier baseModel(String boxPath) {
        return PlankedChests.id(boxPath + "_base");
    }

    /** The lid, which rises and turns as the box opens: {@code plankedchests:<boxPath>_lid}. */
    public static Identifier lidModel(String boxPath) {
        return PlankedChests.id(boxPath + "_lid");
    }

    /**
     * The vanilla sprite a box is drawn with.
     *
     * <p>Derived from the block's own path rather than from a colour list: vanilla names the sheets
     * {@code shulker} and {@code shulker_<colour>} while the blocks are {@code shulker_box} and
     * {@code <colour>_shulker_box}, so the colour is the path with {@code _shulker_box} taken off —
     * and the undyed box, whose path is exactly {@code shulker_box}, leaves nothing behind and so
     * lands on the unsuffixed sheet.
     */
    public static Identifier sheet(String boxPath) {
        String colour = boxPath.equals("shulker_box")
                ? ""
                : "_" + boxPath.replace("_shulker_box", "");
        return Identifier.withDefaultNamespace(SHEET_DIR + "shulker" + colour);
    }
}
