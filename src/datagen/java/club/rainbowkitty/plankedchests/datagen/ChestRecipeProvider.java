package club.rainbowkitty.plankedchests.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.TransmuteRecipeBuilder;
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

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.block.ChestBlocks;
import club.rainbowkitty.plankedchests.cart.ChestCarts;
import club.rainbowkitty.plankedchests.wood.WoodType;

/**
 * Per-wood crafting recipes: a plank ring → chest (the vanilla chest recipe shape) and a chest +
 * tripwire hook → trapped chest. The vanilla {@code minecraft:chest} / {@code trapped_chest}
 * recipes, which also match a uniform plank ring, are suppressed by hand-written overrides in the
 * {@code disable_vanilla_recipes} built-in data pack ({@code resourcepacks/disable_vanilla_recipes/
 * data/minecraft/recipe/}, registered by {@code BuiltinDataPacks}, on by default and disableable to
 * restore the vanilla recipes). A modded wood's ring uses its {@code data/plankedchests/tags/
 * item/<wood>_planks.json} tag (a single, {@code required: false} entry) instead of a direct item
 * reference, since this mod is never a build dependency of the wood's source mod - see
 * {@code ChestBlocks#DATAGEN}.
 *
 * <p>Also one cart recipe per cargo — each of the 27 woods' chests and trapped chests, plus
 * vanilla's trapped chest, the barrel, the eight copper chests and the seventeen shulker boxes. Of
 * those, every cargo but this mod's own is one vanilla already draws in a cart, and so needs
 * nothing but the recipe. Vanilla's own {@code chest + minecart} is redirected to this
 * mod's cart too, but not from here: that one has to be written at vanilla's recipe id to replace
 * it rather than collide with it, and {@code FabricRecipeProvider#getRecipeIdentifier} rebuilds
 * every id it is given as {@code <modId>:<path>}. Overriding that would also move the generated
 * advancement onto vanilla's, so it is a hand-written
 * {@code src/main/resources/data/minecraft/recipe/chest_minecart.json} instead — beside the
 * {@code mineable/axe} tag, which is in the {@code minecraft} namespace for the same reason.
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

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries,
            RecipeOutput output) {
        return new RecipeProvider(registries, output) {
            @Override
            public void buildRecipes() {
                for (WoodType wood : WoodType.values()) {
                    Block chest = ChestBlocks.chests().get(wood);
                    Block trapped = ChestBlocks.trappedChests().get(wood);

                    // The recipe's *output* (chest/trapped, above) still needs conditioning even
                    // though the planks ingredient below no longer does: ChestBlocks#DATAGEN
                    // registers every wood's block for this pass regardless, but on a real server
                    // missing this wood's mod, ChestBlocks#init skips it - so the block this recipe
                    // names as its result would not exist there.
                    RecipeOutput output = wood.requiredModId() == null
                            ? this.output
                            : withConditions(this.output,
                                    ResourceConditions.allModsLoaded(wood.requiredModId()));

                    ShapedRecipeBuilder chestRecipe = this.shaped(RecipeCategory.DECORATIONS, chest)
                            .pattern("###")
                            .pattern("# #")
                            .pattern("###");
                    if (wood.requiredModId() == null) {
                        Item planks = BuiltInRegistries.ITEM.getValueOrThrow(ResourceKey.create(
                                Registries.ITEM, Identifier.withDefaultNamespace(
                                        wood.id() + "_planks")));
                        chestRecipe.define('#', planks)
                                .unlockedBy(getHasName(planks), this.has(planks));
                    } else {
                        TagKey<Item> planks = TagKey.create(
                                Registries.ITEM, PlankedChests.id(wood.id() + "_planks"));
                        chestRecipe.define('#', planks)
                                .unlockedBy("has_" + wood.id() + "_planks", this.has(planks));
                    }
                    chestRecipe.save(output);

                    this.shapeless(RecipeCategory.REDSTONE, trapped)
                            .requires(chest)
                            .requires(Items.TRIPWIRE_HOOK)
                            .unlockedBy(getHasName(chest), this.has(chest))
                            .save(output);

                    // One item for every wood, so the wood travels in the result's components
                    // instead of in 27 registry entries. The vanilla chest_minecart recipe names
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
        return ResourceKey.create(Registries.RECIPE, PlankedChests.id(path + "_minecart"));
    }

    @Override
    public String getName() {
        return "Planked Chests Recipes";
    }
}
