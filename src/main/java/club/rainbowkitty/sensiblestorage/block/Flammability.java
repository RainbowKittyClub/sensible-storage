package club.rainbowkitty.sensiblestorage.block;

import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;

import net.minecraft.world.level.block.Block;

import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Fire and fuel for every planked block, as vanilla gives the block it stands in for, and as its
 * wood allows. Vanilla's entries name its own blocks ({@code FireBlock#bootStrap},
 * {@code FuelValues#vanillaBurnTimes}), so none reach these.
 *
 * <p>Every counterpart fuels a furnace for 1.5 items (the chest, trapped chest, crafting table,
 * bookshelf and chiseled bookshelf, {@code FuelValues.java:63-69}), so every planked block does.
 * A non-flammable wood's blocks are dropped again by vanilla itself: datagen lists them in
 * {@code minecraft:non_flammable_wood}, which vanilla removes from the fuel list after Fabric's
 * {@code BUILD} event adds them, as it does crimson's planks and shelves.
 *
 * <p>Of the counterparts only the bookshelf catches fire (30 to catch, 20 to burn,
 * {@code FireBlock.java:412}), so only the bookshelves do, and only a flammable wood's: vanilla
 * gives no crimson or warped block fire odds.
 */
public final class Flammability {
    private Flammability() {}

    /**
     * Registers fire odds and fuel values. Call after every planked block is registered:
     * {@code ChestBlocks}, {@code CraftingTableBlocks} and {@code BookshelfBlocks}.
     */
    public static void register() {
        FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
        BookshelfBlocks.bookshelves().forEach((wood, shelf) -> {
            if (wood.flammable()) {
                fire.add(shelf, 30, 20);
            }
        });

        FuelValueEvents.BUILD.register((builder, context) -> {
            // Vanilla's baseUnit * 3 / 2 for each counterpart.
            int burnTime = context.baseSmeltTime() * 3 / 2;
            for (Map<WoodType, ? extends Block> blocks : List.of(ChestBlocks.chests(),
                    ChestBlocks.trappedChests(), CraftingTableBlocks.tables(),
                    BookshelfBlocks.bookshelves(), BookshelfBlocks.chiseledBookshelves())) {
                blocks.values().forEach(block -> builder.add(block, burnTime));
            }
        });
    }
}
