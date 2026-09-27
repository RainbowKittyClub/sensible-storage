package club.rainbowkitty.sensiblestorage.block;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelBlockItem;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Registers a crafting table per {@link WoodType} but oak, with its block item, and the grid block
 * entity type those tables share with vanilla's. Vanilla's own crafting table is already oak, so an
 * oak one here would only be a second copy of it.
 */
public final class CraftingTableBlocks {
    private static final Map<WoodType, PlankedCraftingTableBlock> TABLES = new LinkedHashMap<>();

    private static BlockEntityType<CraftingTableBlockEntity> gridType;

    private CraftingTableBlocks() {}

    /** All registered crafting tables, wood order; never one for oak. */
    public static Map<WoodType, PlankedCraftingTableBlock> tables() {
        return TABLES;
    }

    /**
     * The grid's block entity type, valid for vanilla's crafting table and every planked one; null
     * before {@link #init()}.
     */
    public static BlockEntityType<CraftingTableBlockEntity> gridType() {
        return gridType;
    }

    /**
     * Builds and registers a table and its item for every wood but oak that
     * {@link ChestBlocks#init()} registered a chest for, so call after it. Reusing that set rather
     * than re-deriving it keeps its mod-presence and datagen gating in one place, and keeps the
     * woods this installation advertises over {@link SensibleStorage#HANDSHAKE} true of the tables
     * as well.
     */
    public static void init() {
        for (WoodType wood : ChestBlocks.chests().keySet()) {
            if (wood == WoodType.OAK) {
                continue;
            }
            String path = wood.craftingTableId();
            // A note block state Polymer sets aside and the pack redraws with this table's model.
            BlockState packState = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
                    PolymerBlockModel.of(SensibleStorage.id("block/" + path)));
            PlankedCraftingTableBlock table = new PlankedCraftingTableBlock(wood, packState,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)
                            .setId(ResourceKey.create(Registries.BLOCK, SensibleStorage.id(path))));
            Registry.register(BuiltInRegistries.BLOCK, SensibleStorage.id(path), table);
            registerItem(path, table);
            TABLES.put(wood, table);
        }
        registerGridType();
    }

    // One grid type for every table. No client is sent it, what a client sees of the grid being
    // its display: a handshake client's crafting table has no renderer for it either.
    private static void registerGridType() {
        Set<Block> blocks = new HashSet<>(TABLES.values());
        blocks.add(Blocks.CRAFTING_TABLE);
        gridType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                SensibleStorage.id("crafting_table"),
                new BlockEntityType<>(CraftingTableBlockEntity::new, Set.copyOf(blocks)));
        PolymerBlockUtils.registerBlockEntity(gridType);
    }

    // A client running this mod and carrying this wood gets the real item; a client with only the
    // resource pack sees a vanilla crafting table drawn with this table's own model; everyone else
    // sees a plain vanilla crafting table. The model id is the item's own, as for any block item.
    private static void registerItem(String path, PlankedCraftingTableBlock table) {
        ItemRegistration.register(SensibleStorage.MOD_ID, path, key -> new PolymerModelBlockItem(
                table, Items.CRAFTING_TABLE, SensibleStorage.id(path),
                SensibleStorage.HANDSHAKE::supportsAll,
                new Item.Properties().useBlockDescriptionPrefix().setId(key)));
    }
}
