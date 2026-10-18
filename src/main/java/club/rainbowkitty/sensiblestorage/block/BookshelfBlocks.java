package club.rainbowkitty.sensiblestorage.block;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import eu.pb4.polymer.blocks.api.MultiPolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.kalic0re.common.carrier.CarrierTiers;
import club.rainbowkitty.kalic0re.common.carrier.Carriers;
import club.rainbowkitty.kalic0re.common.item.ItemRegistration;
import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * Registers a bookshelf and a chiseled bookshelf per {@link WoodType} but oak, each with its block
 * item. Vanilla's own are already oak: the tinted oak frame reproduces vanilla's bookshelf to
 * within a few levels per channel, and vanilla's chiseled shelf gets the random books through this
 * mod's override of its blockstate, so oak ones here would only be second copies.
 */
public final class BookshelfBlocks {
    private static final Map<WoodType, PlankedBookshelfBlock> BOOKSHELVES = new LinkedHashMap<>();
    private static final Map<WoodType, PlankedChiseledBookshelfBlock> CHISELED =
            new LinkedHashMap<>();

    private BookshelfBlocks() {}

    /** All registered bookshelves, wood order; never one for oak. */
    public static Map<WoodType, PlankedBookshelfBlock> bookshelves() {
        return BOOKSHELVES;
    }

    /** All registered chiseled bookshelves, wood order; never one for oak. */
    public static Map<WoodType, PlankedChiseledBookshelfBlock> chiseledBookshelves() {
        return CHISELED;
    }

    /**
     * Builds and registers both shelves and their items for every wood but oak that
     * {@link ChestBlocks#init()} registered a chest for, so call after it — the same reuse, and for
     * the same reasons, as {@link CraftingTableBlocks#init()}.
     */
    public static void init() {
        for (WoodType wood : ChestBlocks.chests().keySet()) {
            if (wood == WoodType.OAK) {
                continue;
            }
            String path = wood.bookshelfId();
            // A note block state Polymer sets aside and the pack redraws with this shelf's model,
            // grouped with the anchor wood's shelf so a client's own flash of it still shows one.
            BlockState packState = Carriers.request(SensibleStorage.id(path), "",
                    CarrierTiers.BOOKSHELF,
                    PolymerBlockModel.of(SensibleStorage.id("block/" + path)));
            PlankedBookshelfBlock shelf = new PlankedBookshelfBlock(wood, packState,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF)
                            .setId(ResourceKey.create(Registries.BLOCK, SensibleStorage.id(path))));
            Registry.register(BuiltInRegistries.BLOCK, SensibleStorage.id(path), shelf);
            registerItem(path, shelf, Items.BOOKSHELF, new Item.Properties());
            BOOKSHELVES.put(wood, shelf);

            String chiseledPath = wood.chiseledBookshelfId();
            ResourceKey<Block> chiseledKey =
                    ResourceKey.create(Registries.BLOCK, SensibleStorage.id(chiseledPath));
            PlankedChiseledBookshelfBlock chiseled = new PlankedChiseledBookshelfBlock(wood,
                    requestChiseledStates(wood),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CHISELED_BOOKSHELF)
                            .setId(chiseledKey));
            Registry.register(BuiltInRegistries.BLOCK, SensibleStorage.id(chiseledPath), chiseled);
            // Vanilla's block entity, whose constructor names vanilla's type, and whose type
            // accepts only vanilla's shelf until told otherwise.
            ((FabricBlockEntityType) BlockEntityTypes.CHISELED_BOOKSHELF).addValidBlock(chiseled);
            // Vanilla's item carries an empty container, which pick-block with contents fills.
            registerItem(chiseledPath, chiseled, Items.CHISELED_BOOKSHELF, new Item.Properties()
                    .component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
            CHISELED.put(wood, chiseled);
        }
    }

    // A set-aside state per facing, drawn as the parts the wood's multipart blockstate draws for an
    // empty shelf facing that way: the body turned with uvlock, the six empty slots turned without.
    // Null unless Polymer gave all four, so no shelf's frame depends on which way it faces.
    private static @Nullable Map<Direction, BlockState> requestChiseledStates(WoodType wood) {
        Map<Direction, BlockState> states = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // The blockstate's y: 0 for north, then 90 a quarter-turn clockwise.
            int y = ((int) facing.toYRot() + 180) % 360;
            MultiPolymerBlockModel model = MultiPolymerBlockModel.of().with(PolymerBlockModel.of(
                    ChiseledBookshelfModels.body(wood.id()), 0, y, true));
            for (int slot = 0; slot < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size();
                    slot++) {
                model.with(PolymerBlockModel.of(
                        ChiseledBookshelfModels.emptySlot(wood.id(), slot), 0, y));
            }
            // Grouped by colour, so a client's own flash of it shows something alike.
            BlockState state = Carriers.request(SensibleStorage.id(wood.chiseledBookshelfId()),
                    "facing=" + facing.getSerializedName(), "", model);
            if (state == null) {
                return null;
            }
            states.put(facing, state);
        }
        return states;
    }

    // As CraftingTableBlocks#registerItem: the real item for a client running this mod, the
    // UnpinnedBlockItem client item drawn with this block's model for one with the pack, the
    // plain vanilla item otherwise.
    private static void registerItem(String path, Block block, Item fallback,
            Item.Properties properties) {
        ItemRegistration.register(SensibleStorage.MOD_ID, path, key -> new UnpinnedBlockItem(
                block, fallback, SensibleStorage.id(path), SensibleStorage.HANDSHAKE::supportsAll,
                properties.useBlockDescriptionPrefix().setId(key)));
    }
}
