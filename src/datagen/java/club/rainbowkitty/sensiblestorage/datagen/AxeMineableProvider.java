package club.rainbowkitty.sensiblestorage.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Adds every block this mod registers to {@code minecraft:mineable/axe}, as vanilla has its own
 * chests and crafting table: each wood's chest, trapped chest and crafting table.
 *
 * <p>The tag lives in the {@code minecraft} namespace because that is the tag the mining speed is
 * read from; one under this mod's own namespace would be a tag nothing reads. It used to be
 * hand-written, and fell behind when the modded woods arrived, which is why it follows
 * {@link WoodType} from here instead.
 */
public final class AxeMineableProvider extends FabricTagsProvider.BlockTagsProvider {
    /**
     * Initializes the axe-mineable tag provider.
     *
     * @param output the datagen output
     * @param registriesFuture future containing the registry lookups
     */
    public AxeMineableProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Optional for every wood, as in ChestCartCargoProvider: ChestBlocks#init skips a wood
        // whose mod is absent, and a required entry would fail the whole tag on that server.
        TagAppender<Block> axe = builder(BlockTags.MINEABLE_WITH_AXE);
        for (WoodType wood : WoodType.values()) {
            axe.addOptional(block(wood.chestId()));
            axe.addOptional(block(wood.trappedChestId()));
        }
        for (WoodType wood : CraftingTableBlocks.tables().keySet()) {
            axe.addOptional(block(wood.craftingTableId()));
        }
    }

    @Override
    public String getName() {
        return "Sensible Storage Axe Mineable";
    }

    // One of this mod's blocks, by registry path.
    private static ResourceKey<Block> block(String path) {
        return ResourceKey.create(Registries.BLOCK, SensibleStorage.id(path));
    }
}
