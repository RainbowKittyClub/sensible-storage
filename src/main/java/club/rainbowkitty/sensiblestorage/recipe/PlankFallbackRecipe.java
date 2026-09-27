package club.rainbowkitty.sensiblestorage.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import club.rainbowkitty.sensiblestorage.SensibleStorage;

/**
 * A shaped recipe that steps aside for any grid another crafting recipe also matches - vanilla's
 * own chest, crafting table, bookshelf and chiseled bookshelf recipes, rewritten so a grid a wood's
 * own recipe is written for goes to that recipe, and every other grid still makes vanilla's block.
 * Nothing here is specific to any one block: the pattern and result come from the recipe JSON, so
 * each override names this one serializer.
 *
 * <p>Without this, {@code minecraft:chest} and {@code sensiblestorage:birch_chest} (etc.) both
 * match a ring of, say, eight birch planks - {@code #minecraft:planks} takes any plank, and
 * vanilla's shaped recipe does not require its eight matched slots to hold the *same* item. Recipes
 * of one {@link net.minecraft.world.item.crafting.RecipeType} that both match one input are
 * resolved by {@link net.minecraft.world.item.crafting.RecipeMap#getRecipesFor}, which returns the
 * first match of a list sorted by {@link net.minecraft.resources.Identifier}'s natural order - path
 * first, namespace second. Comparing {@code "chest"} against a wood's {@code "<wood>_chest"} is a
 * coin flip that has nothing to do with which recipe should win: {@code "birch_chest"} sorts before
 * it and wins on its own, but {@code "spruce_chest"} sorts after it and loses, silently handing a
 * plain spruce ring a vanilla chest. This recipe closes that gap by refusing any grid another
 * recipe matches, so such a grid only ever has one match regardless of sort order.
 *
 * <p>Deferring to whatever else matches, rather than to a list of woods, is what keeps every other
 * grid making vanilla's block: planks of a wood this mod has no block for, oak's (vanilla's own
 * blocks are oak), a mixed ring, or a chiseled bookshelf's planks over another wood's slabs. None
 * has a recipe of its own to defer to.
 *
 * <p>{@code ShapedRecipe} itself cannot be extended for this - its {@code pattern} and
 * {@code result} fields are private to it, leaving a subclass nothing to build a codec from - so
 * this instead duplicates its handful of methods and adds the one predicate.
 */
public final class PlankFallbackRecipe extends NormalCraftingRecipe {
    private static final MapCodec<PlankFallbackRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
                    ShapedRecipePattern.MAP_CODEC.forGetter(o -> o.pattern),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result))
                    .apply(i, PlankFallbackRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, PlankFallbackRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
                    CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
                    ShapedRecipePattern.STREAM_CODEC, o -> o.pattern,
                    ItemStackTemplate.STREAM_CODEC, o -> o.result,
                    PlankFallbackRecipe::new);

    /** This recipe's serializer, registered under {@code sensiblestorage:plank_fallback}. */
    public static final RecipeSerializer<PlankFallbackRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    // The server's recipe list as last sorted by otherCraftingRecipes, and what that sort kept.
    private static @Nullable Collection<RecipeHolder<?>> scannedRecipes;
    private static List<CraftingRecipe> otherCrafting = List.of();

    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;

    private PlankFallbackRecipe(Recipe.CommonInfo commonInfo,
            CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern,
            ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.result = result;
    }

    /** Registers {@link #SERIALIZER}. Call once from {@code onInitialize}. */
    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                SensibleStorage.id("plank_fallback"), SERIALIZER);
    }

    @Override
    public RecipeSerializer<PlankFallbackRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(this.pattern.ingredients());
    }

    /**
     * Matches the pattern, unless another crafting recipe matches the same grid. Only a server
     * level holds recipes to ask; anywhere else this is the pattern alone.
     */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input)
                && !(level instanceof ServerLevel server && matchedElsewhere(input, server));
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return this.result.create();
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new ShapedCraftingRecipeDisplay(
                this.pattern.width(), this.pattern.height(),
                this.pattern.ingredients().stream()
                        .map(e -> e.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE))
                        .toList(),
                new SlotDisplay.ItemStackSlotDisplay(this.result),
                new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }

    // True when a crafting recipe other than a fallback matches too. Skipping every fallback, this
    // one included, is also what keeps the check from recursing back into itself.
    private static boolean matchedElsewhere(CraftingInput input, ServerLevel level) {
        for (CraftingRecipe recipe : otherCraftingRecipes(level)) {
            if (recipe.matches(input, level)) {
                return true;
            }
        }
        return false;
    }

    // Every crafting recipe but the fallbacks, sorted out once per recipe list rather than on every
    // grid change: a reload replaces the list the server hands out, which rebuilds this.
    private static List<CraftingRecipe> otherCraftingRecipes(ServerLevel level) {
        Collection<RecipeHolder<?>> recipes = level.recipeAccess().getRecipes();
        if (recipes != scannedRecipes) {
            List<CraftingRecipe> others = new ArrayList<>();
            for (RecipeHolder<?> holder : recipes) {
                if (holder.value().getType() == RecipeType.CRAFTING
                        && holder.value() instanceof CraftingRecipe recipe
                        && !(recipe instanceof PlankFallbackRecipe)) {
                    others.add(recipe);
                }
            }
            otherCrafting = List.copyOf(others);
            scannedRecipes = recipes;
        }
        return otherCrafting;
    }
}
