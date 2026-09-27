package club.rainbowkitty.sensiblestorage.datagen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.ChestBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Lists every planked item of a non-flammable wood in {@code minecraft:non_flammable_wood}, beside
 * that wood's own planks, so vanilla drops it from the fuel list as it does them
 * ({@code Flammability}).
 *
 * <p>Every entry is optional, for {@link ChestCartCargoProvider}'s reason.
 */
public final class NonFlammableWoodProvider extends FabricTagsProvider.ItemTagsProvider {
    /**
     * Initializes the non-flammable wood tag provider.
     *
     * @param output the datagen output
     * @param registriesFuture future containing the registry lookups
     */
    public NonFlammableWoodProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        TagAppender<Item> tag = builder(ItemTags.NON_FLAMMABLE_WOOD);
        for (WoodType wood : ChestBlocks.chests().keySet()) {
            if (wood.flammable()) {
                continue;
            }
            for (String path : List.of(wood.chestId(), wood.trappedChestId(),
                    wood.craftingTableId(), wood.bookshelfId(), wood.chiseledBookshelfId())) {
                tag.addOptional(ResourceKey.create(Registries.ITEM, SensibleStorage.id(path)));
            }
        }
    }

    @Override
    public String getName() {
        return "Sensible Storage Non-Flammable Wood Item Tags";
    }
}
