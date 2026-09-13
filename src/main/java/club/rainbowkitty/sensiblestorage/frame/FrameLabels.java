package club.rainbowkitty.sensiblestorage.frame;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.kalic0re.common.label.EntityLabels;
import club.rainbowkitty.kalic0re.common.label.HoverScanner;
import club.rainbowkitty.kalic0re.common.label.ItemText;
import club.rainbowkitty.kalic0re.common.label.LabelMetrics;
import club.rainbowkitty.kalic0re.common.label.LabelStyle;
import club.rainbowkitty.kalic0re.common.label.LabelTarget;
import club.rainbowkitty.kalic0re.common.label.LabelText;

/** Names whatever is in the item frame under a player's crosshair. */
public final class FrameLabels implements EntityLabels {
    // Replaces vanilla's own framed-item nametag like for like: that one billboarded and drew
    // through walls, so this one has to as well or framed items get harder to read than before.
    private static final LabelStyle STYLE = new LabelStyle(0.5F,
            Display.BillboardConstraints.CENTER, true, 2.5, Display.TextDisplay.Align.CENTER,
            true, Brightness.FULL_BRIGHT);

    // Taken off the frame's own face rather than fixed, so a map frame clears by proportionally
    // as much as a normal one.
    private static final double GAP = 0.2;

    private FrameLabels() {
    }

    /** Starts labelling item frames. */
    public static void register() {
        HoverScanner.register(new FrameLabels());
    }

    @Override
    public boolean handles(Entity entity) {
        return entity instanceof ItemFrame;
    }

    @Override
    public LabelTarget target(ServerLevel level, EntityHitResult hit) {
        ItemFrame frame = (ItemFrame) hit.getEntity();
        ItemStack item = frame.getItem();

        // The same stacks vanilla would have nametagged and no others: ItemFrameRenderer's
        // shouldShowName asks for a custom name, which for a written book is its title. An item
        // that was anonymous in a frame before stays anonymous.
        if (item.getCustomName() == null) {
            return null;
        }

        // The offset is in the billboard's own frame, so a purely vertical one keeps the label
        // directly over the item from every angle, for wall, floor and ceiling frames alike.
        LabelText text = ItemText.of(item);
        return new LabelTarget(level, frame.position(), 0.0F, text,
                new Vec3(0.0, rise(frame, text), 0.0), STYLE);
    }

    private static double rise(ItemFrame frame, LabelText text) {
        AABB box = frame.getBoundingBox();
        double gap = Math.max(box.getXsize(), box.getZsize()) * GAP;

        // A ceiling frame has a block directly above it, so its label hangs underneath instead.
        // Text grows upward from where it is put, hence the extra drop by its own height.
        if (frame.getDirection() == Direction.DOWN) {
            return box.minY - frame.getY() - gap - LabelMetrics.height(STYLE, text);
        }

        return box.maxY - frame.getY() + gap;
    }
}
