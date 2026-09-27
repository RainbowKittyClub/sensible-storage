package club.rainbowkitty.sensiblestorage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import club.rainbowkitty.kalic0re.common.claim.ClaimExemptions;
import club.rainbowkitty.sensiblestorage.SensibleStorage;

/**
 * Crafting tables under claim mods. Several make vanilla's crafting table public by id, which
 * suits a table that forgets its grid on close and not one that keeps it. A player let into a
 * claimed table only by such an exemption crafts in a grid of their own, never seeing the stored
 * one; and a planked table is exempt wherever vanilla's is, as it would be were it vanilla's.
 */
public final class CraftingTableClaims {
    private CraftingTableClaims() {
    }

    /** Probes the loaded claim mods and makes planked tables stand in for vanilla's. */
    public static void init() {
        ClaimExemptions.init();
        ClaimExemptions.registerStandIn(SensibleStorage.id("claims"),
                state -> state.getBlock() instanceof PlankedCraftingTableBlock,
                Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    /** Whether this player is at the table at {@code pos} only by a claim mod's exemption. */
    public static boolean needsOwnGrid(Player player, BlockPos pos) {
        return player instanceof ServerPlayer serverPlayer
                && ClaimExemptions.refusedWithoutExemption(serverPlayer, pos);
    }
}
