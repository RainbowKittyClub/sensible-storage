package club.rainbowkitty.sensiblestorage.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

import club.rainbowkitty.kalic0re.common.datagen.SpecialModelExtentsProvider;

/**
 * Datagen entrypoint: per-wood chest, crafting table and both bookshelves' textures, cart icons,
 * blockstates, models, item-model definitions, lang, the cart cargo, axe-mineable and bookshelf
 * tags, recipes and loot. Also the measurements of vanilla's special item models, which the
 * crafting table grid's display positions items by.
 */
public final class SensibleStorageDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(ChestTextureProvider::new);
        pack.addProvider(CraftingTableTextureProvider::new);
        pack.addProvider(PlankSpriteProvider::new);
        pack.addProvider(BookshelfTextureProvider::new);
        pack.addProvider(ChiseledBookshelfTextureProvider::new);
        pack.addProvider(ChestMinecartTextureProvider::new);
        pack.addProvider(CargoCartTextureProvider::new);
        pack.addProvider(ChestBlockStateProvider::new);
        pack.addProvider(CraftingTableModelProvider::new);
        pack.addProvider(BookshelfModelProvider::new);
        pack.addProvider(ChiseledBookshelfModelProvider::new);
        pack.addProvider(ChestElementModelProvider::new);
        pack.addProvider(ShulkerElementModelProvider::new);
        pack.addProvider(ShulkerAtlasProvider::new);
        pack.addProvider(ChestLanguageProvider::new);
        pack.addProvider(ChestCartCargoProvider::new);
        pack.addProvider(AxeMineableProvider::new);
        pack.addProvider(BookshelfTagsProvider::new);
        pack.addProvider(BookshelfItemTagsProvider::new);
        pack.addProvider(NonFlammableWoodProvider::new);
        pack.addProvider(ChestRecipeProvider::new);
        pack.addProvider(ChestLootProvider::new);
        pack.addProvider(SpecialModelExtentsProvider::new);
    }
}
