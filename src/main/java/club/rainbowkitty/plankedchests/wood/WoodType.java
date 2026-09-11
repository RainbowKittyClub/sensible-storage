package club.rainbowkitty.plankedchests.wood;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.StringRepresentable;

/**
 * The wood types that get a chest and a trapped chest: every vanilla wood with a {@code _planks}
 * block, plus one per modded wood from a mod this server runs. One source of truth for the
 * registration loop (gated per wood on {@link #requiredModId()}), datagen and the block-entity menu
 * title.
 */
public enum WoodType implements StringRepresentable {
    OAK(null),
    SPRUCE(null),
    BIRCH(null),
    JUNGLE(null),
    ACACIA(null),
    DARK_OAK(null),
    MANGROVE(null),
    CHERRY(null),
    PALE_OAK(null),
    BAMBOO(null),
    CRIMSON(null),
    WARPED(null),

    PINE("woods_and_mires"),

    REDWOOD("terrestria"),
    HEMLOCK("terrestria"),
    RUBBER("terrestria"),
    CYPRESS("terrestria"),
    WILLOW("terrestria"),
    JAPANESE_MAPLE("terrestria"),
    RAINBOW_EUCALYPTUS("terrestria"),
    SAKURA("terrestria"),
    YUCCA_PALM("terrestria"),

    CELESTIAL("enderscape"),
    MURUBLIGHT("enderscape"),
    VEILED("enderscape"),

    SCORCHED("cinderscapes"),
    UMBRAL("cinderscapes");

    /**
     * Serialises a wood as its {@link #id()}, for the cart item's wood component and the cart
     * entity's saved data. Enum order is not part of the format — a wood added or removed between
     * versions must not renumber the rest.
     */
    public static final StringRepresentable.EnumCodec<WoodType> CODEC =
            StringRepresentable.fromEnum(WoodType::values);

    private final String id = name().toLowerCase(Locale.ROOT);
    private final String chestId = id + "_chest";
    private final String trappedChestId = id + "_trapped_chest";
    private final String displayName = Stream.of(id.split("_"))
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
            .collect(Collectors.joining(" "));
    private final @Nullable String requiredModId;

    WoodType(@Nullable String requiredModId) {
        this.requiredModId = requiredModId;
    }

    /** The wood's lowercase id, e.g. {@code dark_oak}. */
    public String id() {
        return id;
    }

    /** The wood's lowercase id; {@link #CODEC} serialises to this. */
    @Override
    public String getSerializedName() {
        return id;
    }

    /** Registry path for this wood's chest block and item, e.g. {@code dark_oak_chest}. */
    public String chestId() {
        return chestId;
    }

    /** Registry path for this wood's trapped chest, e.g. {@code dark_oak_trapped_chest}. */
    public String trappedChestId() {
        return trappedChestId;
    }

    /** Title-cased wood name for display, e.g. {@code Dark Oak}. */
    public String displayName() {
        return displayName;
    }

    /** The mod this wood's planks come from, or {@code null} for a vanilla wood. */
    public @Nullable String requiredModId() {
        return requiredModId;
    }

    /** Namespace the {@code <id>_planks} block/item lives under: {@code requiredModId()}, or
     * {@code minecraft} for a vanilla wood. */
    public String planksNamespace() {
        return requiredModId == null ? "minecraft" : requiredModId;
    }
}
