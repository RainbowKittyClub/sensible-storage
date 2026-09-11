package club.rainbowkitty.plankedchests.cart;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChangeOverTimeBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Copper cargo ages in a cart the way it would out of one: it weathers where it stands, takes wax
 * from a honeycomb, and gives wax or oxidation back to an axe.
 *
 * <p>Written against the placed block rather than against the copper golem, which is vanilla's own
 * copper <em>entity</em> and the other model this could have followed. A golem ages on a wall clock
 * — seven hours a stage, whatever is around it — because it is a pet whose owner deserves time to
 * notice. A cart's cargo is a copper chest, and the comparison a player actually makes is with the
 * copper chest standing beside the rail, so this follows that one instead: the same random-tick
 * cadence, the same neighbour rule, the same {@code randomTickSpeed} to turn it off, and the same
 * refusal to age while someone is looking inside.
 *
 * <p>None of it is saved, because none of it is new state. Which weather state the cargo is in and
 * whether it is waxed are together just which of the eight copper chest blocks the cart carries,
 * and the cargo block is saved already — so ageing is a {@code setCargo} and waxing is a different
 * {@code setCargo}. A waxed cargo then stops ageing for free, by not being a
 * {@link ChangeOverTimeBlock} at all.
 *
 * <p>Nothing here names copper chests. Any cargo that ages is aged, which today is the eight copper
 * chests and tomorrow is whatever a datapack adds to {@link ChestCarts#CARGO_TAG} that vanilla
 * already knows how to weather.
 */
public final class CargoWeathering {
    // The chance a placed copper block takes its next weather state on a random tick. Transcribed
    // from ChangeOverTimeBlock#changeOverTime, where it is a local called
    // eachBlockOncePerDayChance rather than a constant this could read.
    private static final float AGE_CHANCE = 0.05688889f;

    // Blocks in a chunk section, which is the pool ServerLevel#tickChunk draws from: it picks
    // randomTickSpeed positions out of each 16x16x16 section every tick, so a given block's chance
    // of being random-ticked on a given tick is randomTickSpeed/4096.
    private static final int SECTION_BLOCKS = 16 * 16 * 16;

    private CargoWeathering() {
    }

    /**
     * Ages the cart's cargo one weather state, on the ticks a placed copy of it would have aged on.
     *
     * <p>Called every tick; the two rolls inside are what make that rare. The first stands in for
     * the random tick a block would have had to be picked for, and the second is
     * {@code ChangeOverTimeBlock}'s own per-tick chance. What happens after them — the scan for
     * copper within four blocks, which holds a cargo back while younger copper is beside it and
     * hurries it along while older copper is — is vanilla's own, called rather than copied.
     *
     * <p>A cargo someone has open never ages, matching
     * {@code WeatheringCopperChestBlock#randomTick} skipping a chest with a player inside it.
     * Rebuilding the stand-in under an open lid would be reason enough on its own; that it is also
     * what the block does makes it the rule rather than the workaround.
     *
     * @param cart the cart whose cargo may age
     * @param level the cart's level, which the neighbour scan reads blocks from
     */
    public static void age(PlankedChestMinecart cart, ServerLevel level) {
        if (cart.isCargoOpen()
                || !(cart.cargoOrDefault().value() instanceof ChangeOverTimeBlock<?> ageing)) {
            return;
        }

        RandomSource random = level.getRandom();
        if (!randomTicked(level, random) || random.nextFloat() >= AGE_CHANCE) {
            return;
        }
        ageing.getNextState(cart.cargoState(), level, cart.blockPosition(), random)
                .ifPresent(aged -> cart.setCargo(aged.getBlock().builtInRegistryHolder()));
    }

    /**
     * Answers a click carrying a honeycomb or an axe, or {@code null} for any click that is neither
     * — which is every click on a cargo that is not copper.
     *
     * <p>Sneaking is required, and that is the placed block's gate rather than an invention: an
     * ordinary right-click on a copper chest runs the block's own use and opens it, and only a
     * secondary-use click gets as far as the item's {@code useOn}. A cart is a container in the
     * same way, so an ordinary click still opens it and a honeycomb in hand never stands in the way
     * of that. It does mean a sneak-click with an axe scrapes instead of opening — which is also
     * what the block does, and is why the axe branch is deliberately last.
     *
     * @param cart the cart whose cargo is being waxed or scraped
     * @param player the player clicking, whose item is consumed or damaged
     * @param hand the hand that clicked, always the main one
     * @return the result to answer the click with, or null to let the cart answer it
     */
    public static @Nullable InteractionResult useTool(PlankedChestMinecart cart, Player player,
            InteractionHand hand) {
        if (!(cart.level() instanceof ServerLevel level) || !player.isSecondaryUseActive()) {
            return null;
        }

        ItemStack held = player.getItemInHand(hand);
        Block cargo = cart.cargoOrDefault().value();
        if (held.getItem() == Items.HONEYCOMB) {
            Block waxed = HoneycombItem.WAXABLES.get().get(cargo);
            if (waxed == null) {
                return null;
            }
            // No sound of our own: this is the one of the three level events that carries its
            // sound with it (LevelEventHandler:426-427), where the two axe events are particles
            // alone. Vanilla's HoneycombItem#useOn plays nothing either, for the same reason.
            replace(cart, level, waxed, null, LevelEvent.PARTICLES_AND_SOUND_WAX_ON);
            held.shrink(1);
            return InteractionResult.SUCCESS_SERVER;
        }

        if (!held.typeHolder().is(ItemTags.AXES)) {
            return null;
        }
        // Scrape before unwax, which is AxeItem#evaluateNewBlockState's order and matters: a waxed
        // block is in neither weathering map, so one click on waxed oxidized copper takes the wax
        // off and leaves the oxidation, and the next click takes the oxidation.
        Block scraped = WeatheringCopper.getPrevious(cargo).orElse(null);
        if (scraped != null) {
            replace(cart, level, scraped, SoundEvents.AXE_SCRAPE, LevelEvent.PARTICLES_SCRAPE);
        } else {
            Block unwaxed = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(cargo);
            if (unwaxed == null) {
                return null;
            }
            replace(cart, level, unwaxed, SoundEvents.AXE_WAX_OFF, LevelEvent.PARTICLES_WAX_OFF);
        }
        held.hurtAndBreak(1, player, hand.asEquipmentSlot());
        return InteractionResult.SUCCESS_SERVER;
    }

    // Whether this is a tick the cargo would have been random-ticked on had it been placed, game
    // rule and all - so /gamerule randomTickSpeed 0 stops a cart ageing exactly as it stops a wall.
    private static boolean randomTicked(ServerLevel level, RandomSource random) {
        int speed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        return speed > 0 && random.nextInt(SECTION_BLOCKS) < speed;
    }

    // Swaps the cargo and plays what a placed block would, to everyone including the player who
    // clicked. Vanilla's own calls pass that player here so their client does not play it twice,
    // but a vanilla client has no idea this entity is copper and predicted nothing to repeat.
    private static void replace(PlankedChestMinecart cart, ServerLevel level, Block cargo,
            @Nullable SoundEvent sound, int particles) {
        cart.setCargo(cargo.builtInRegistryHolder());
        if (sound != null) {
            level.playSound(null, cart, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        level.levelEvent(null, particles, cart.blockPosition(), 0);
    }
}
