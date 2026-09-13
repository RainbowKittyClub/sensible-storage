package club.rainbowkitty.plankedchests.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.references.BlockItemIds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.cart.ChestCarts;
import club.rainbowkitty.plankedchests.wood.WoodType;

/**
 * Writes {@link ChestCarts#CARGO_TAG}: the blocks a chest minecart will carry.
 *
 * <p>A chest minecart is a chest's inventory however it is drawn, so the list is the blocks for
 * which that is not a lie — the containers that are a chest in all but name. Vanilla's chest and
 * trapped chest, the barrel, the eight copper chests, the seventeen shulker boxes, and this mod's
 * own per-wood chests and trapped chests. The two vanilla groups go in as tag references rather
 * than as twenty-five ids, so a future weathering state or dye colour arrives on its own.
 *
 * <p>Generated rather than hand-written because the per-wood half of it follows {@link WoodType},
 * which the hand-written {@code minecraft:mineable/axe} does not and has drifted from.
 *
 * <p>Not in the list, and each for its own reason:
 *
 * <ul>
 *   <li><b>Ender chests.</b> A shared inventory that is not this cart's 27 slots; a cart carrying
 *       one would draw an ender chest and open a perfectly ordinary chest instead.
 *   <li><b>Hoppers, droppers, dispensers, furnaces.</b> Containers, but not of this shape and not
 *       of this size, and every one of them is a vanilla cart or a block whose own behaviour a cart
 *       would fail to reproduce. {@code /summon} with a furnace is the case that raised the guard.
 * </ul>
 */
public final class ChestCartCargoProvider extends FabricTagsProvider.BlockTagsProvider {

    /**
     * Initializes the cargo tag provider.
     *
     * @param output the datagen output
     * @param registriesFuture future containing the registry lookups
     */
    public ChestCartCargoProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Optional tags, not required ones. TagsProvider resolves every required reference against
        // the tags this provider itself defines, and a vanilla tag is not among them - there is no
        // parent provider to hand it, so a required #minecraft: reference fails datagen outright.
        // Both of these are BlockTags constants rather than typed ids, so nothing is being waved
        // through: the compiler has already checked they exist.
        TagAppender<Block> cargo = builder(ChestCarts.CARGO_TAG)
                .add(BlockItemIds.CHEST, BlockItemIds.TRAPPED_CHEST, BlockItemIds.BARREL)
                .addOptionalTag(BlockTags.COPPER_CHESTS)
                .addOptionalTag(BlockTags.SHULKER_BOXES);

        // Optional for every wood, not just the modded ones. ChestBlocks#init skips a wood whose
        // mod is absent, so a required entry would fail tag loading on exactly the server this mod
        // is built to run on; and a vanilla wood is only unconditional because nothing has ever
        // removed one, which is not a promise worth writing into a tag.
        for (WoodType wood : WoodType.values()) {
            cargo.addOptional(ResourceKey.create(
                    Registries.BLOCK, PlankedChests.id(wood.chestId())));
            cargo.addOptional(ResourceKey.create(
                    Registries.BLOCK, PlankedChests.id(wood.trappedChestId())));
        }
    }

    @Override
    public String getName() {
        return "Planked Chests Cart Cargo";
    }
}
