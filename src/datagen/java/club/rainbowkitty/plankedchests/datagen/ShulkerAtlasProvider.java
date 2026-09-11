package club.rainbowkitty.plankedchests.datagen;

import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import club.rainbowkitty.plankedchests.display.ShulkerModels;

/**
 * Adds vanilla's seventeen shulker sheets to the <em>block</em> atlas, so that the item models a
 * cart's shulker box is drawn from can reach them.
 *
 * <p>The sheets live at {@code minecraft:entity/shulker/shulker[_<colour>]} and vanilla stitches
 * them onto its own {@code shulker_boxes} atlas, which only the special renderers read. An item
 * model can only name a sprite on the block atlas, so without this every part would draw as missing
 * texture. Copying the seventeen PNGs into this mod's pack would also work and is worse: it
 * redistributes vanilla art and freezes it, where a reference follows whatever resource pack the
 * player has on top.
 *
 * <p>Two things make this safe, and both are vanilla's own precedent rather than a trick.
 * {@code SpriteSourceList.load} walks {@code ResourceManager#getResourceStack} and appends every
 * pack's sources, so writing {@code assets/minecraft/atlases/blocks.json} <em>adds</em> to the
 * block atlas rather than replacing vanilla's. And vanilla already pulls entity textures onto
 * that atlas exactly this way — {@code entity/bell/bell_body} and
 * {@code entity/enchantment/enchanting_table_book} are both {@code minecraft:single} sources in its
 * own {@code blocks.json}.
 *
 * <p>A {@code minecraft:single} source with no {@code sprite} names the sprite after its
 * {@code resource} verbatim ({@code SingleFile#run}), so the models bind to the same identifier
 * {@link ShulkerModels#sheet} hands out and there is no second naming rule to keep in step.
 */
public final class ShulkerAtlasProvider implements DataProvider {
    private final PackOutput.PathProvider atlases;

    public ShulkerAtlasProvider(FabricPackOutput output) {
        this.atlases = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "atlases");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonArray sources = new JsonArray();
        source(sources, Blocks.SHULKER_BOX);
        Blocks.DYED_SHULKER_BOX.forEach(box -> source(sources, box));

        JsonObject root = new JsonObject();
        root.add("sources", sources);
        // The vanilla atlas id, deliberately: this file is merged into minecraft:blocks, not a new
        // atlas of our own, because that is the one an item model's sprites are looked up on.
        return DataProvider.saveStable(cache, root,
                this.atlases.json(Identifier.withDefaultNamespace("blocks")));
    }

    @Override
    public String getName() {
        return "Planked Chests Shulker Atlas";
    }

    // {"type":"minecraft:single","resource":"minecraft:entity/shulker/shulker_<colour>"}
    private static void source(JsonArray sources, Block box) {
        String boxPath = BuiltInRegistries.BLOCK.getKey(box).getPath();
        JsonObject single = new JsonObject();
        single.addProperty("type", "minecraft:single");
        single.addProperty("resource", ShulkerModels.sheet(boxPath).toString());
        sources.add(single);
    }
}
