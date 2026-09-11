package club.rainbowkitty.plankedchests.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;

import net.minecraft.core.HolderLookup;

import club.rainbowkitty.plankedchests.block.ChestBlocks;
import club.rainbowkitty.plankedchests.cart.ChestCarts;
import club.rainbowkitty.plankedchests.wood.WoodType;

/**
 * English names for every chest, e.g. "Dark Oak Chest", "Warped Trapped Chest", plus the three
 * keys the minecarts need.
 *
 * <p>The carts need three and not thirty: one pattern that {@code rkcore} fills with the cargo
 * block's own name, one for the plain-chest variant that has no wood to compose from, and the
 * entity type's description id. Vanilla's cart is renamed too, but that key belongs to the
 * {@code minecraft} namespace and so lives in a hand-written
 * {@code assets/minecraft/lang/en_us.json} rather than here.
 */
public final class ChestLanguageProvider extends FabricLanguageProvider {
    public ChestLanguageProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider registries, TranslationBuilder builder) {
        builder.add(ChestBlocks.CREATIVE_TAB_TITLE_KEY, "Planked Chests");
        builder.add(ChestBlocks.LARGE_CHEST_TITLE_KEY, "Large %s Chest");
        builder.add(ChestCarts.NAME_PATTERN_KEY, "Minecart with %s");
        builder.add(ChestCarts.PLAIN_NAME_KEY, ChestCarts.PLAIN_NAME_FALLBACK);
        builder.add(ChestCarts.type(), ChestCarts.PLAIN_NAME_FALLBACK);
        for (WoodType wood : WoodType.values()) {
            String name = wood.displayName();
            builder.add(ChestBlocks.chests().get(wood), name + " Chest");
            builder.add(ChestBlocks.trappedChests().get(wood), name + " Trapped Chest");
        }
    }
}
