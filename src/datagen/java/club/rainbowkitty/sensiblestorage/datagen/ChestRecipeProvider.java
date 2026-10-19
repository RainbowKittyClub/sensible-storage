package club.rainbowkitty.sensiblestorage.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

//? if >=26.3
/*import net.minecraft.advancements.Advancement;*/
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.TransmuteRecipeBuilder;
//? if >=26.3
/*import net.minecraft.data.worldgen.BootstrapContext;*/
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.block.ChestBlocks;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.cart.ChestCarts;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Per-wood crafting recipes: a plank ring → chest (the vanilla chest recipe shape), a chest +
 * tripwire hook → trapped chest, a 2×2 plank square → crafting table, and planks around three books
 * → bookshelf. The table and the bookshelf have the same collision as the chest and the same fix,
 * {@code data/minecraft/recipe/crafting_table.json} and {@code bookshelf.json}, and all that
 * follows about the ring holds for them. Vanilla's own {@code minecraft:chest} recipe also matches
 * a uniform plank ring - {@code #minecraft:planks} takes any wood, not just this one's - so
 * {@code data/minecraft/recipe/chest.json} is a hand-written override naming a custom serializer,
 * {@link club.rainbowkitty.sensiblestorage.recipe.PlankFallbackRecipe}, that refuses any grid
 * another recipe here also matches; see that class for why a wood's own recipe cannot simply be
 * trusted to win a tie against vanilla's. Oak has no table or bookshelf, since vanilla's own are
 * already oak, so a uniform oak grid has nothing else to match and still makes vanilla's.
 * {@code trapped_chest} gets a plain, opt-in-only override instead ({@code disable_vanilla_recipes}
 * built-in data pack, {@code BuiltinDataPacks}, off by default) - it never collides with a wood's
 * own trapped-chest recipe (different items entirely), so disabling it is a look-and-feel choice,
 * not a correctness one. A modded wood's ring uses its
 * {@code data/sensiblestorage/tags/item/<wood>_planks.json} tag (a single, {@code required: false}
 * entry) instead of a direct item reference, since this mod is never a build dependency of the
 * wood's source mod - see {@code ChestBlocks#DATAGEN}.
 *
 * <p>Also one cart recipe per cargo — each of the 28 woods' chests and trapped chests, plus
 * vanilla's trapped chest, the barrel, the eight copper chests and the seventeen shulker boxes. Of
 * those, every cargo but this mod's own is one vanilla already draws in a cart, and so needs
 * nothing but the recipe. Vanilla's own {@code chest + minecart} is redirected to this mod's cart
 * too, but not from here: that one has to be written at vanilla's recipe id to replace it rather
 * than collide with it, and {@code FabricRecipeProvider#getRecipeIdentifier} rebuilds every id it
 * is given as {@code <modId>:<path>}. Overriding that would also move the generated advancement
 * onto vanilla's, so it is a hand-written
 * {@code src/main/resources/data/minecraft/recipe/chest_minecart.json} instead. It still names
 * {@code minecraft:chest} exactly rather than {@code #c:chests/wooden} (below): every wood already
 * has its own cart recipe with the correct cargo baked in, and widening this one to the tag would
 * let it also match a planked chest, racing that wood's own recipe for the same grid with no cargo
 * component set.
 *
 * <p>{@code data/c/tags/item/chests/wooden.json} lists every plain planked chest alongside
 * {@code minecraft:chest} (not the trapped chests — vanilla's own recipes never took one either),
 * under Fabric's own convention id for a wooden chest rather than one of this mod's own, so any
 * other mod already reading {@code c:chests/wooden} recognizes these too. Hand-written overrides
 * of vanilla's {@code hopper} and all ten {@code *_chest_boat}/{@code bamboo_chest_raft} recipes
 * (also under {@code data/minecraft/recipe/}) swap their {@code minecraft:chest} ingredient for
 * that tag. Unlike the cart, neither result is wood-specific to the chest half of the recipe, so
 * there is nothing for a tag-widened recipe to race.
 */
public final class ChestRecipeProvider extends FabricRecipeProvider {
    /**
     * Creates a new recipe provider for planked chests recipes.
     *
     * @param output the fabric pack output
     * @param registriesFuture a future providing access to registries
     */
    public ChestRecipeProvider(FabricPackOutput output,
            CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    // 26.3 builds recipes into registry bootstrap contexts rather than a RecipeOutput.
    @Override
    //? if >=26.3 {
    /*protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries,
            BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
        return new RecipeProvider(recipes, advancements) {
    *///?} else {
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries,
            RecipeOutput output) {
        return new RecipeProvider(registries, output) {
    //?}
            @Override
            public void buildRecipes() {
                for (WoodType wood : WoodType.values()) {
                    Block chest = ChestBlocks.chests().get(wood);
                    Block trapped = ChestBlocks.trappedChests().get(wood);
                    Block table = CraftingTableBlocks.tables().get(wood);
                    Block bookshelf = BookshelfBlocks.bookshelves().get(wood);
                    Block chiseled = BookshelfBlocks.chiseledBookshelves().get(wood);

                    // The recipes' *outputs* (the blocks above) still need conditioning
                    // though the planks ingredient below no longer does: ChestBlocks#DATAGEN
                    // registers every wood's block for this pass regardless, but on a real server
                    // missing this wood's mod, ChestBlocks#init skips it - so the block this recipe
                    // names as its result would not exist there.
                    RecipeOutput output = wood.requiredModId() == null
                            ? this.output
                            : withConditions(this.output,
                                    ResourceConditions.allModsLoaded(wood.requiredModId()));

                    withPlanks(this.shaped(RecipeCategory.DECORATIONS, chest)
                            .pattern("###")
                            .pattern("# #")
                            .pattern("###"), wood)
                            .save(output);

                    // No table or bookshelf for oak, whose are vanilla's own: with nothing else
                    // to match, PlankFallbackRecipe leaves an oak grid to them.
                    if (table != null) {
                        withPlanks(this.shaped(RecipeCategory.DECORATIONS, table)
                                .pattern("##")
                                .pattern("##"), wood)
                                .save(output);
                    }
                    if (bookshelf != null) {
                        withPlanks(this.shaped(RecipeCategory.BUILDING_BLOCKS, bookshelf)
                                .define('X', Items.BOOK)
                                .pattern("###")
                                .pattern("XXX")
                                .pattern("###"), wood)
                                .save(output);
                    }
                    // The wood's own slabs, not any: another wood's leaves the grid to vanilla.
                    if (chiseled != null) {
                        withSlabs(withPlanks(this.shaped(RecipeCategory.BUILDING_BLOCKS, chiseled)
                                .pattern("###")
                                .pattern("XXX")
                                .pattern("###"), wood), wood)
                                .save(output);
                    }

                    this.shapeless(RecipeCategory.REDSTONE, trapped)
                            .requires(chest)
                            .requires(Items.TRIPWIRE_HOOK)
                            .unlockedBy(getHasName(chest), this.has(chest))
                            .save(output);

                    // One item for every wood, so the wood travels in the result's components
                    // instead of in 28 registry entries. The vanilla chest_minecart recipe names
                    // minecraft:chest exactly rather than a tag, so none of these collide with it.
                    cartRecipe(chest, output);
                    cartRecipe(trapped, output);
                }

                // The vanilla cargoes, which need no gate: every one of them is in every build.
                cartRecipe(Blocks.TRAPPED_CHEST, this.output);
                cartRecipe(Blocks.BARREL, this.output);
                Blocks.COPPER_CHEST.forEach(chest -> cartRecipe(chest, this.output));
                keepingContents(Blocks.SHULKER_BOX, this.output);
                Blocks.DYED_SHULKER_BOX.forEach(box -> keepingContents(box, this.output));
            }

            // Keys '#' to one wood's planks, and unlocks the recipe on holding them.
            private ShapedRecipeBuilder withPlanks(ShapedRecipeBuilder recipe, WoodType wood) {
                if (wood.requiredModId() == null) {
                    Item planks = BuiltInRegistries.ITEM.getValueOrThrow(ResourceKey.create(
                            Registries.ITEM, Identifier.withDefaultNamespace(
                                    wood.id() + "_planks")));
                    return recipe.define('#', planks)
                            .unlockedBy(getHasName(planks), this.has(planks));
                }
                TagKey<Item> planks = TagKey.create(
                        Registries.ITEM, SensibleStorage.id(wood.id() + "_planks"));
                return recipe.define('#', planks)
                        .unlockedBy("has_" + wood.id() + "_planks", this.has(planks));
            }

            // Keys 'X' to one wood's slab: the item for a vanilla wood, and for a modded one the
            // one-entry tag BookshelfItemTagsProvider writes, as the planks have.
            private ShapedRecipeBuilder withSlabs(ShapedRecipeBuilder recipe, WoodType wood) {
                if (wood.requiredModId() == null) {
                    return recipe.define('X', BuiltInRegistries.ITEM.getValueOrThrow(
                            ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace(
                                    wood.id() + "_slab"))));
                }
                return recipe.define('X', BookshelfItemTagsProvider.slabs(wood));
            }

            // A cart carrying one cargo, crafted from that block and a minecart. The cargo rides in
            // the result's components, so all of these share the one registered item - which is why
            // the id is given rather than derived: RecipeBuilder would name every one of them after
            // that item and only the last would survive.
            private void cartRecipe(Block cargo, RecipeOutput output) {
                this.shapeless(RecipeCategory.TRANSPORTATION, cartResult(cargo))
                        .requires(cargo)
                        .requires(Items.MINECART)
                        .unlockedBy(getHasName(cargo), this.has(cargo))
                        .save(output, cartRecipeId(cargo));
            }

            // The same recipe for a cargo that carries its contents on the item, which of every
            // cargo this cart accepts is the shulker boxes alone. A shapeless recipe builds its
            // result from the recipe and nothing else, so crafting a full box into a cart would
            // destroy what was in it; transmute keeps the input's components, and
            // CargoMinecartChest empties the container it arrives in into the cart's own slots.
            // Vanilla reaches for the same recipe type for the same reason - every shulker box dye
            // recipe is one.
            private void keepingContents(Block cargo, RecipeOutput output) {
                TransmuteRecipeBuilder.transmute(RecipeCategory.TRANSPORTATION,
                                Ingredient.of(cargo), Ingredient.of(Items.MINECART),
                                cartResult(cargo))
                        .unlockedBy(getHasName(cargo), this.has(cargo))
                        .save(output, cartRecipeId(cargo));
            }
        };
    }

    // The cart item with one cargo baked in: one item id, one result per cargo.
    private static ItemStackTemplate cartResult(Block cargo) {
        return new ItemStackTemplate(ChestCarts.item().builtInRegistryHolder(), 1,
                DataComponentPatch.builder()
                        .set(ChestCarts.cargoComponent(), cargo.builtInRegistryHolder())
                        .build());
    }

    // "<cargo's own path>_minecart", which for a wood's chest is the "<wood>_chest_minecart" these
    // recipes have always been written at. Always in this mod's namespace, even for a vanilla
    // cargo: the recipe is this mod's, and FabricRecipeProvider would rewrite it there regardless.
    private static ResourceKey<Recipe<?>> cartRecipeId(Block cargo) {
        String path = Ids.blockPath(cargo);
        return ResourceKey.create(Registries.RECIPE, SensibleStorage.id(path + "_minecart"));
    }

    @Override
    public String getName() {
        return "Sensible Storage Recipes";
    }
}
