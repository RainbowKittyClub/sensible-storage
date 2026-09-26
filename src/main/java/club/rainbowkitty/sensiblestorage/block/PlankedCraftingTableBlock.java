package club.rainbowkitty.sensiblestorage.block;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * A wooden crafting table. Server-side it behaves exactly like vanilla's. What a client is sent
 * comes in three tiers: one that also runs this mod (see {@link SensibleStorage#HANDSHAKE}) gets
 * the real block state and draws it from its own block model; one with this server's resource pack
 * gets a full-block state Polymer set aside for this table, which the pack draws with the same
 * model; and every other client gets vanilla's crafting table itself. Unlike
 * {@link PlankedChestBlock} there is nothing to animate and no shape to fake, so no display entity
 * stands in for it and no barrier hides it.
 *
 * <p>{@link PolymerTexturedBlock} is what keeps that set-aside state intact on its way out: Polymer
 * remaps every state it has handed out back to a plain one for any block not carrying the marker.
 */
public class PlankedCraftingTableBlock extends CraftingTableBlock
        implements PolymerTexturedBlock, PolymerClientDecoded {
    private static final BlockState CLIENT_STATE = Blocks.CRAFTING_TABLE.defaultBlockState();
    // Vanilla's own title, which CraftingTableBlock keeps private.
    private static final Component CONTAINER_TITLE = Component.translatable("container.crafting");

    private final WoodType wood;
    private final @Nullable BlockState packState;

    /**
     * @param wood the wood this table is made of
     * @param packState the state a client with this server's resource pack is sent, from
     *     {@code PolymerBlockResourceUtils.requestBlock}; null if Polymer had none left to give, in
     *     which case such a client gets vanilla's table like everyone else
     */
    public PlankedCraftingTableBlock(WoodType wood, @Nullable BlockState packState,
            BlockBehaviour.Properties properties) {
        super(properties);
        this.wood = wood;
        this.packState = packState;
    }

    /** Returns the wood this table is made of. */
    public WoodType wood() {
        return wood;
    }

    /** Vanilla's crafting menu, kept open by this block rather than by vanilla's. */
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((containerId, inventory, player) -> new Menu(
                containerId, inventory, ContainerLevelAccess.create(level, pos), this),
                CONTAINER_TITLE);
    }

    /**
     * A client that also runs this mod <em>and registered this wood</em> gets the real state (and
     * draws it itself); one with the resource pack gets the state set aside for this table; every
     * other client gets vanilla's crafting table. Polymer resends the world when a client's pack
     * status changes, so a pack that fails to load after being accepted still lands on vanilla's
     * table.
     */
    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        if (SensibleStorage.HANDSHAKE.supportsAll(context)) {
            return state;
        }
        if (packState != null && context != null
                && PolymerResourcePackUtils.hasMainPack(context)) {
            return packState;
        }
        return CLIENT_STATE;
    }

    // Vanilla's crafting menu, valid at this table: CraftingMenu#stillValid wants
    // Blocks.CRAFTING_TABLE itself at the position, so would close the menu a tick after opening.
    private static final class Menu extends CraftingMenu {
        private final ContainerLevelAccess access;
        private final Block table;

        private Menu(int containerId, Inventory inventory, ContainerLevelAccess access,
                Block table) {
            super(containerId, inventory, access);
            this.access = access;
            this.table = table;
        }

        @Override
        public boolean stillValid(Player player) {
            return stillValid(this.access, player, this.table);
        }
    }
}
