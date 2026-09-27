package club.rainbowkitty.sensiblestorage.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.block.ChestBlocks;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Drop-self loot for every chest, copying a renamed chest's {@code custom_name} off the block
 * entity, as vanilla's {@code createNameableBlockEntityTable} does; plain drop-self for every
 * crafting table, as vanilla's own table has; and for every bookshelf, itself with silk touch and
 * otherwise three books, as vanilla's.
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
                output.dropSelf(table);
            }
            Block bookshelf = BookshelfBlocks.bookshelves().get(wood);
            if (bookshelf != null) {
                output.add(bookshelf, createSingleItemTableWithSilkTouch(
                        bookshelf, Items.BOOK, ConstantValue.exactly(3.0F)));
            }
        }
    }
}
