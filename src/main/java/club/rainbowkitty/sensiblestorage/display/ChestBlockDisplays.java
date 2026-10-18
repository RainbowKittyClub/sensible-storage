package club.rainbowkitty.sensiblestorage.display;

import java.util.LinkedHashSet;
import java.util.Set;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.sensiblestorage.block.PlankedChestBlock;
import club.rainbowkitty.sensiblestorage.mixin.BlockDisplayAccessor;

/**
 * Draws a {@link ChestDisplayOverlay} over every block display showing a chest or trapped chest,
 * however it was made: kalic0re.zip's {@code create_from_blocks} copies blocks into block displays
 * riding a root, and a pack client draws a copied chest as nothing. A planked chest keeps its wood;
 * anything a client is shown as vanilla's chest (Lootr's) draws as vanilla's.
 *
 * <p>Bound when the block display loads and dropped when it unloads. A change to its data (block,
 * transformation, interpolation, look) redraws it at the end of that tick; nothing runs per tick
 * otherwise.
 */
public final class ChestBlockDisplays {
    // Block displays whose data changed this tick.
    private static final Set<Display.BlockDisplay> CHANGED = new LinkedHashSet<>();

    /** The overlay slot every block display carries, added by {@code BlockDisplayMixin}. */
    public interface Carrier {
        /** The chest drawn over this block display, or null. */
        @Nullable ChestDisplayOverlay sensiblestorage$overlay();

        /** Sets the chest drawn over this block display. */
        void sensiblestorage$setOverlay(@Nullable ChestDisplayOverlay overlay);
    }

    private ChestBlockDisplays() {}

    /** Binds, redraws and drops overlays as block displays load, change and unload. */
    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Display.BlockDisplay display) {
                refresh(display);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Display.BlockDisplay display) {
                drop(display);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (CHANGED.isEmpty()) {
                return;
            }
            Display.BlockDisplay[] changed = CHANGED.toArray(Display.BlockDisplay[]::new);
            CHANGED.clear();
            for (Display.BlockDisplay display : changed) {
                if (!display.isRemoved() && display.level().getEntity(display.getId()) == display) {
                    refresh(display);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CHANGED.clear());
    }

    /** From any change to a block display's synched data, server side. */
    public static void dataChanged(Display.BlockDisplay display) {
        if (!display.level().isClientSide()) {
            CHANGED.add(display);
        }
    }

    // Binds, keeps and redraws, or drops the overlay for the block display's current block.
    private static void refresh(Display.BlockDisplay display) {
        Carrier carrier = (Carrier) display;
        BlockState state = ((BlockDisplayAccessor) display).sensiblestorage$blockState();
        String chestId = chestId(state);
        ChestDisplayOverlay overlay = carrier.sensiblestorage$overlay();
        if (overlay != null && (chestId == null || !overlay.state().equals(state))) {
            drop(display);
            overlay = null;
        }
        if (chestId == null) {
            return;
        }
        if (overlay == null) {
            overlay = new ChestDisplayOverlay(display, state, chestId);
            carrier.sensiblestorage$setOverlay(overlay);
            EntityAttachment.of(overlay, display);
            // A client already tracking the block display learns of its new riders only so.
            overlay.sendPacket(new ClientboundSetPassengersPacket(display));
            return;
        }
        overlay.copyDisplay();
        overlay.tick();
    }

    // Drops the overlay, if there is one.
    private static void drop(Display.BlockDisplay display) {
        Carrier carrier = (Carrier) display;
        ChestDisplayOverlay overlay = carrier.sensiblestorage$overlay();
        if (overlay != null) {
            overlay.destroy();
            carrier.sensiblestorage$setOverlay(null);
        }
    }

    // The chest id drawing `state`, or null for no chest; vanilla's by what a client is shown.
    private static @Nullable String chestId(BlockState state) {
        if (state.getBlock() instanceof PlankedChestBlock planked) {
            return planked.registryPath();
        }
        BlockState shown = PolymerBlockUtils.getPolymerBlockState(state, null);
        if (shown.is(Blocks.CHEST)) {
            return ChestModels.VANILLA_CHEST_ID;
        }
        return shown.is(Blocks.TRAPPED_CHEST) ? ChestModels.VANILLA_TRAPPED_CHEST_ID : null;
    }
}
