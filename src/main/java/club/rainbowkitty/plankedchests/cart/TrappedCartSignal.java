package club.rainbowkitty.plankedchests.cart;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The comparator reading a trapped chest cart puts on the rail it is standing on: how many players
 * have it open, which is the number a placed trapped chest gives out as redstone power.
 *
 * <p>A comparator rather than a signal source because an entity cannot be one. Redstone power comes
 * from a block state — {@code isSignalSource} is handed nothing but the state, with no level and no
 * position — so there is no way to answer "is there a cart here" from it. {@code ComparatorBlock}
 * gets both, which makes it the one place the question can be asked at all; see
 * {@code ComparatorBlockMixin}.
 *
 * <p>Nothing here fires while the cart is closed: a zero reading is left alone rather than written
 * as a zero, so a detector rail under a closed trapped cart still reports its own container
 * fullness exactly as it does today.
 */
public final class TrappedCartSignal {
    // Vanilla's own rail-occupancy volume, DetectorRailBlock#getSearchBB - the block inset by 0.2
    // on both horizontal axes and off the top. Borrowed rather than invented so that "which rail is
    // this cart on" has one answer on this server, and the one players already know from detector
    // rails: a cart wide enough to reach into two of these reads on both, here as there.
    private static final double INSET = 0.2;

    // Static-utility class; not instantiable.
    private TrappedCartSignal() {
    }

    /**
     * The signal a comparator facing {@code pos} reads from a trapped chest cart resting there, or
     * 0 when that is not what is there — no rail, no cart, a cart carrying an ordinary chest, or
     * one nobody has open.
     *
     * @param level the level the comparator is in
     * @param pos the block the comparator faces
     */
    public static int read(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!BaseRailBlock.isRail(state)) {
            return 0;
        }

        List<PlankedChestMinecart> carts = level.getEntitiesOfClass(
                PlankedChestMinecart.class, searchBox(pos), PlankedChestMinecart::isTrapSignalling);
        return carts.isEmpty() ? 0 : Mth.clamp(carts.getFirst().openCount(), 0, 15);
    }

    /** The volume a cart standing on the rail at {@code pos} occupies. */
    static AABB searchBox(BlockPos pos) {
        return new AABB(
                pos.getX() + INSET, pos.getY(), pos.getZ() + INSET,
                pos.getX() + 1 - INSET, pos.getY() + 1 - INSET, pos.getZ() + 1 - INSET);
    }
}
