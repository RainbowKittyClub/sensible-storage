package club.rainbowkitty.plankedchests.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.cart.ChestCarts;
import club.rainbowkitty.plankedchests.display.ChestModels;
import club.rainbowkitty.plankedchests.wood.WoodType;
import club.rainbowkitty.rkcore.common.item.ItemRegistration;
import club.rainbowkitty.rkcore.common.item.PolymerModelBlockItem;

/** Registers a chest and a trapped chest per {@link WoodType}, plus the two block-entity types. */
public final class ChestBlocks {
    /** Shared type for every plain chest in this mod; assigned in {@link #init()}. */
    public static BlockEntityType<PlankedChestBlockEntity> chestType;
    /** Shared type for every trapped chest in this mod; assigned in {@link #init()}. */
    public static BlockEntityType<PlankedTrappedChestBlockEntity> trappedType;

    /** Translation key for the dedicated creative tab's title. */
    public static final String CREATIVE_TAB_TITLE_KEY =
            "itemGroup." + PlankedChests.MOD_ID + ".chests";

    /** Double-chest menu title key; takes the wood display name as {@code %s}. */
    public static final String LARGE_CHEST_TITLE_KEY =
            "container." + PlankedChests.MOD_ID + ".large_chest";

    private static final Map<WoodType, PlankedChestBlock> CHESTS = new LinkedHashMap<>();
    private static final Map<WoodType, PlankedTrappedChestBlock> TRAPPED = new LinkedHashMap<>();

    // Fabric API's own signal that this JVM is a `runDatagen` pass, not a real boot (verified via
    // FabricDataGenHelper's bytecode - no public constant exposes it). Registration below ignores
    // the mod-presence gate under datagen, so every WoodType still gets a Block for the lang/
    // recipe/loot providers to read: the 15 modded woods are never Gradle dependencies of this mod
    // (only their id strings are needed - see docs/plan/modded-wood-types.md), so isModLoaded is
    // always false for them here regardless of what a real target server has installed.
    private static final boolean DATAGEN = System.getProperty("fabric-api.datagen") != null;

    private ChestBlocks() {}

    /** All registered plain chest blocks, wood order. */
    public static Map<WoodType, PlankedChestBlock> chests() {
        return CHESTS;
    }

    /** All registered trapped chest blocks, wood order. */
    public static Map<WoodType, PlankedTrappedChestBlock> trappedChests() {
        return TRAPPED;
    }

    /**
     * Registers a dedicated {@code plankedchests:chests} creative tab holding every chest, then
     * every trapped chest, then every minecart. A Polymer server-side tab: adding entries to the
     * vanilla tabs does not reliably sync to Polymer clients, but a Polymer-registered tab does
     * (and shows in {@code /polymer creative}). Call after {@link #init()} and
     * {@code ChestCarts#init()}, since it reads both.
     *
     * <p>What this tab is <em>not</em> is a way onto a vanilla client's own creative screen. That
     * screen is built client-side from the client's own registry, and
     * {@code PolymerServerProtocol#sendCreativeSyncPackets} sends nothing at all to a connection
     * that did not negotiate the creative-tab protocol — so a vanilla client reaches this tab only
     * through {@code /polymer creative}. The carts are craftable and pick-block-able for that
     * reason, rather than browsing being their only route.
     */
    public static void registerCreativeTab() {
        CreativeModeTab tab = PolymerCreativeModeTabUtils.builder()
                .title(Component.translatable(CREATIVE_TAB_TITLE_KEY))
                .icon(() -> new ItemStack(CHESTS.get(WoodType.OAK)))
                .displayItems((params, output) -> {
                    CHESTS.values().forEach(output::accept);
                    TRAPPED.values().forEach(output::accept);
                    output.acceptAll(ChestCarts.carts());
                })
                .build();
        PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(PlankedChests.id("chests"), tab);
    }

    /** Builds and registers every block, block-entity type and block item. */
    public static void init() {
        List<Block> chestBlocks = new ArrayList<>();
        List<Block> trappedBlocks = new ArrayList<>();

        for (WoodType wood : WoodType.values()) {
            if (!DATAGEN && wood.requiredModId() != null
                    && !FabricLoader.getInstance().isModLoaded(wood.requiredModId())) {
                continue;
            }
            PlankedChestBlock chest = new PlankedChestBlock(wood, () -> chestType,
                    chestProperties(wood.chestId()));
            PlankedTrappedChestBlock trapped = new PlankedTrappedChestBlock(wood, () -> trappedType,
                    chestProperties(wood.trappedChestId()));
            registerBlock(wood.chestId(), chest);
            registerBlock(wood.trappedChestId(), trapped);
            // The item only needs the block object, not the (still-null) block-entity types.
            registerItem(wood.chestId(), chest, Items.CHEST);
            registerItem(wood.trappedChestId(), trapped, Items.TRAPPED_CHEST);
            CHESTS.put(wood, chest);
            TRAPPED.put(wood, trapped);
            chestBlocks.add(chest);
            trappedBlocks.add(trapped);
        }

        chestType = registerBlockEntityType("chest",
                (pos, state) -> new PlankedChestBlockEntity(chestType, pos, state), chestBlocks);
        trappedType = registerBlockEntityType("trapped_chest",
                (pos, state) -> new PlankedTrappedChestBlockEntity(trappedType, pos, state),
                trappedBlocks);
        // Keep the real block entity in chunk packets for a client that runs this mod (its
        // BlockEntityRenderer needs one); strip it for everyone else, who see the display entity.
        registerSyncedBlockEntity(chestType);
        registerSyncedBlockEntity(trappedType);
    }

    private static void registerSyncedBlockEntity(BlockEntityType<?> type) {
        PolymerBlockUtils.registerBlockEntity(type,
                (t, context) -> PlankedChests.HANDSHAKE.supportsAll(context) ? t : null);
    }

    // Full copy of the vanilla chest settings, keyed under this mod's namespace.
    private static BlockBehaviour.Properties chestProperties(String path) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST)
                .setId(ResourceKey.create(Registries.BLOCK, PlankedChests.id(path)))
                // Vanilla derives this from the collision shape, which is never a full cube for a
                // chest. Polymer reports the barrier client-state's full-cube shape for non-player
                // collision queries, so the default predicate would make this a redstone conductor
                // and a chest directly below it would refuse to open (isBlockedChestByBlock).
                .isRedstoneConductor((state, level, pos) -> false);
    }

    private static void registerBlock(String path, Block block) {
        Registry.register(BuiltInRegistries.BLOCK, PlankedChests.id(path), block);
    }

    private static <T extends ChestBlockEntity> BlockEntityType<T> registerBlockEntityType(
            String path, BlockEntityType.BlockEntitySupplier<T> factory, List<Block> blocks) {
        BlockEntityType<T> type = new BlockEntityType<>(factory, Set.copyOf(blocks));
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, PlankedChests.id(path), type);
    }

    // A client running this mod and carrying this wood gets the real item (real name + model); a
    // client with only the resource pack sees a vanilla chest carrying the custom model (step 0 =
    // closed); everyone else sees a plain vanilla chest. The wood gate is not cosmetic: the real
    // item id is unresolvable on a client whose planked-chests never registered this wood.
    private static void registerItem(String path, Block block, Item fallback) {
        ItemRegistration.register(PlankedChests.MOD_ID, path, key -> new PolymerModelBlockItem(
                block, fallback, ChestModels.wholeModel(path),
                PlankedChests.HANDSHAKE::supportsAll,
                new Item.Properties().useBlockDescriptionPrefix().setId(key)));
    }
}
