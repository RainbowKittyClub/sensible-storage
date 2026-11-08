package club.rainbowkitty.sensiblestorage.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/** Writes the tables whose persistent grids this mod declares to FAWE. */
public final class CraftingTableTagsProvider extends FabricTagsProvider.BlockTagsProvider {
    /** Vanilla's table and every planked table, all sharing this mod's persistent grid. */
    public static final TagKey<Block> CRAFTING_TABLES =
            TagKey.create(Registries.BLOCK, SensibleStorage.id("crafting_tables"));

    /** Creates the crafting-table tag provider. */
    public CraftingTableTagsProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        TagAppender<Block> tables = builder(CRAFTING_TABLES).add(
                BuiltInRegistries.BLOCK.getResourceKey(Blocks.CRAFTING_TABLE).orElseThrow());
        for (WoodType wood : CraftingTableBlocks.tables().keySet()) {
            tables.addOptional(ResourceKey.create(
                    Registries.BLOCK, SensibleStorage.id(wood.craftingTableId())));
        }
    }

    @Override
    public String getName() {
        return "Sensible Storage Crafting Table Tag";
    }
}
