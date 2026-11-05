package club.rainbowkitty.sensiblestorage.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Writes {@link #PLANKED_CHESTS}: every chest and trapped chest this mod registers. Its
 * {@code fabric.mod.json} hands the tag to FastAsyncWorldEdit as the blocks whose lifecycle it
 * covers, so a world edit places and removes them through {@code setBlockState}, where their
 * display holders and block entities are made and dropped.
 *
 * <p>Generated for {@link ChestCartCargoProvider}'s reason: the list follows {@link WoodType}, and
 * every entry is optional because a wood whose mod is absent registers nothing.
 */
public final class ChestTagsProvider extends FabricTagsProvider.BlockTagsProvider {
    /** Every planked chest and trapped chest, of every wood. */
    public static final TagKey<Block> PLANKED_CHESTS =
            TagKey.create(Registries.BLOCK, SensibleStorage.id("planked_chests"));

    /**
     * Initializes the planked chest tag provider.
     *
     * @param output the datagen output
     * @param registriesFuture future containing the registry lookups
     */
    public ChestTagsProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        TagAppender<Block> chests = builder(PLANKED_CHESTS);
        for (WoodType wood : WoodType.values()) {
            chests.addOptional(ResourceKey.create(
                    Registries.BLOCK, SensibleStorage.id(wood.chestId())));
            chests.addOptional(ResourceKey.create(
                    Registries.BLOCK, SensibleStorage.id(wood.trappedChestId())));
        }
    }

    @Override
    public String getName() {
        return "Sensible Storage Planked Chest Tag";
    }
}
