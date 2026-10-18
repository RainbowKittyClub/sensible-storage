package club.rainbowkitty.sensiblestorage.cart;

import java.util.HashSet;
import java.util.Set;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.sensiblestorage.display.ChestModels;

/**
 * Gives every cart a client sees as vanilla's {@code minecraft:chest_minecart} a
 * {@link VanillaCartDisplay}: vanilla's own chest minecart however it was made, and Lootr's loot
 * cart, which LootrPolymer sends as one. This mod's own cart draws its cargo itself.
 */
public final class VanillaCarts {
    // Lootr's loot cart, by id: Lootr is never a dependency of this mod.
    private static final Identifier LOOTR_CART =
            Identifier.fromNamespaceAndPath("lootr", "chest_minecart");

    // The cart types drawn here, filled once every mod has registered.
    private static final Set<EntityType<?>> TYPES = new HashSet<>();

    /** The holder slot every cart carries, added by {@code AbstractMinecartMixin}. */
    public interface Carrier {
        /** The chest drawn on this cart, or null. */
        @Nullable VanillaCartDisplay sensiblestorage$display();

        /** Sets the chest drawn on this cart. */
        void sensiblestorage$setDisplay(@Nullable VanillaCartDisplay display);
    }

    private VanillaCarts() {}

    /** Draws the chest of every such cart as it loads, until it unloads. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            TYPES.add(EntityTypes.CHEST_MINECART);
            BuiltInRegistries.ENTITY_TYPE.getOptional(LOOTR_CART).ifPresent(TYPES::add);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof AbstractMinecart cart && TYPES.contains(cart.getType())) {
                attach(cart);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Carrier carrier && carrier.sensiblestorage$display() != null) {
                carrier.sensiblestorage$display().destroy();
                carrier.sensiblestorage$setDisplay(null);
            }
        });
    }

    /** From the end of every cart's tick: ticks its chest, if it has one. */
    public static void cartTicked(AbstractMinecart cart) {
        VanillaCartDisplay display = ((Carrier) cart).sensiblestorage$display();
        if (display != null) {
            display.cartTicked();
        }
    }

    /**
     * A menu on {@code container} opened ({@code delta} 1) or closed (-1); its lid follows if it is
     * a cart drawn here. Lootr's per-player inventory reports its cart instead of itself.
     */
    public static void openersChanged(@Nullable Container container, int delta) {
        if (container instanceof Carrier carrier && carrier.sensiblestorage$display() != null) {
            carrier.sensiblestorage$display().openersChanged(delta);
        }
    }

    // Binds a display to the cart, unless its cargo is no vanilla chest (a command can set any).
    private static void attach(AbstractMinecart cart) {
        String chestId = chestId(cart.getDisplayBlockState());
        if (chestId == null || ((Carrier) cart).sensiblestorage$display() != null) {
            return;
        }
        VanillaCartDisplay display = new VanillaCartDisplay(cart, chestId);
        ((Carrier) cart).sensiblestorage$setDisplay(display);
        EntityAttachment.of(display, cart);
        // A client already tracking the cart learns of the new riders only from a fresh list.
        display.sendPacket(new ClientboundSetPassengersPacket(cart));
    }

    // The vanilla chest id drawing a cart's cargo, or null when a client is shown no vanilla chest;
    // Lootr's cart carries Lootr's chest, which LootrPolymer shows as vanilla's.
    private static @Nullable String chestId(BlockState state) {
        BlockState cargo = PolymerBlockUtils.getPolymerBlockState(state, null);
        if (cargo.is(Blocks.CHEST)) {
            return ChestModels.VANILLA_CHEST_ID;
        }
        return cargo.is(Blocks.TRAPPED_CHEST) ? ChestModels.VANILLA_TRAPPED_CHEST_ID : null;
    }
}
