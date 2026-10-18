package club.rainbowkitty.sensiblestorage.display;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Ticks the {@link ChestElementHolder}s that have something to show, and no others: a lid moving,
 * or a block that changed. A holder wakes on its chest's opener count
 * ({@code ChestBlockEntity.signalOpenCount}, every change and every five-tick recheck while open)
 * and on its block's state, and drops out the first tick it sends nothing.
 */
public final class ChestLids {
    // Holders to tick this tick, in wake order.
    private static final Set<ChestElementHolder> AWAKE = new LinkedHashSet<>();

    private ChestLids() {}

    /** Ticks the awake holders at the end of every server tick. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> AWAKE.clear());
    }

    /**
     * Hands the holder of the chest at {@code pos}, if it has one, its new opener count and wakes
     * it; called for every opener count change of any chest block entity. The count is the one
     * vanilla's block event carries, and Lootr keeps its own counter that only this reaches.
     */
    public static void openCountChanged(Level level, BlockPos pos, int count) {
        if (level instanceof ServerLevel
                && BlockBoundAttachment.get(level, pos) instanceof BlockBoundAttachment attachment
                && attachment.holder() instanceof ChestElementHolder holder) {
            holder.openCount(count);
            wake(holder);
        }
    }

    // Ticks the holder from the next end of tick until it settles.
    static void wake(ChestElementHolder holder) {
        AWAKE.add(holder);
    }

    // Steps every awake lid, then poses and sends; a pair's halves both draw the pair's angle, so a
    // moving half wakes its partner.
    private static void tick() {
        if (AWAKE.isEmpty()) {
            return;
        }
        List<ChestElementHolder> batch = new ArrayList<>(AWAKE);
        for (int i = 0; i < batch.size(); i++) {
            ChestElementHolder partner = batch.get(i).pairedHolder();
            if (partner != null && AWAKE.add(partner)) {
                batch.add(partner);
            }
        }
        for (ChestElementHolder holder : batch) {
            holder.step();
        }
        for (ChestElementHolder holder : batch) {
            holder.tick();
            if (holder.getAttachment() == null || holder.settled()) {
                AWAKE.remove(holder);
            }
        }
    }
}
