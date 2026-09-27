package club.rainbowkitty.sensiblestorage.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * The item side of the bookshelves' tags: Fabric's {@code c:bookshelves}, which the lectern
 * override in {@code data/minecraft/recipe/lectern.json} takes in place of vanilla's bookshelf, so
 * any planked one makes a lectern.
 *
 * <p>Every entry is optional, for {@link ChestCartCargoProvider}'s reason.
 */
public final class BookshelfItemTagsProvider extends FabricTagsProvider.ItemTagsProvider {
    // Fabric's convention tag for bookshelves, which it fills with vanilla's.
    private static final TagKey<Item> C_BOOKSHELVES =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "bookshelves"));

    /**
     * Initializes the bookshelf item tag provider.
     *
     * @param output the datagen output
     * @param registriesFuture future containing the registry lookups
     */
    public BookshelfItemTagsProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        TagAppender<Item> convention = builder(C_BOOKSHELVES);
        for (WoodType wood : BookshelfBlocks.bookshelves().keySet()) {
            convention.addOptional(
                    ResourceKey.create(Registries.ITEM, SensibleStorage.id(wood.bookshelfId())));
        }
    }

    @Override
    public String getName() {
        return "Sensible Storage Bookshelf Item Tags";
    }
}
