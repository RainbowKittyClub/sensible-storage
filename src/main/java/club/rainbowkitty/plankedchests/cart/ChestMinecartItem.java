package club.rainbowkitty.plankedchests.cart;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.display.ChestModels;
import club.rainbowkitty.plankedchests.wood.WoodType;
import club.rainbowkitty.rkcore.common.vehicle.CargoCartItem;

/**
 * The one chest minecart item, for all woods. Everything per-wood is derived from the cargo block
 * on the stack, so the wording lives in one place and changing it never means regenerating recipes.
 *
 * <p>Exists alongside the generic {@link CargoCartItem} only for the two things that are this mod's
 * rather than the machinery's: vanilla's own name for the wood-less variant, and (from S5) the
 * per-wood icon.
 */
public class ChestMinecartItem extends CargoCartItem {
    /**
     * @param type this mod's cart type, which this item places
     * @param cargoComponent the component a stack carries its chest block in
     * @param properties standard item properties, which must already carry the item's registry id
     */
    public ChestMinecartItem(EntityType<? extends AbstractMinecart> type,
            DataComponentType<Holder<Block>> cargoComponent, Properties properties) {
        // No one model for every stack: the icon is per wood and comes from cargoModel below.
        super(type, cargoComponent, ChestCarts.defaultCargo(), ChestCarts.NAME_PATTERN_KEY,
                Items.CHEST_MINECART, null, PlankedChests.HANDSHAKE::supportsAll, properties);
    }

    /**
     * Names a cart for its chest, keeping vanilla's wording for the wood-less variant.
     *
     * <p>Overriding this rather than {@code getName} deliberately: the base class answers an
     * {@code item_name} written onto a stack before it composes anything, and a client running this
     * mod depends on that — the composed name reaches it as exactly that component.
     *
     * <p>The wood-less case is answered here and not delegated back to {@link ChestCarts}. It used
     * to live there, and routing it through {@code ChestCarts.displayName} once this became an
     * override made the two call each other without end.
     */
    @Override
    public Component cartName(@Nullable Holder<Block> cargo) {
        if (cargo == null || cargo.value() == Blocks.CHEST) {
            return Component.translatableWithFallback(
                    ChestCarts.PLAIN_NAME_KEY, ChestCarts.PLAIN_NAME_FALLBACK);
        }
        return super.cartName(cargo);
    }

    /**
     * Chest-like blocks only, per {@link ChestCarts#CARGO_TAG}.
     *
     * <p>Asked of a stack's component and of a cart's saved cargo alike, so a block outside the tag
     * is refused wherever it arrives from. A tag read costs a set lookup on an already-resolved
     * holder, which is cheap enough to do on every read rather than caching a copy that a
     * {@code /reload} would leave stale.
     */
    @Override
    public boolean acceptsCargo(Holder<Block> cargo) {
        return cargo.is(ChestCarts.CARGO_TAG);
    }

    /**
     * Shulker boxes, and nothing else this cart carries.
     *
     * <p>The cargo's own rule, not the cart's: a mined shulker box keeps its contents and a mined
     * chest, barrel or copper chest does not, so a cart is broken like the block it is drawn as.
     * Nothing is handed out by this that the cargo did not already have — a shulker cart costs a
     * shulker box, which was already a container you could carry full.
     *
     * <p>Answering yes for the chests instead would make a cart crafted from planks and iron do
     * what vanilla reserves for a trip to the End, which is a change to what storage costs rather
     * than a change to how a cart behaves.
     */
    @Override
    public boolean cargoKeepsContents(Holder<Block> cargo) {
        return cargo.is(BlockTags.SHULKER_BOXES);
    }

    /**
     * A wood's own cart icon, a drawn cargo's, or null for anything else.
     *
     * <p>Public rather than protected, because the creative tab has to bake this onto the stacks it
     * offers — see {@link ChestCarts#carts()}.
     *
     * <p>Null leaves the stack with no model of its own, which is what the wood-less variant wants:
     * it is drawn as the vanilla chest minecart it presents as, and that is S7's whole point. Any
     * cargo with no icon of its own lands there too — a block a datapack added to the tag — since a
     * plain chest cart reads better than a missing model.
     */
    @Override
    public @Nullable Identifier cargoModel(Holder<Block> cargo) {
        WoodType wood = ChestCarts.woodOf(cargo);
        return wood != null ? ChestModels.cartModel(wood.id()) : ChestCarts.cartIcon(cargo);
    }
}
