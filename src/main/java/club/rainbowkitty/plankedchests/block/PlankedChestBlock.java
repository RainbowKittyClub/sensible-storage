package club.rainbowkitty.plankedchests.block;

import java.util.function.Supplier;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.display.ChestElementHolder;
import club.rainbowkitty.plankedchests.wood.WoodType;

/**
 * A wooden chest. Server-side it behaves exactly like a vanilla chest. A client that also runs this
 * mod (see {@link PlankedChests#HANDSHAKE}) is sent the real block state and renders it with the
 * client {@code BlockEntityRenderer}; every other client sees an invisible barrier with the chest
 * drawn by a Polymer display entity wired in {@link club.rainbowkitty.plankedchests.display}.
 */
public class PlankedChestBlock extends ChestBlock
        implements PolymerBlock, BlockWithElementHolder, PolymerClientDecoded {
    private static final BlockState CLIENT_STATE = Blocks.BARRIER.defaultBlockState();

    private final WoodType wood;

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
     * A client that also runs this mod <em>and registered this wood</em> gets the real state (and
     * renders it itself); every other client gets the barrier block. Mining is server-side either
     * way because this is a PolymerBlock.
     */
    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return PlankedChests.HANDSHAKE.supportsAll(context) ? state : CLIENT_STATE;
    }

    /** The display entity samples light at its position, so push light updates for this block. */
    @Override
    public boolean forceLightUpdates(BlockState blockState) {
        return true;
    }

    /** The visible chest: a display entity bound to this block. */
    @Override
    public ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
        return new ChestElementHolder(world, pos, this, state);
    }

    /** Ticked so the display can follow the block entity's lid openness. */
    @Override
    public boolean tickElementHolder(ServerLevel world, BlockPos pos, BlockState state) {
        return true;
    }
}
