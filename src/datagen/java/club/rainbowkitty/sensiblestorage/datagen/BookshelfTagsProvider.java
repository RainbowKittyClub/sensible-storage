package club.rainbowkitty.sensiblestorage.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * The block tags that make a planked bookshelf behave as vanilla's does, beyond
 * {@code mineable/axe} ({@link AxeMineableProvider}): {@code minecraft:enchantment_power_provider},
 * which is how an enchanting table counts the shelves around it, and Fabric's
 * {@code c:bookshelves}, which lists vanilla's bookshelf and so should list these.
 *
 * <p>Every entry is optional, for {@link ChestCartCargoProvider}'s reason.
 */
public final class BookshelfTagsProvider extends FabricTagsProvider.BlockTagsProvider {
    /** Fabric's convention tag for bookshelves, which it fills with vanilla's. */
    public static final TagKey<Block> C_BOOKSHELVES =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "bookshelves"));

    /**
     * Initializes the bookshelf block tag provider.
     *
     * @param output the datagen output
     * @param registriesFuture future containing the registry lookups
     */
    public BookshelfTagsProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        TagAppender<Block> power = builder(BlockTags.ENCHANTMENT_POWER_PROVIDER);
        TagAppender<Block> convention = builder(C_BOOKSHELVES);
        for (WoodType wood : BookshelfBlocks.bookshelves().keySet()) {
            ResourceKey<Block> shelf =
                    ResourceKey.create(Registries.BLOCK, SensibleStorage.id(wood.bookshelfId()));
            power.addOptional(shelf);
            convention.addOptional(shelf);
        }
    }

    @Override
    public String getName() {
        return "Sensible Storage Bookshelf Block Tags";
    }
}
