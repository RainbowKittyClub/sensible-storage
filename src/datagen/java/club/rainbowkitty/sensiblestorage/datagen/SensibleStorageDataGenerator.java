package club.rainbowkitty.sensiblestorage.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Datagen entrypoint: per-wood chest and crafting table textures, cart icons, blockstates, models,
 * item-model definitions, lang, the cart cargo, axe-mineable and claimed-planks tags, recipes and
 * loot.
 */
public final class SensibleStorageDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(ChestTextureProvider::new);
        pack.addProvider(CraftingTableTextureProvider::new);
        pack.addProvider(ChestMinecartTextureProvider::new);
        pack.addProvider(CargoCartTextureProvider::new);
        pack.addProvider(ChestBlockStateProvider::new);
        pack.addProvider(CraftingTableModelProvider::new);
        pack.addProvider(ChestElementModelProvider::new);
        pack.addProvider(ShulkerElementModelProvider::new);
        pack.addProvider(ShulkerAtlasProvider::new);
        pack.addProvider(ChestLanguageProvider::new);
        pack.addProvider(ChestCartCargoProvider::new);
        pack.addProvider(AxeMineableProvider::new);
        pack.addProvider(ClaimedPlanksProvider::new);
        pack.addProvider(ChestRecipeProvider::new);
        pack.addProvider(ChestLootProvider::new);
    }
}
