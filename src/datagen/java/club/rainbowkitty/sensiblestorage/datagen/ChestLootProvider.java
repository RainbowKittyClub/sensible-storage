package club.rainbowkitty.sensiblestorage.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.block.ChestBlocks;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Drop-self loot for every chest and crafting table, copying a renamed one's {@code custom_name}
 * off the block entity, as vanilla's {@code createNameableBlockEntityTable} does for a chest.
 * Vanilla's own crafting table gets the same, overriding its plain drop-self: every table now keeps
 * a grid, and with it a name. For every bookshelf, itself with silk touch and
 * otherwise three books; and for every chiseled bookshelf, itself with silk touch only, its books
 * spilling from the block entity — both as vanilla's.
 */
public final class ChestLootProvider extends FabricBlockLootSubProvider {
    /**
     * Constructs a loot table provider for plank chests.
     *
     * @param output the data pack output
     * @param registriesFuture the registries lookup
     */
    public ChestLootProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void generate() {
        add(Blocks.CRAFTING_TABLE, createNameableBlockEntityTable(Blocks.CRAFTING_TABLE));
        for (WoodType wood : WoodType.values()) {
            Block chest = ChestBlocks.chests().get(wood);
            Block trapped = ChestBlocks.trappedChests().get(wood);

            // Orphaned loot for an uninstalled mod's chest is harmless (nothing ever drops it),
            // but condition it anyway so its JSON stays consistent with the recipe it ships with.
            BlockLootSubProvider output = wood.requiredModId() == null
                    ? this
                    : withConditions(ResourceConditions.allModsLoaded(wood.requiredModId()));
            output.add(chest, createNameableBlockEntityTable(chest));
            output.add(trapped, createNameableBlockEntityTable(trapped));
            // No table or bookshelf for oak, whose are vanilla's own.
            Block table = CraftingTableBlocks.tables().get(wood);
            if (table != null) {
                output.add(table, createNameableBlockEntityTable(table));
            }
            Block bookshelf = BookshelfBlocks.bookshelves().get(wood);
            if (bookshelf != null) {
                output.add(bookshelf, createSingleItemTableWithSilkTouch(
                        bookshelf, Items.BOOK, ConstantValue.exactly(3.0F)));
            }
            Block chiseled = BookshelfBlocks.chiseledBookshelves().get(wood);
            if (chiseled != null) {
                output.dropWhenSilkTouch(chiseled);
            }
        }
    }
}
