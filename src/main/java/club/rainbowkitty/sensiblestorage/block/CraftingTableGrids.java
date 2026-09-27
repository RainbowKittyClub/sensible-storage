package club.rainbowkitty.sensiblestorage.block;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import club.rainbowkitty.kalic0re.common.craft.CraftingGridBlockEntity;
import club.rainbowkitty.kalic0re.common.display.ContainerContentsHolder;
import club.rainbowkitty.kalic0re.common.display.ContainerContentsVisual;
import club.rainbowkitty.kalic0re.common.display.ItemModelExtents;
import club.rainbowkitty.kalic0re.common.item.PolymerModelBlockItem;
import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;
import club.rainbowkitty.kalic0re.common.piston.PistonMovable;

/**
 * What every crafting table with a grid does beyond vanilla's: vanilla's own through
 * {@code CraftingTableBlockMixin}, each planked one by inheriting from it. A crafting table from
 * another mod is left alone, its block not being one {@link CraftingTableBlocks#gridType()}
 * accepts.
 *
 * <p>The grid's items hover over the table's top, one per square, 1/64 above it. Vanilla's
 * {@code crafting_table_top.png}, which every planked top copies, draws its grid lines at pixels 3,
 * 6, 9 and 12, so the squares are centred 3/16 apart. An item shown as its inventory icon is drawn
 * with the icon's canvas 5/32 wide, a little narrower than that spacing, and turns to face
 * whoever looks at it. Any other model is drawn 2/16 to the block, so a block sits well inside
 * its square, and stands facing the player the grid faces.
 * {@link CraftingTableRules#SHOW_INVENTORY} hides them all.
 */
public final class CraftingTableGrids {
    // Distance between neighbouring squares' centres on the table's top, in blocks.
    private static final double SQUARE_PITCH = 3.0 / 16.0;
    // How far above the table's top the items hover, in blocks.
    private static final double HOVER = 1.0 / 64.0;
    // The size one block of a standing model is drawn at, in blocks.
    private static final float BLOCK_SIZE = 2.0f / 16.0f;
    // The width an icon's canvas is drawn at, in blocks.
    private static final float SPRITE_SIZE = 5.0f / 32.0f;
    // A quarter of the default display distance.
    private static final float VIEW_RANGE = 0.25f;
    private static final ContainerContentsVisual VISUAL = new ContainerContentsVisual(
            ContainerContentsVisual.SlotLayout.topGrid(CraftingGridBlockEntity.WIDTH,
                    CraftingGridBlockEntity.HEIGHT, SQUARE_PITCH, HOVER),
            BLOCK_SIZE, SPRITE_SIZE, ContainerContentsVisual.Placement.RESTING,
            Display.BillboardConstraints.CENTER, Display.BillboardConstraints.FIXED);
    // What a display shows while the rule hides the grid: every square empty.
    private static final List<ItemStack> HIDDEN =
            Collections.nCopies(CraftingGridBlockEntity.SIZE, ItemStack.EMPTY);
    // Every display made, for a change of the rule to reach; each drops out once unreferenced.
    private static final Set<ContainerContentsHolder> DISPLAYS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private CraftingTableGrids() {
    }

    /**
     * Lets pistons move an empty crafting table, has the grid's displays measure this mod's items
     * by the model clients draw them with, fetching vanilla's models as a server starts, brings
     * each changed display up to date as the tick ends, and refreshes every display when
     * {@link CraftingTableRules#SHOW_INVENTORY} changes. Call once during mod initialization.
     */
    public static void init() {
        PistonMovable.register(CraftingTableGrids::pistonMayMove);
        ContainerContentsHolder.init();
        GameRuleEvents.changeCallback(CraftingTableRules.SHOW_INVENTORY)
                .register((shown, server) -> refreshDisplays());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ItemModelExtents.prepare());
        // A planked chest's own item_model names no definition: Polymer gives it the whole-chest
        // model on its way to each client, so measure that one.
        ItemModelExtents.registerModelSource(stack -> switch (stack.getItem()) {
            case PolymerModelBlockItem item -> item.model();
            case PolymerModelItem item -> item.model();
            default -> null;
        });
    }

    /** Whether this state's block keeps a grid. */
    public static boolean hasGrid(BlockState state) {
        BlockEntityType<CraftingTableBlockEntity> type = CraftingTableBlocks.gridType();
        return type != null && type.isValid(state);
    }

    /** A new grid for a crafting table, or null for a table from another mod. */
    public static @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return hasGrid(state) ? new CraftingTableBlockEntity(pos, state) : null;
    }

    /** The table's grid as its menu provider, or null to leave the table to vanilla's menu. */
    public static @Nullable MenuProvider menuProvider(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CraftingTableBlockEntity grid ? grid : null;
    }

    /** The comparator signal: how many of the grid's slots hold anything. */
    public static int signal(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CraftingGridBlockEntity grid
                ? grid.filledSlotCount() : 0;
    }

    /**
     * The display of a table's grid, filled once a player first watches it, or null for a table
     * from another mod.
     */
    public static @Nullable ElementHolder elementHolder(ServerLevel level, BlockPos pos,
            BlockState state) {
        if (!hasGrid(state)) {
            return null;
        }
        ContainerContentsHolder display =
                new ContainerContentsHolder(VISUAL, VIEW_RANGE, CraftingTableGrids::contents);
        DISPLAYS.add(display);
        return display;
    }

    /**
     * Brings a grid's display up to date as the tick ends, if its chunk is loaded and it has one.
     * A change undone within the tick, such as a full hopper's, shows nothing.
     */
    public static void showGrid(CraftingGridBlockEntity grid) {
        if (!(grid.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = grid.getBlockPos();
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk != null && BlockBoundAttachment.get(chunk, pos) instanceof BlockBoundAttachment
                attachment && attachment.holder() instanceof ContainerContentsHolder display) {
            display.refreshAtTickEnd();
        }
    }

    // Brings every display still attached up to date; one whose table has gone reads no grid.
    private static void refreshDisplays() {
        List<ContainerContentsHolder> displays;
        synchronized (DISPLAYS) {
            displays = List.copyOf(DISPLAYS);
        }
        displays.forEach(ContainerContentsHolder::refresh);
    }

    // The grid at the holder's current position: a piston carries the holder to the table's new
    // place, so the position it was made at goes stale. Every square empty while the rule hides it.
    private static ContainerContentsHolder.@Nullable Contents contents(
            ContainerContentsHolder holder) {
        if (!(BlockBoundAttachment.get(holder) instanceof BlockBoundAttachment attachment
                && attachment.getWorld().getBlockEntity(attachment.getBlockPos())
                        instanceof CraftingGridBlockEntity grid)) {
            return null;
        }
        return new ContainerContentsHolder.Contents(
                CraftingTableRules.showInventory(attachment.getWorld()) ? grid.getItems() : HIDDEN,
                grid.facing());
    }

    // A crafting table may move while its grid is empty or it has none, as before it had one.
    private static boolean pistonMayMove(BlockState state, Level level, BlockPos pos) {
        if (!(state.getBlock() instanceof CraftingTableBlock)) {
            return false;
        }
        return !(level.getBlockEntity(pos) instanceof CraftingGridBlockEntity grid)
                || grid.isEmpty();
    }
}
