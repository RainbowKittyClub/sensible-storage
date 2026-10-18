package club.rainbowkitty.sensiblestorage.display;

import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.sensiblestorage.SensibleStorage;

/**
 * Draws vanilla's chest and trapped chest with display entities for every client holding the
 * server pack, which draws vanilla's own chest invisible ({@link VanillaChestPack}). Polymer binds
 * a holder to each such block through an overlay, as it does for a block of its own.
 *
 * <p>Lootr's loot chests count too: LootrPolymer sends every client vanilla's chest or trapped
 * chest in their place, and Lootr's opener counter signals through vanilla's
 * {@code signalOpenCount}, so the same holder draws them.
 */
public final class VanillaChestDisplays {
    // Lootr's chest and trapped chest, by id: Lootr is never a dependency of this mod.
    private static final Identifier LOOTR_CHEST = Identifier.fromNamespaceAndPath("lootr", "chest");
    private static final Identifier LOOTR_TRAPPED_CHEST =
            Identifier.fromNamespaceAndPath("lootr", "trapped_chest");

    // Whether the overlays are in; a client's integrated server can start more than once.
    private static boolean bound;

    private VanillaChestDisplays() {}

    /**
     * Binds a {@link ChestElementHolder} to every vanilla and Lootr chest once the server starts,
     * after every mod has registered its blocks and overlays and before any chunk loads.
     */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> bindAll());
    }

    // Puts the overlays in, once.
    private static void bindAll() {
        if (bound) {
            return;
        }
        bound = true;
        bind(Blocks.CHEST, ChestModels.VANILLA_CHEST_ID, false);
        bind(Blocks.TRAPPED_CHEST, ChestModels.VANILLA_TRAPPED_CHEST_ID, false);
        // LootrPolymer's own overlay there only draws its indicator, which is off by default.
        BuiltInRegistries.BLOCK.getOptional(LOOTR_CHEST)
                .ifPresent(block -> bind(block, ChestModels.VANILLA_CHEST_ID, true));
        BuiltInRegistries.BLOCK.getOptional(LOOTR_TRAPPED_CHEST)
                .ifPresent(block -> bind(block, ChestModels.VANILLA_TRAPPED_CHEST_ID, true));
    }

    // Registers the overlay; another mod's already there is kept unless `replace`.
    private static void bind(Block block, String chestId, boolean replace) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (BlockWithElementHolder.get(block.defaultBlockState()) != null) {
            if (!replace) {
                SensibleStorage.LOGGER.warn("{} already has an element holder; pack clients will "
                        + "see no chest there", id);
                return;
            }
            SensibleStorage.LOGGER.info("Replacing {}'s element holder with a chest display", id);
        }
        BlockWithElementHolder.registerOverlay(block, new BlockWithElementHolder() {
            @Override
            public ElementHolder createElementHolder(ServerLevel world, BlockPos pos,
                    BlockState state) {
                return new ChestElementHolder(world, pos, chestId,
                        ChestElementHolder.Audience.PACK, state);
            }
        });
    }
}
