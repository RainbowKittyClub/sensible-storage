package club.rainbowkitty.plankedchests.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Datagen entrypoint: per-wood chest textures, cart icons, blockstates, item-model definitions,
 * lang, the cart cargo tag, recipes and loot.
 */
public final class PlankedChestsDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(ChestTextureProvider::new);
        pack.addProvider(ChestMinecartTextureProvider::new);
        pack.addProvider(CargoCartTextureProvider::new);
        pack.addProvider(ChestBlockStateProvider::new);
        pack.addProvider(ChestElementModelProvider::new);
        pack.addProvider(ShulkerElementModelProvider::new);
        pack.addProvider(ShulkerAtlasProvider::new);
        pack.addProvider(ChestLanguageProvider::new);
        pack.addProvider(ChestCartCargoProvider::new);
        pack.addProvider(ChestRecipeProvider::new);
        pack.addProvider(ChestLootProvider::new);
    }
}
