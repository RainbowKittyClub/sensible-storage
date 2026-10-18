package club.rainbowkitty.sensiblestorage.block;

import java.util.function.Supplier;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import club.rainbowkitty.sensiblestorage.SensibleStorage;
import club.rainbowkitty.sensiblestorage.display.ChestElementHolder;
import club.rainbowkitty.sensiblestorage.wood.WoodType;

/**
 * A wooden chest. Server-side it behaves exactly like a vanilla chest. A client that also runs this
 * mod (see {@link SensibleStorage#HANDSHAKE}) is sent the real block state and renders it with the
 * client {@code BlockEntityRenderer}; every other client is sent the vanilla chest as a stand-in,
 * which gives it the chest's outline, hitbox and collision, with the planked chest drawn over it by
 * a Polymer display entity wired in {@link club.rainbowkitty.sensiblestorage.display}.
 */
public class PlankedChestBlock extends ChestBlock
        implements PolymerBlock, BlockWithElementHolder, PolymerClientDecoded {
    private final WoodType wood;

    // The planks an onlooker's break particles are cut from. Resolved on first use: a modded
    // wood's planks may register after this block does.
    private @Nullable BlockState planks;

    /**
     * @param wood the wood this chest is made of
     * @param blockEntityType supplier of the shared block-entity type for this chest family; the
     *     supplier form is required because the type is registered after the block
     */
    public PlankedChestBlock(WoodType wood,
            Supplier<BlockEntityType<? extends ChestBlockEntity>> blockEntityType,
            BlockBehaviour.Properties properties) {
        super(blockEntityType, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
        this.wood = wood;
    }

    /** Returns the wood this chest is made of. */
    public WoodType wood() {
        return wood;
    }

    /**
     * Registry path (and translation-key suffix) for this chest, e.g. {@code dark_oak_chest}.
     * {@link PlankedTrappedChestBlock} overrides this with the {@code _trapped_chest} form.
     */
    public String registryPath() {
        return wood.chestId();
    }

    /**
     * Base chest sprite name for the client renderer, e.g. {@code dark_oak}.
     * {@link PlankedTrappedChestBlock} overrides this with the {@code _trapped} form. Matches the
     * sprites {@code ChestTextureProvider} writes under {@code textures/entity/chest/}.
     */
    public String spriteBase() {
        return wood.id();
    }

    /** Returns a plain chest block entity carrying this family's registered type. */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlankedChestBlockEntity(blockEntityType(), pos, state);
    }

    /**
     * Names a double chest "Large Birch Chest" instead of the vanilla "Large Chest". Vanilla's
     * combiner that supplies that name is private, so wrap its result: a null or single-chest
     * ({@link BlockEntity}) provider is left alone — a single's title comes from
     * {@link PlankedChestBlockEntity#getDefaultName()} — and only the double's unnamed fallback is
     * swapped, so a player-renamed half still wins.
     */
    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        MenuProvider provider = super.getMenuProvider(state, level, pos);
        if (provider == null || provider instanceof BlockEntity) {
            return provider;
        }
        return new MenuProvider() {
            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return provider.createMenu(id, inventory, player);
            }

            @Override
            public Component getDisplayName() {
                Component name = provider.getDisplayName();
                if (name.getContents() instanceof TranslatableContents contents
                        && "container.chestDouble".equals(contents.getKey())) {
                    return Component.translatable(
                            ChestBlocks.LARGE_CHEST_TITLE_KEY, wood.displayName());
                }
                return name;
            }
        };
    }

    /**
     * The vanilla block a client without the handshake is sent in this chest's place, with the same
     * {@code facing}, {@code type} and {@code waterlogged}. {@link PlankedTrappedChestBlock}
     * answers the trapped chest, so a client's pairing predictions split plain from trapped the way
     * the server's {@link #chestCanConnectTo} does.
     */
    protected Block standIn() {
        return Blocks.CHEST;
    }

    /**
     * A client that also runs this mod <em>and registered this wood</em> gets the real state (and
     * renders it itself); every other client gets the {@link #standIn()} carrying this state's
     * properties. Polymer also answers the server's own shape queries with that state, so items,
     * mobs and the hover ray meet a chest-shaped block too.
     */
    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return SensibleStorage.HANDSHAKE.supportsAll(context)
                ? state
                : standIn().withPropertiesOf(state);
    }

    /**
     * Every client predicts the chest's {@code SUCCESS} itself, the vanilla one through the
     * stand-in and the mod client through the real block, so a server-sourced swing would be a
     * second swing.
     */
    @Override
    public boolean isPolymerBlockInteraction(BlockState state, ServerPlayer player,
            InteractionHand hand, ItemStack stack, ServerLevel world, BlockHitResult blockHitResult,
            InteractionResult actionResult) {
        return false;
    }

    /**
     * Client and server agree on hardness (a full copy of the vanilla chest's) and tool (the axe
     * tag), so the client mines natively in either game mode and vanilla leaves the breaker out of
     * its own break event.
     */
    @Override
    public boolean handleMiningOnServer(ItemStack tool, BlockState state, BlockPos pos,
            ServerPlayer player) {
        return false;
    }

    /** An onlooker's break particles and sound come from this wood's planks, not the stand-in's. */
    @Override
    public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
        if (planks == null) {
            planks = BuiltInRegistries.BLOCK.getOptional(
                    Identifier.fromNamespaceAndPath(wood.planksNamespace(), wood.id() + "_planks"))
                    .orElse(Blocks.OAK_PLANKS)
                    .defaultBlockState();
        }
        return planks;
    }

    /**
     * The visible chest: a display entity bound to this block, for every client sent the stand-in.
     * It ticks only while its lid moves
     * ({@link club.rainbowkitty.sensiblestorage.display.ChestLids}), so Polymer never ticks it.
     */
    @Override
    public ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
        return new ChestElementHolder(world, pos, registryPath(),
                ChestElementHolder.Audience.STAND_IN, state);
    }
}
