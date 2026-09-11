package club.rainbowkitty.plankedchests.display;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.minecraft.world.level.block.state.properties.ChestType;

import club.rainbowkitty.plankedchests.PlankedChests;

/**
 * Naming for the per-wood chest models, item definitions, and sprites. The display entity is two
 * parts — a static base and a lid rotated about its hinge each tick (see {@code ChestElementHolder}
 * and {@code ChestElementModelProvider}) — so each {@link ChestType} has one base and one lid item.
 */
public final class ChestModels {
    /**
     * Variant base for the vanilla-textured chest — the one chest here not built from a wood's
     * planks. It exists only as a minecart's cargo, so that the plain chest look is still available
     * to a cart whose lid animates; no block, item or wood corresponds to it.
     */
    public static final String VANILLA = "vanilla";

    /** Chest id for {@link #VANILLA}, which names its base and lid models. */
    public static final String VANILLA_CHEST_ID = chestId(VANILLA);

    /**
     * Variant base per copper weather state, each named after the vanilla sheet it is drawn from.
     * Like {@link #VANILLA} these exist only as a minecart's cargo.
     *
     * <p>Built with {@code same} because a waxed copper chest is the same metal as its unwaxed twin
     * and vanilla draws the pair from one sheet — so this maps all eight blocks onto four variants,
     * without a second list of names to keep in step.
     */
    public static final WeatheringCopperCollection<String> COPPER =
            WeatheringCopperCollection.same(new WeatheringCopperCollection.ByState<>(
                    "copper", "copper_exposed", "copper_weathered", "copper_oxidized"));

    // Sprite/model-id suffix per ChestType, indexed by ordinal (SINGLE, LEFT, RIGHT).
    private static final String[] SUFFIX = {"", "_left", "_right"};

    private ChestModels() {}

    /** The chest id a variant base's base and lid models are named from. */
    public static String chestId(String variantBase) {
        return variantBase + "_chest";
    }

    private static String suffix(ChestType type) {
        return SUFFIX[type.ordinal()];
    }

    /**
     * Sprite name (under {@code textures/block/chest/}) for one wood variant and chest type, e.g.
     * {@code birch}, {@code birch_left}, {@code birch_trapped_right}.
     *
     * @param variantBase {@code wood.id()} for a plain chest, {@code wood.id() + "_trapped"} for a
     *     trapped one
     */
    public static String sprite(String variantBase, ChestType type) {
        return variantBase + suffix(type);
    }

    /**
     * Base-part item-model definition id for a chest of the given type.
     *
     * @param chestId {@code wood.chestId()} or {@code wood.trappedChestId()}
     */
    public static Identifier baseModel(String chestId, ChestType type) {
        return PlankedChests.id(chestId + suffix(type) + "_base");
    }

    /**
     * Lid-part item-model definition id for a chest of the given type. Authored in its closed
     * position; {@code ChestElementHolder} rotates it about the hinge each tick.
     *
     * @param chestId {@code wood.chestId()} or {@code wood.trappedChestId()}
     */
    public static Identifier lidModel(String chestId, ChestType type) {
        return PlankedChests.id(chestId + suffix(type) + "_lid");
    }

    /**
     * Whole, closed-chest item-model definition id — the {@link ChestType#SINGLE} base and lid
     * combined into one static model, for the inventory/held-item icon (which never animates or
     * shows a half).
     *
     * @param chestId {@code wood.chestId()} or {@code wood.trappedChestId()}
     */
    public static Identifier wholeModel(String chestId) {
        return PlankedChests.id(chestId + "_whole");
    }

    /**
     * Item-model definition id for the icon of a cart carrying a wood's chest — a flat sprite, not
     * one of the chest models above, matching vanilla's own choice for {@code chest_minecart}.
     *
     * <p>Named from the wood rather than from a chest id because there is no trapped variant to
     * tell apart: a cart carries only the plain chest. No registered item has this as its model —
     * there is one cart item for every wood and {@code ChestMinecartItem} picks per stack.
     *
     * @param woodId {@code wood.id()}
     */
    public static Identifier cartModel(String woodId) {
        return cargoCartModel(woodId + "_chest");
    }

    /**
     * Item-model definition id for the icon of a cart carrying any cargo, named from that cargo
     * block's own path — {@code shulker_box} gives {@code plankedchests:shulker_box_minecart}.
     *
     * <p>The same rule vanilla names {@code chest_minecart} by, and the same one
     * {@code ChestRecipeProvider} names the cart recipes by, so a cargo's recipe and its icon are
     * always called the same thing. {@link #cartModel} is this with a wood's chest id filled in.
     *
     * @param cargoPath the cargo block's registry path
     */
    public static Identifier cargoCartModel(String cargoPath) {
        return PlankedChests.id(cargoPath + "_minecart");
    }
}
