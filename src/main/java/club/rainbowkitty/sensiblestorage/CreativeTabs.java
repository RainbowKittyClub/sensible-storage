package club.rainbowkitty.sensiblestorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.block.ChestBlocks;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.cart.ChestCarts;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Where this mod's items appear in the creative inventory: in each vanilla tab its vanilla
 * counterpart appears in, right after that counterpart, and all together in this mod's own tab.
 *
 * <p>The vanilla tabs reach a client running this mod, which builds them itself, and one with
 * Polymer, which syncs them. A vanilla client builds its creative screen from its own registry
 * and is sent nothing by {@code PolymerServerProtocol#sendCreativeSyncPackets}, which skips a
 * connection that did not negotiate the creative-tab protocol; it reaches this mod's items only
 * through the Polymer tab, by {@code /polymer creative}. The carts are craftable and
 * pick-block-able for that reason, rather than browsing being their only route.
 */
public final class CreativeTabs {
    /** Translation key for this mod's own tab's title. */
    public static final String TITLE_KEY = "itemGroup." + SensibleStorage.MOD_ID + ".chests";

    // CreativeModeTabs keeps its tab keys private, so the vanilla keys are rebuilt here.
    private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS =
            vanilla("functional_blocks");
    private static final ResourceKey<CreativeModeTab> REDSTONE_BLOCKS = vanilla("redstone_blocks");
    private static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES =
            vanilla("tools_and_utilities");

    private CreativeTabs() {}

    /**
     * Registers this mod's tab and its entries in vanilla's. Call after every block and item is
     * registered: {@code ChestBlocks}, {@code CraftingTableBlocks}, {@code BookshelfBlocks} and
     * {@code ChestCarts}.
     */
    public static void register() {
        CreativeModeTab tab = PolymerCreativeModeTabUtils.builder()
                .title(Component.translatable(TITLE_KEY))
                .icon(() -> new ItemStack(ChestBlocks.chests().get(WoodType.OAK)))
                .displayItems((params, output) -> {
                    ChestBlocks.chests().values().forEach(output::accept);
                    ChestBlocks.trappedChests().values().forEach(output::accept);
                    CraftingTableBlocks.tables().values().forEach(output::accept);
                    BookshelfBlocks.bookshelves().values().forEach(output::accept);
                    BookshelfBlocks.chiseledBookshelves().values().forEach(output::accept);
                    output.acceptAll(ChestCarts.carts());
                })
                .build();
        PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(
                SensibleStorage.id("chests"), tab);

        // Vanilla lists the chest and the chiseled bookshelf in both of these, the trapped chest in
        // Redstone Blocks only, and the crafting table and bookshelf in Functional Blocks only.
        CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_BLOCKS).register(output -> {
            insertAfter(output, Items.CRAFTING_TABLE, CraftingTableBlocks.tables().values());
            insertAfter(output, Items.BOOKSHELF, BookshelfBlocks.bookshelves().values());
            insertAfter(output, Items.CHISELED_BOOKSHELF,
                    BookshelfBlocks.chiseledBookshelves().values());
            insertAfter(output, Items.CHEST, ChestBlocks.chests().values());
        });
        CreativeModeTabEvents.modifyOutputEvent(REDSTONE_BLOCKS).register(output -> {
            insertAfter(output, Items.CHEST, ChestBlocks.chests().values());
            insertAfter(output, Items.CHISELED_BOOKSHELF,
                    BookshelfBlocks.chiseledBookshelves().values());
            insertAfter(output, Items.TRAPPED_CHEST, ChestBlocks.trappedChests().values());
            output.insertAfter(Items.CHEST_MINECART, ChestCarts.carts());
        });
        // And the chest minecart in Tools & Utilities as well as Redstone Blocks.
        CreativeModeTabEvents.modifyOutputEvent(TOOLS_AND_UTILITIES).register(output ->
                output.insertAfter(Items.CHEST_MINECART, ChestCarts.carts()));
    }

    // Inserts a set of this mod's items, in wood order, right after their vanilla counterpart.
    private static void insertAfter(FabricCreativeModeTabOutput output, ItemLike anchor,
            Collection<? extends ItemLike> items) {
        List<ItemStack> stacks = new ArrayList<>(items.size());
        items.forEach(item -> stacks.add(new ItemStack(item)));
        output.insertAfter(anchor, stacks);
    }

    // A vanilla tab's key.
    private static ResourceKey<CreativeModeTab> vanilla(String path) {
        return ResourceKey.create(
                Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace(path));
    }
}
