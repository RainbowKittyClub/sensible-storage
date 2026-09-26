package club.rainbowkitty.sensiblestorage.recipe;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import club.rainbowkitty.sensiblestorage.SensibleStorage;

/**
 * A shaped recipe that steps aside for any grid holding one {@code claimed} item alone - vanilla's
 * own {@code minecraft:chest} recipe, rewritten so a uniform ring of a wood with its own chest goes
 * to that wood's recipe, and every other ring still makes vanilla's. Nothing here is specific to a
 * chest: the pattern, result and claimed set all come from the recipe JSON.
 *
 * <p>Without this, {@code minecraft:chest} and {@code sensiblestorage:oak_chest} (etc.) both match
 * a ring of, say, eight oak planks - {@code #minecraft:planks} takes any plank, and vanilla's
 * shaped recipe does not require its eight matched slots to hold the *same* item. Recipes of one
 * {@link net.minecraft.world.item.crafting.RecipeType} that both match one input are resolved by
 * {@link net.minecraft.world.item.crafting.RecipeMap#getRecipesFor}, which returns the first match
 * of a list sorted by {@link net.minecraft.resources.Identifier}'s natural order - path first,
 * namespace second. Comparing {@code "chest"} against a wood's {@code "<wood>_chest"} is a coin
 * flip that has nothing to do with which recipe should win: {@code "birch_chest"} sorts before it
 * and wins on its own, but {@code "oak_chest"} sorts after it and loses, silently handing a plain
 * oak ring a vanilla chest. This recipe closes that gap by refusing a grid that a wood's own recipe
 * is written for, so such a grid only ever has one match regardless of sort order.
 *
 * <p>Refusing only claimed items, rather than every uniform grid, is what keeps a plank with no
 * chest of its own - a wood from a mod this one has no chest for - making vanilla's chest rather
 * than nothing at all.
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
                    RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("claimed")
                            .forGetter(o -> o.claimed),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result))
                    .apply(i, PlankFallbackRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, PlankFallbackRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
                    CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
                    ShapedRecipePattern.STREAM_CODEC, o -> o.pattern,
                    ByteBufCodecs.holderSet(Registries.ITEM), o -> o.claimed,
                    ItemStackTemplate.STREAM_CODEC, o -> o.result,
                    PlankFallbackRecipe::new);

    /** This recipe's serializer, registered under {@code sensiblestorage:plank_fallback}. */
    public static final RecipeSerializer<PlankFallbackRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ShapedRecipePattern pattern;
    private final HolderSet<Item> claimed;
    private final ItemStackTemplate result;

    private PlankFallbackRecipe(Recipe.CommonInfo commonInfo,
            CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern,
            HolderSet<Item> claimed, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.claimed = claimed;
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

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input) && !isClaimed(input);
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

    // True when every filled slot holds the same item and that item is claimed - a grid of one
    // plank type is left to that wood's own recipe, but a second distinct item makes it mixed.
    private boolean isClaimed(CraftingInput input) {
        ItemStack first = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = stack;
            } else if (!stack.is(first.getItem())) {
                return false;
            }
        }
        return !first.isEmpty() && first.is(this.claimed);
    }
}
