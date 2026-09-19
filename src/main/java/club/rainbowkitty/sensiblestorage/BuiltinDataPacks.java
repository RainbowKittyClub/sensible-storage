package club.rainbowkitty.sensiblestorage;

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
     * Overrides the vanilla {@code trapped_chest} recipe into an unreachable one (see
     * {@code resourcepacks/disable_vanilla_recipes/}), a purely thematic call - a leftover vanilla
     * {@code minecraft:chest} (from loot, another mod, or an older save) could otherwise still make
     * a plain trapped chest sitting next to 27 planked ones. Off by default
     * ({@code /datapack enable} to turn it on). {@code minecraft:chest} itself needs no such
     * override: {@link club.rainbowkitty.sensiblestorage.recipe.MixedPlankChestRecipe} already
     * keeps it out of a uniform plank ring's way unconditionally, so this pack has nothing left to
     * do for it - see that class for why disabling it outright was never really optional there.
     * The identifier still reads {@code disable_vanilla_recipes} (plural) despite covering one
     * recipe now: renaming a built-in pack's id resets its enabled/disabled state on any world that
     * already recorded it, and this mod has been live since 2026-09-08.
     */
    public static final Identifier DISABLE_VANILLA_RECIPES =
            SensibleStorage.id("disable_vanilla_recipes");

    // Static-utility class; prevent instantiation.
    private BuiltinDataPacks() {}

    /** Registers every pack in this class. Call once from {@code onInitialize}. */
    public static void register() {
        Optional<ModContainer> self =
                FabricLoader.getInstance().getModContainer(SensibleStorage.MOD_ID);
        if (self.isEmpty()) {
            return;
        }
        ModContainer container = self.get();

        ResourceLoader.registerBuiltinPack(
                DISABLE_VANILLA_RECIPES, container,
                Component.literal("Sensible Storage: disable vanilla trapped chest recipe"),
                PackActivationType.NORMAL);
    }
}
