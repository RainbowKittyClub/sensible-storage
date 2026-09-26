package club.rainbowkitty.sensiblestorage.datagen;

import java.util.Collection;
import java.util.List;
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
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Writes the {@code claimed} tags vanilla's two overridden recipes name, one per block: the planks
 * whose wood has a chest, or a crafting table, of its own here. A uniform grid of one of these goes
 * to that wood's recipe, and {@code PlankFallbackRecipe} steps aside for it; any other plank still
 * makes vanilla's block.
 *
 * <p>Generated rather than hand-written so each tag follows exactly the woods that have the block,
 * oak's missing table included, the same way {@link ChestCartCargoProvider}'s does.
 */
public final class ClaimedPlanksProvider extends FabricTagsProvider.ItemTagsProvider {
    // Named by data/minecraft/recipe/chest.json and crafting_table.json respectively.
    private static final TagKey<Item> CHEST =
            TagKey.create(Registries.ITEM, SensibleStorage.id("planks_with_chest"));
    private static final TagKey<Item> CRAFTING_TABLE =
            TagKey.create(Registries.ITEM, SensibleStorage.id("planks_with_crafting_table"));

    /**
     * Initializes the claimed-planks tag provider.
     *
     * @param output the datagen output
     * @param registriesFuture future containing the registry lookups
     */
    public ClaimedPlanksProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        claim(builder(CHEST), List.of(WoodType.values()));
        claim(builder(CRAFTING_TABLE), CraftingTableBlocks.tables().keySet());
    }

    @Override
    public String getName() {
        return "Sensible Storage Claimed Planks";
    }

    // Adds each wood's planks, optional for ChestCartCargoProvider's reason: a modded wood's planks
    // exist only where its mod does, and a required entry would fail the whole tag there.
    private static void claim(TagAppender<Item> tag, Collection<WoodType> woods) {
        for (WoodType wood : woods) {
            tag.addOptional(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(
                    wood.planksNamespace(), wood.id() + "_planks")));
        }
    }
}
