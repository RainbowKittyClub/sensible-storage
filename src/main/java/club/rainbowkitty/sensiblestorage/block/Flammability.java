package club.rainbowkitty.sensiblestorage.block;

import java.util.List;
import java.util.Map;

//? if >=26.3 {
/*import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
*///?}
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
//? if <26.3
import net.fabricmc.fabric.api.registry.FuelValueEvents;

//? if >=26.3 {
/*import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CookingFuel;
*///?}
import net.minecraft.world.level.block.Block;
//? if >=26.3 {
/*import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
*///?}

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
 * {@code BUILD} event adds them, as it does crimson's planks and shelves. 26.3 makes fuel an item
 * component that vanilla sets per item and leaves off crimson and warped, so there only a
 * flammable wood's items get it.
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

        //? if >=26.3 {
        /*DefaultItemComponentEvents.MODIFY.register(context -> {
            // Vanilla's Item.Properties#cookingFuel for each counterpart.
            CookingFuel fuel = new CookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS,
                    ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER);
            for (Map<WoodType, ? extends Block> blocks : List.of(ChestBlocks.chests(),
                    ChestBlocks.trappedChests(), CraftingTableBlocks.tables(),
                    BookshelfBlocks.bookshelves(), BookshelfBlocks.chiseledBookshelves())) {
                blocks.forEach((wood, block) -> {
                    if (wood.flammable()) {
                        context.modify(block.asItem(),
                                builder -> builder.set(DataComponents.COOKING_FUEL, fuel));
                    }
                });
            }
        });
        *///?} else {
        FuelValueEvents.BUILD.register((builder, context) -> {
            // Vanilla's baseUnit * 3 / 2 for each counterpart.
            int burnTime = context.baseSmeltTime() * 3 / 2;
            for (Map<WoodType, ? extends Block> blocks : List.of(ChestBlocks.chests(),
                    ChestBlocks.trappedChests(), CraftingTableBlocks.tables(),
                    BookshelfBlocks.bookshelves(), BookshelfBlocks.chiseledBookshelves())) {
                blocks.values().forEach(block -> builder.add(block, burnTime));
            }
        });
        //?}
    }
}
