package club.rainbowkitty.plankedchests.cart;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.plankedchests.display.ChestModels;
import club.rainbowkitty.rkcore.common.vehicle.CargoDisplayHolder;
import club.rainbowkitty.rkcore.common.vehicle.CargoMinecartChest;

/**
 * A chest minecart whose chest is made of one of this mod's woods.
 *
 * <p>Everything about carrying a block is {@link CargoMinecartChest}'s; what is left here is what
 * the block <em>means</em>. Which model the stand-in draws for it, the open count its lid follows,
 * the sound that opening makes, the facing a barrel is laid at, and — through
 * {@link CargoWeathering} — the fact that copper ages.
 */
public class PlankedChestMinecart extends CargoMinecartChest {
    // Menus open on this cart, since MinecartChest keeps no such count of its own and the lid has
    // to know. Deliberately not saved: a cart loads with nothing open, which is true.
    private int openCount;

    /**
     * @param type this mod's cart type, which every client is told is a plain vanilla minecart
     * @param level the level the cart is being created in
     */
    public PlankedChestMinecart(EntityType<? extends PlankedChestMinecart> type, Level level) {
        super(type, level, ChestCarts::item);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Waxing and scraping a copper cargo come first, and only for a sneaking player — see
     * {@link CargoWeathering#useTool}. Every other click is the container's, as it was.
     *
     * <p>Main hand only, which is {@link CargoMinecartChest#interact}'s own rule and is
     * load-bearing here rather than tidy: a click on a cart drawing a stand-in arrives once per
     * hand, and an axe answered twice would take two weather states off one scrape.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (hand == InteractionHand.MAIN_HAND) {
            InteractionResult copper = CargoWeathering.useTool(this, player, hand);
            if (copper != null) {
                return copper;
            }
        }
        return super.interact(player, hand, location);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Where a copper cargo ages. Every tick, because a block's random tick is rolled every tick
     * too; {@link CargoWeathering#age} is what makes it as rare as a placed block's.
     */
    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            CargoWeathering.age(this, level);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>A barrel is laid on its back, so that the cart carries an open top rather than an opening
     * pointing out of one side. Vanilla's default is {@code facing=north}, which is the state a
     * barrel is registered with rather than a considered choice for cargo — a placed barrel always
     * takes the direction it was placed from, and a cargo block has no such direction to take.
     *
     * <p>It also carries its own openness, which is the one cargo here whose animation is a block
     * state rather than something this mod draws. That makes this state change over the cart's
     * life, where every other cargo's is fixed — see {@link #refreshCargoState}, which is what
     * actually gets a change sent.
     */
    @Override
    public BlockState cargoState() {
        BlockState state = super.cargoState();
        return state.getBlock() instanceof BarrelBlock
                ? state.setValue(BarrelBlock.FACING, Direction.UP)
                        .setValue(BarrelBlock.OPEN, isCargoOpen())
                : state;
    }

    @Override
    public @Nullable CargoDisplayHolder createCargoDisplay() {
        Holder<Block> cargo = cargoOrDefault();
        String chestId = ChestCarts.plankedChestId(cargo);
        if (chestId != null) {
            return new ChestCartDisplay(this, chestId);
        }
        // Same bargain as the chest, for the same reason: vanilla's cargo renderer resolves a block
        // state and has nowhere to put an openness, so a box it drew would never open.
        String shulkerBox = ChestCarts.shulkerBoxPath(cargo);
        if (shulkerBox != null) {
            return new ShulkerCartDisplay(this, shulkerBox);
        }
        // A copper chest, for the same reason again. Vanilla does have these in its own cart-cargo
        // table, so one drawn its way is at least the right metal — but that table bakes the lid
        // shut, and a copper chest opens like any other.
        String copperChest = ChestCarts.copperChestId(cargo);
        if (copperChest != null) {
            return new ChestCartDisplay(this, copperChest);
        }
        // Vanilla's own chest art, drawn by us so that the plain look still gets a lid that opens —
        // vanilla's cargo renderer bakes a chest's openness in at model bake and cannot animate it.
        // Anything else is a block a client can draw for itself, so vanilla is left to draw it.
        return cargo.value() == Blocks.CHEST
                ? new ChestCartDisplay(this, ChestModels.VANILLA_CHEST_ID)
                : null;
    }

    /** The container menu's title and the cart's hover name, per chest rather than per cargo. */
    @Override
    protected Component getTypeName() {
        return ChestCarts.displayName(cargo());
    }

    /**
     * Whether anyone currently has this cart's cargo open — the one open/close signal everything
     * that animates with the cargo follows.
     *
     * <p>Deliberately about the cargo rather than about a chest. A placed container animates from
     * its own block entity: a chest tweens {@code ChestLidController} off {@code openCount}, and a
     * shulker box is told through a block event. A cart has neither — there is no block and no
     * block entity — so this count, kept by {@link #startOpen} and {@link #stopOpen}, is the only
     * thing a cart's cargo can open and close against, whatever that cargo is.
     */
    public boolean isCargoOpen() {
        return openCount > 0;
    }

    @Override
    public void startOpen(ContainerUser user) {
        super.startOpen(user);
        if (openCount++ == 0) {
            playLidSound(true);
            refreshCargoState();
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        super.stopOpen(user);
        if (openCount > 0 && --openCount == 0) {
            playLidSound(false);
            refreshCargoState();
        }
    }

    /**
     * Re-sends the cargo's block state, for the one cargo whose openness lives in it.
     *
     * <p>{@code CartCargoSupport} writes that state once, when the cargo is applied, because for
     * every other cargo it never changes again. A barrel's does, so opening the cart has to send it
     * — {@code setCustomDisplayBlockState} is synced entity data, and vanilla's cargo renderer
     * reads it each frame, so that is the whole of what a client needs.
     *
     * <p>Gated on the cargo rather than done unconditionally, and not a
     * {@code CartCargoSupport#refresh} either. For a cargo this mod draws itself the applied state
     * is deliberately {@code AIR}, blanking vanilla's cargo slot so only the stand-in is drawn, and
     * pushing the real block over that would draw the cargo twice. A refresh would avoid that but
     * rebuild the stand-in from scratch on every open, resetting a chest or shulker lid mid-swing.
     */
    private void refreshCargoState() {
        if (cargoOrDefault().value() instanceof BarrelBlock) {
            setCustomDisplayBlockState(Optional.of(cargoState()));
        }
    }

    /**
     * Creaks — or hisses — like a placed block of whatever cargo this cart carries.
     *
     * <p>An addition rather than a fix: a vanilla chest minecart is silent, since nothing in
     * {@code MinecartChest} or {@code ContainerEntity} plays anything and the lid it draws never
     * moves. This mod's cart lid does move, which is what makes the silence noticeable.
     *
     * <p>One call for every cargo, because the volume and the pitch jitter are the same in
     * {@code ChestBlockEntity#playSound} and {@code ShulkerBoxBlockEntity#startOpen} alike — half
     * volume, ±0.05 of pitch, on the block channel. Only the event differs, and that is
     * {@link #lidSound}'s to answer.
     */
    private void playLidSound(boolean opening) {
        SoundEvent sound = lidSound(cargoOrDefault().value(), opening);
        if (sound == null) {
            return;
        }
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.BLOCKS,
                0.5f, level().getRandom().nextFloat() * 0.1f + 0.9f);
    }

    /**
     * The pair a cargo opens and closes with, or null for a cargo that has none.
     *
     * <p>A chest is asked for its own, so a wood shipping custom chest sounds is heard through them
     * and so is vanilla's — copper chests included, since they are {@link ChestBlock}s too. Neither
     * a shulker box nor a barrel has such an accessor, so both are matched to the constants their
     * own block entities use.
     *
     * <p>Null is still reachable, for a cargo a datapack put in the tag that is none of these.
     * Silence beats guessing at a pair for a block this mod knows nothing about.
     */
    private static @Nullable SoundEvent lidSound(Block cargo, boolean opening) {
        if (cargo instanceof ChestBlock chest) {
            return opening ? chest.getOpenChestSound() : chest.getCloseChestSound();
        }
        if (cargo instanceof ShulkerBoxBlock) {
            return opening ? SoundEvents.SHULKER_BOX_OPEN : SoundEvents.SHULKER_BOX_CLOSE;
        }
        if (cargo instanceof BarrelBlock) {
            return opening ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE;
        }
        return null;
    }
}
