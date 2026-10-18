package club.rainbowkitty.sensiblestorage.block;

import java.util.function.Predicate;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import club.rainbowkitty.kalic0re.common.item.PolymerModelBlockItem;
import club.rainbowkitty.kalic0re.common.item.StandInItems;

/**
 * A {@link PolymerModelBlockItem} that a pack client holds as the stand-in policy's non-placing
 * item even where the policy would pin a stand-in, so that client never predicts a placement.
 * Without RKC Polymer's policy, whose place-sound rule a non-placing item needs, it keeps the
 * vanilla fallback.
 */
public final class UnpinnedBlockItem extends PolymerModelBlockItem {
    private final Predicate<PacketContext> useRealItem;

    /**
     * @param block the block placed
     * @param fallback the vanilla item shown to clients without the pack, or with it on stock
     *     Polymer
     * @param model the item-model definition id shown to clients with the pack
     * @param useRealItem clients for which this returns true are sent the real item id
     * @param properties standard item properties, already carrying this item's id
     */
    public UnpinnedBlockItem(Block block, Item fallback, Identifier model,
            Predicate<PacketContext> useRealItem, Properties properties) {
        super(block, fallback, model, useRealItem, properties);
        this.useRealItem = useRealItem;
    }

    @Override
    public Item getPolymerItem(ItemStack stack, PacketContext context) {
        if (!useRealItem.test(context) && StandInItems.available()
                && PolymerResourcePackUtils.hasMainPack(context)) {
            // The policy's non-placing item; RKC Polymer exposes no constant for it.
            return Items.FIREWORK_STAR;
        }
        return super.getPolymerItem(stack, context);
    }

    /** Pins nothing, since no client holds this as a placing stand-in item. */
    @Override
    public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context,
            HolderLookup.Provider lookup) {
    }
}
