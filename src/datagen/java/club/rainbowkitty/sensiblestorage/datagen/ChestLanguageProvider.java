package club.rainbowkitty.sensiblestorage.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import net.minecraft.core.HolderLookup;

import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.block.ChestBlocks;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.cart.ChestCarts;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * English names for every chest and crafting table, e.g. "Dark Oak Chest", "Warped Trapped Chest",
 * "Birch Crafting Table", plus the three keys the minecarts need.
 *
 * <p>The carts need three and not thirty: one pattern that {@code kalic0re} fills with the cargo
 * block's own name, one for the plain-chest variant that has no wood to compose from, and the
 * entity type's description id. Vanilla's cart is renamed too, but that key belongs to the
 * {@code minecraft} namespace and so lives in a hand-written
 * {@code assets/minecraft/lang/en_us.json} rather than here.
 */
public final class ChestLanguageProvider extends FabricLanguageProvider {
    /**
     * Constructs a provider for generating English language translations.
     *
     * @param output the output for generated data
     * @param registriesFuture future providing registry access
     */
    public ChestLanguageProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider registries, TranslationBuilder builder) {
        builder.add(ChestBlocks.CREATIVE_TAB_TITLE_KEY, "Sensible Storage");
        builder.add(ChestBlocks.LARGE_CHEST_TITLE_KEY, "Large %s Chest");
        builder.add(ChestCarts.NAME_PATTERN_KEY, "Minecart with %s");
        builder.add(ChestCarts.PLAIN_NAME_KEY, ChestCarts.PLAIN_NAME_FALLBACK);
        builder.add(ChestCarts.type(), ChestCarts.PLAIN_NAME_FALLBACK);
        for (WoodType wood : WoodType.values()) {
            String name = wood.displayName();
            builder.add(ChestBlocks.chests().get(wood), name + " Chest");
            builder.add(ChestBlocks.trappedChests().get(wood), name + " Trapped Chest");
        }
        CraftingTableBlocks.tables().forEach(
                (wood, table) -> builder.add(table, wood.displayName() + " Crafting Table"));
        BookshelfBlocks.bookshelves().forEach(
                (wood, shelf) -> builder.add(shelf, wood.displayName() + " Bookshelf"));
    }
}
