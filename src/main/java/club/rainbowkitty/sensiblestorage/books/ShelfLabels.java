package club.rainbowkitty.sensiblestorage.books;

import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.kalic0re.common.label.BlockLabels;
import club.rainbowkitty.kalic0re.common.label.HoverScanner;
import club.rainbowkitty.kalic0re.common.label.ItemText;
import club.rainbowkitty.kalic0re.common.label.LabelMetrics;
import club.rainbowkitty.kalic0re.common.label.LabelStyle;
import club.rainbowkitty.kalic0re.common.label.LabelTarget;
import club.rainbowkitty.kalic0re.common.label.LabelText;

/** Names the book in the chiseled bookshelf slot under a player's crosshair. */
public final class ShelfLabels implements BlockLabels {
    // FIXED rather than a billboard: a billboarded label pivots about its own centre, so at
    // oblique angles its ends swing through the shelf. Pinned to the face it never can.
    private static final LabelStyle STYLE = new LabelStyle(0.33F,
            Display.BillboardConstraints.FIXED, false, 0.9, Display.TextDisplay.Align.CENTER,
            true, Brightness.FULL_BRIGHT);

    // Measured from the block centre, so half a block reaches the front face and the rest is the
    // gap that keeps the text off it.
    private static final double FACE_CLEARANCE = 0.5 + 1.0 / 16.0;
    private static final double ROW_OFFSET = 0.25;

    private ShelfLabels() {
    }

    /** Starts labelling chiseled bookshelves. */
    public static void register() {
        HoverScanner.register(new ShelfLabels());
    }

    @Override
    public LabelTarget target(ServerLevel level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChiseledBookShelfBlock shelf)) {
            return null;
        }

        // getHitSlot is empty unless the hit face is the shelf's front, which is the only side
        // with books on it, so this doubles as the "looking at the readable face" check.
        Direction facing = state.getValue(ChiseledBookShelfBlock.FACING);
        OptionalInt slot = shelf.getHitSlot(hit, facing);
        if (slot.isEmpty()) {
            return null;
        }

        if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelfEntity)) {
            return null;
        }

        ItemStack book = shelfEntity.getItem(slot.getAsInt());
        if (book.isEmpty()) {
            return null;
        }

        LabelText text = ItemText.of(book);
        int row = slot.getAsInt() / shelf.getColumns();

        // Pinning the yaw to the shelf makes the label's own +Z the way the front face points, so
        // clearing the face is a push along it.
        double dy = (row == 0 ? ROW_OFFSET : -ROW_OFFSET) - LabelMetrics.centring(STYLE, text);
        Vec3 offset = new Vec3(0.0, dy, FACE_CLEARANCE);

        return new LabelTarget(level, Vec3.atCenterOf(pos), facing.toYRot(), text, offset, STYLE);
    }
}
