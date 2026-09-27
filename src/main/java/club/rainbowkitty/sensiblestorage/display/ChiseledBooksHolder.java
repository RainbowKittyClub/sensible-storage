package club.rainbowkitty.sensiblestorage.display;

import java.util.ArrayList;
import java.util.List;

import com.mojang.math.Axis;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.kalic0re.common.display.ItemDisplays;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.block.ChiseledBooks;
import club.rainbowkitty.sensiblestorage.block.ChiseledBookshelfModels;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * The books of a planked chiseled bookshelf, for a client with the resource pack but not this mod.
 * Such a client is sent a set-aside state that draws the wood's frame with every slot empty,
 * whatever the shelf holds; this holder's one item display draws each occupied slot's book and its
 * shadow over it, and nothing else, from the wood's {@link ChiseledBookshelfModels#books} item
 * model, so the frame is terrain's everywhere. The stack's {@code custom_model_data} strings say
 * what each slot shows: its book number from {@link ChiseledBooks#book}, the book terrain would
 * draw there, or empty.
 *
 * <p>The display sits in the block in front of the shelf: an entity is lit by the block it stands
 * in, and in the shelf itself that would be solid, so black. Its transformation carries the model
 * back onto the face, just in front of it.
 *
 * <p>Nothing ticks: the display changes only on {@link BlockBoundAttachment#BLOCK_STATE_UPDATE}.
 * Nothing here reads the level either, the holder being built during chunk load.
 */
public class ChiseledBooksHolder extends ElementHolder {
    // How far in front of the shelf's face the books are drawn, in blocks, so they never tie on
    // depth with the empty slots terrain draws beneath them.
    private static final float LIFT = 1.0f / 512.0f;

    private final Identifier model;
    private final int[] books = new int[ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size()];
    private final ItemDisplayElement display = ItemDisplays.element();

    // The state last shown, so an update that changes nothing sends nothing.
    private @Nullable BlockState shown;

    /**
     * Creates the books for a shelf that has just been placed or loaded.
     *
     * @param pos the shelf's position, which fixes each slot's book
     * @param wood the shelf's wood, which names its item model
     * @param initialState the shelf's state now
     */
    public ChiseledBooksHolder(BlockPos pos, WoodType wood, BlockState initialState) {
        this.model = ChiseledBookshelfModels.books(wood);
        for (int slot = 0; slot < books.length; slot++) {
            books[slot] = ChiseledBooks.book(pos, slot);
        }
        addElement(display);
        show(initialState);
    }

    // A client running this mod draws the real shelf, books and all; one without the pack has
    // neither the models this names nor the planked frame under them.
    @Override
    public boolean startWatching(ServerGamePacketListenerImpl player) {
        return !SensibleStorage.HANDSHAKE.supportsAll(player)
                && PolymerResourcePackUtils.hasMainPack(player)
                && super.startWatching(player);
    }

    // The holder is not ticked, so a change is flushed here rather than on a tick that never comes.
    @Override
    public void notifyUpdate(HolderAttachment.UpdateType updateType) {
        super.notifyUpdate(updateType);
        if (updateType == BlockBoundAttachment.BLOCK_STATE_UPDATE
                && getAttachment() instanceof BlockBoundAttachment bound
                && show(bound.getBlockState())) {
            tick();
        }
    }

    // Poses the display to the shelf's facing and stacks its occupied slots; false if nothing
    // changed, or the block is not a chiseled shelf (mid-replacement).
    private boolean show(BlockState state) {
        if (state == shown || !state.hasProperty(ChiseledBookShelfBlock.FACING)) {
            return false;
        }
        shown = state;
        Direction facing = state.getValue(ChiseledBookShelfBlock.FACING);
        Vector3f out = new Vector3f(facing.getUnitVec3f());
        display.setOffset(new Vec3(out));
        // With the renderer's own 180° turn (ItemDisplayPose), this turns the north-authored
        // front to the facing, as the blockstate's y rotation does.
        display.setLeftRotation(Axis.YP.rotationDegrees(-facing.toYRot()));
        display.setTranslation(out.mul(LIFT - 1.0f));
        display.setItem(stack(state));
        return true;
    }

    // The stack drawing a state's occupied slots, or none when every slot is empty.
    private ItemStack stack(BlockState state) {
        List<String> slots = new ArrayList<>(books.length);
        boolean any = false;
        for (int slot = 0; slot < books.length; slot++) {
            boolean occupied =
                    state.getValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(slot));
            slots.add(occupied ? Integer.toString(books[slot]) : "");
            any |= occupied;
        }
        if (!any) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = ItemDisplays.modelStack(Items.BOOK, model);
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), slots, List.of()));
        return stack;
    }
}
