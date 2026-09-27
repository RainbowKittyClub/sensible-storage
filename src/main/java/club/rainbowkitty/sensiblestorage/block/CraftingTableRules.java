package club.rainbowkitty.sensiblestorage.block;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import club.rainbowkitty.sensiblestorage.SensibleStorage;

/**
 * The game rules gating the crafting-table grid. Both are read on every use rather than cached,
 * so flipping one takes effect at once.
 */
public final class CraftingTableRules {
    /**
     * Whether a crafting table keeps its grid between uses. Off, a table hands its grid back on
     * close, as vanilla's does, and anything left in it moves into the grid of whoever opens it
     * next, going back to them on close.
     */
    public static final GameRule<Boolean> HOLD_ITEMS = GameRuleBuilder.forBoolean(true)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(SensibleStorage.id("crafting_tables_hold_items"));

    /** Whether hoppers, droppers and crafters reach a grid that holds items. */
    public static final GameRule<Boolean> HOPPERS_CAN_ACCESS = GameRuleBuilder.forBoolean(false)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(SensibleStorage.id("hoppers_can_access_crafting_tables"));

    private CraftingTableRules() {
    }

    /** Forces the rules to register. Call during mod initialization. */
    public static void init() {
    }

    /** Whether tables in this level hold items; the default where the level has no rules. */
    public static boolean holdItems(@Nullable Level level) {
        return level instanceof ServerLevel server ? server.getGameRules().get(HOLD_ITEMS) : true;
    }

    /** Whether hoppers reach tables in this level; the default where the level has no rules. */
    public static boolean hoppersCanAccess(@Nullable Level level) {
        return level instanceof ServerLevel server
                && server.getGameRules().get(HOPPERS_CAN_ACCESS);
    }
}
