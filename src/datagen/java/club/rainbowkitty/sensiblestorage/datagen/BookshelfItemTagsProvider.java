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
 * any planked one makes a lectern; and per modded wood a one-entry
 * {@code sensiblestorage:<wood>_slabs}, which the chiseled bookshelf's recipe takes, since this mod
 * cannot name a modded slab item at build time (the planks' hand-written tags do the same).
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

    /** A modded wood's one-entry slab tag, e.g. {@code sensiblestorage:redwood_slabs}. */
    public static TagKey<Item> slabs(WoodType wood) {
        return TagKey.create(Registries.ITEM, SensibleStorage.id(wood.id() + "_slabs"));
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        TagAppender<Item> convention = builder(C_BOOKSHELVES);
        for (WoodType wood : BookshelfBlocks.bookshelves().keySet()) {
            convention.addOptional(
                    ResourceKey.create(Registries.ITEM, SensibleStorage.id(wood.bookshelfId())));
        }
        for (WoodType wood : WoodType.values()) {
            if (wood.requiredModId() != null) {
                builder(slabs(wood)).addOptional(ResourceKey.create(Registries.ITEM, Identifier
                        .fromNamespaceAndPath(wood.planksNamespace(), wood.id() + "_slab")));
            }
        }
    }

    @Override
    public String getName() {
        return "Sensible Storage Bookshelf Item Tags";
    }
}
