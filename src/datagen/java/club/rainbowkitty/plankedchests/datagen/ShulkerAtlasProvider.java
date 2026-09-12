package club.rainbowkitty.plankedchests.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import club.rainbowkitty.plankedchests.display.ShulkerModels;
import club.rainbowkitty.rkcore.common.datagen.AtlasSources;

/**
 * Adds vanilla's seventeen shulker sheets to the <em>block</em> atlas, so that the item models a
 * cart's shulker box is drawn from can reach them.
 *
 * <p>The sheets live at {@code minecraft:entity/shulker/shulker[_<colour>]} and vanilla stitches
 * them onto its own {@code shulker_boxes} atlas, which only the special renderers read. An item
 * model can only name a sprite on the block atlas, so without this every part would draw as missing
 * texture.
 *
 * <p>Why adding to a vanilla atlas is safe, and why the sprite ends up named after the texture it
 * came from, is on {@code AtlasSources} — including that the sprite names match what
 * {@link ShulkerModels#sheet} hands out, so there is no second naming rule to keep in step.
 */
public final class ShulkerAtlasProvider implements DataProvider {
    private final PackOutput.PathProvider atlases;

    /**
     * Creates a provider that generates the shulker atlas sources file.
     *
     * @param output the fabric pack output
     */
    public ShulkerAtlasProvider(FabricPackOutput output) {
        this.atlases = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "atlases");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<Identifier> sheets = new ArrayList<>();
        sheets.add(sheet(Blocks.SHULKER_BOX));
        Blocks.DYED_SHULKER_BOX.forEach(box -> sheets.add(sheet(box)));

        return DataProvider.saveStable(cache, AtlasSources.singleFiles(sheets),
                this.atlases.json(AtlasSources.BLOCK_ATLAS));
    }

    @Override
    public String getName() {
        return "Planked Chests Shulker Atlas";
    }

    // Vanilla's sheet for one shulker box, e.g. minecraft:entity/shulker/shulker_red.
    private static Identifier sheet(Block box) {
        return ShulkerModels.sheet(Ids.blockPath(box));
    }
}
