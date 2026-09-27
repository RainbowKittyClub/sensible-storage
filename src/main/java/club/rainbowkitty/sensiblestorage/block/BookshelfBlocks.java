package club.rainbowkitty.sensiblestorage.block;

import java.util.LinkedHashMap;
import java.util.Map;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.kalic0re.common.item.PolymerModelBlockItem;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Registers a bookshelf per {@link WoodType} but oak, with its block item. Vanilla's own bookshelf
 * is already oak, and the tinted oak frame reproduces it to within a few levels per channel, so an
 * oak one here would only be a second copy of it.
 */
public final class BookshelfBlocks {
    private static final Map<WoodType, PlankedBookshelfBlock> BOOKSHELVES = new LinkedHashMap<>();

    private BookshelfBlocks() {}

    /** All registered bookshelves, wood order; never one for oak. */
    public static Map<WoodType, PlankedBookshelfBlock> bookshelves() {
        return BOOKSHELVES;
    }

    /**
     * Builds and registers a bookshelf and its item for every wood but oak that
     * {@link ChestBlocks#init()} registered a chest for, so call after it — the same reuse, and for
     * the same reasons, as {@link CraftingTableBlocks#init()}.
     */
    public static void init() {
        for (WoodType wood : ChestBlocks.chests().keySet()) {
            if (wood == WoodType.OAK) {
                continue;
            }
            String path = wood.bookshelfId();
            // A note block state Polymer sets aside and the pack redraws with this shelf's model.
            BlockState packState = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
                    PolymerBlockModel.of(SensibleStorage.id("block/" + path)));
            PlankedBookshelfBlock shelf = new PlankedBookshelfBlock(wood, packState,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF)
                            .setId(ResourceKey.create(Registries.BLOCK, SensibleStorage.id(path))));
            Registry.register(BuiltInRegistries.BLOCK, SensibleStorage.id(path), shelf);
            // Vanilla's own entry (FireBlock#bootStrap) is by block, so it does not reach these.
            FlammableBlockRegistry.getDefaultInstance().add(shelf, 30, 20);
            registerItem(path, shelf, Items.BOOKSHELF);
            BOOKSHELVES.put(wood, shelf);
        }
    }

    // As CraftingTableBlocks#registerItem: the real item for a client running this mod, the
    // vanilla item drawn with this block's model for one with the pack, the plain one otherwise.
    private static void registerItem(String path, Block block, Item fallback) {
        ItemRegistration.register(SensibleStorage.MOD_ID, path, key -> new PolymerModelBlockItem(
                block, fallback, SensibleStorage.id(path), SensibleStorage.HANDSHAKE::supportsAll,
                new Item.Properties().useBlockDescriptionPrefix().setId(key)));
    }
}
