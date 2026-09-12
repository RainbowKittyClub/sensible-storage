package club.rainbowkitty.plankedchests;

import java.util.Optional;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Built-in resource packs bundled under {@code resourcepacks/}, registered as server data packs.
 */
public final class BuiltinDataPacks {
    /**
     * Overrides the vanilla {@code minecraft:chest}/{@code trapped_chest} recipes into unreachable
     * ones (see {@code resourcepacks/disable_vanilla_recipes/}), so the plank-ring shape only
     * yields this mod's per-wood chests. Disabling this data pack (e.g. {@code /datapack disable})
     * restores the vanilla recipes.
     */
    public static final Identifier DISABLE_VANILLA_RECIPES =
            PlankedChests.id("disable_vanilla_recipes");

    // Static-utility class; prevent instantiation.
    private BuiltinDataPacks() {}

    /** Registers every pack in this class. Call once from {@code onInitialize}. */
    public static void register() {
        Optional<ModContainer> self =
                FabricLoader.getInstance().getModContainer(PlankedChests.MOD_ID);
        if (self.isEmpty()) {
            return;
        }
        ModContainer container = self.get();

        ResourceLoader.registerBuiltinPack(
                DISABLE_VANILLA_RECIPES, container,
                Component.literal("Planked Chests: disable vanilla chest recipes"),
                PackActivationType.DEFAULT_ENABLED);
    }
}
