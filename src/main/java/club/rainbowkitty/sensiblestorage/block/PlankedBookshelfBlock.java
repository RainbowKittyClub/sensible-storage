package club.rainbowkitty.sensiblestorage.block;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.kalic0re.common.mining.VanillaCreativeBreak;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * A wooden bookshelf. Server-side it is vanilla's bookshelf, which is a plain {@link Block} with no
 * behaviour of its own: enchanting power, drops and flammability all come from tags, loot and
 * registries (see {@code BookshelfBlocks}). What a client is sent comes in the crafting table's
 * three tiers: the real state for one that also runs this mod, a full-block state Polymer set aside
 * for this shelf for one with the resource pack, and vanilla's bookshelf for everyone else.
 *
 * <p>{@link PolymerTexturedBlock} keeps that set-aside state intact on its way out: Polymer remaps
 * every state it has handed out back to a plain one for any block not carrying the marker.
 */
public class PlankedBookshelfBlock extends Block
        implements PolymerTexturedBlock, PolymerClientDecoded, VanillaCreativeBreak {
    private static final BlockState CLIENT_STATE = Blocks.BOOKSHELF.defaultBlockState();

    private final WoodType wood;
    private final @Nullable BlockState packState;

    /**
     * @param wood the wood this shelf is made of
     * @param packState the state a client with this server's resource pack is sent, from
     *     {@code PolymerBlockResourceUtils.requestBlock}; null if Polymer had none left to give, in
     *     which case such a client gets vanilla's bookshelf like everyone else
     */
    public PlankedBookshelfBlock(WoodType wood, @Nullable BlockState packState,
            BlockBehaviour.Properties properties) {
        super(properties);
        this.wood = wood;
        this.packState = packState;
    }

    /** Returns the wood this shelf is made of. */
    public WoodType wood() {
        return wood;
    }

    /**
     * The real state for a client that also runs this mod <em>and registered this wood</em>; the
     * set-aside state for one with the resource pack; vanilla's bookshelf for every other client.
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
}
