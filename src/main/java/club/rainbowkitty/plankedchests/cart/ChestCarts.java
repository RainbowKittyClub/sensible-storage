package club.rainbowkitty.plankedchests.cart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopperCollection;

import club.rainbowkitty.plankedchests.PlankedChests;
import club.rainbowkitty.plankedchests.block.ChestBlocks;
import club.rainbowkitty.plankedchests.block.PlankedChestBlock;
import club.rainbowkitty.plankedchests.display.ChestModels;
import club.rainbowkitty.plankedchests.wood.WoodType;
import club.rainbowkitty.rkcore.common.item.ItemRegistration;
import club.rainbowkitty.rkcore.common.vehicle.CargoMinecarts;

/**
 * Registers the chest minecart — one entity type and one item, whatever the wood — and bridges
 * between a wood and the chest block a cart carries for it.
 *
 * <p>The bridge is this mod's whole remaining share of the cart. Everything generic lives in
 * {@code rkcore}'s {@code common.vehicle}, which carries a {@code Holder<Block>} and has no idea
 * what a wood is; the two directions here are what turn that back into a wood and vice versa.
 */
public final class ChestCarts {
    /** Item-model and lang path for the cart. */
    private static final String ID = "chest_minecart";

    /** Lang key for the one name pattern, "Minecart with %s", that every wood composes through. */
    public static final String NAME_PATTERN_KEY = "item." + PlankedChests.MOD_ID + ".minecart_with";

    /**
     * The wood-less variant's own name key — vanilla's wording, but not vanilla's key.
     *
     * <p>It borrowed {@code item.minecraft.chest_minecart} until the creative tab went in. The two
     * carts sit side by side there now, and telling them apart means renaming vanilla's, which a
     * shared key would have renamed both halves of.
     *
     * <p>What the borrowing bought was staying translated for a client that declined this server's
     * resource pack. {@link #PLAIN_NAME_FALLBACK} keeps the English of that, and such a client
     * never sees vanilla's cart renamed either, so for it the pair reads exactly as it did before.
     * What is genuinely lost is a non-English client that declines the pack, which now reads one
     * of the two in English — the same deal the 27 woods have taken since S2.
     */
    public static final String PLAIN_NAME_KEY = "item." + PlankedChests.MOD_ID + "." + ID;

    /** English for {@link #PLAIN_NAME_KEY}, which is also what the lang provider writes. */
    public static final String PLAIN_NAME_FALLBACK = "Minecart with Chest";

    /**
     * The blocks this cart will carry: chest-like ones, and nothing else.
     *
     * <p>A cart is a chest's inventory whatever it is drawn as, so a cargo that is not a container
     * of that shape produces a cart that lies about itself — {@code /summon} with a furnace made
     * one that looked like a furnace cart and held 27 slots. The list is a tag rather than a
     * method so that a datapack can extend it without this mod knowing about the block, which is
     * the same reason the cargo is a block holder rather than 27 item ids.
     *
     * <p>Written by {@code ChestCartCargoProvider}, which also says what is in it and why.
     */
    public static final TagKey<Block> CARGO_TAG =
            TagKey.create(Registries.BLOCK, PlankedChests.id(CargoMinecarts.CARGO_COMPONENT_PATH));

    private static EntityType<PlankedChestMinecart> type;
    private static ChestMinecartItem item;
    private static DataComponentType<Holder<Block>> cargoComponent;

    // Chest block back to the wood that made it, for the cart display. Built once from the same map
    // ChestBlocks registered, so a wood whose mod is absent is simply not in either.
    private static final Map<Block, WoodType> WOODS_BY_CHEST = new HashMap<>();

    // Copper chest block to the variant its cart draws, all eight onto ChestModels.COPPER's four.
    private static final Map<Block, String> COPPER_CHEST_VARIANTS = new HashMap<>();

    // The shulker boxes this build draws its own animated box for: vanilla's seventeen, taken from
    // the same two collections CargoCartTextureProvider generates from so the two cannot fall out
    // of step. Membership of BlockTags.SHULKER_BOXES would not do - a datapack can put anything in
    // a tag, and a cargo we drew nothing for is left to vanilla.
    private static final Set<Block> DRAWN_SHULKER_BOXES = new HashSet<>();

    // The cargoes this build drew a cart icon for. A superset of the boxes above, since the barrel
    // and the copper chests have icons without having a display of their own, and still not the
    // whole cargo tag: a cargo we drew nothing for needs the plain cart icon rather than a missing
    // model, which is what a datapack's own addition to the tag gets.
    private static final Set<Block> DRAWN_CARGO = new HashSet<>();

    private ChestCarts() {
    }

    /** The cart entity type. */
    public static EntityType<PlankedChestMinecart> type() {
        return type;
    }

    /** The cart item, whose cargo component decides which chest it carries. */
    public static ChestMinecartItem item() {
        return item;
    }

    /** The component a cart stack carries its chest block in. */
    public static DataComponentType<Holder<Block>> cargoComponent() {
        return cargoComponent;
    }

    /** Registers the component, the entity type and the item. */
    public static void init() {
        cargoComponent = CargoMinecarts.registerCargoComponent(PlankedChests.MOD_ID);
        ChestBlocks.chests().forEach((wood, block) -> WOODS_BY_CHEST.put(block, wood));
        DRAWN_SHULKER_BOXES.add(Blocks.SHULKER_BOX);
        Blocks.DYED_SHULKER_BOX.forEach(DRAWN_SHULKER_BOXES::add);
        DRAWN_CARGO.addAll(DRAWN_SHULKER_BOXES);
        DRAWN_CARGO.add(Blocks.BARREL);
        Blocks.COPPER_CHEST.forEach(DRAWN_CARGO::add);
        WeatheringCopperCollection.zipApply(Blocks.COPPER_CHEST, ChestModels.COPPER,
                COPPER_CHEST_VARIANTS::put);

        // Presented as an *empty* minecart, not a chest one, because a client told this is a chest
        // minecart draws a chest under our stand-in — observed, and not explained by any of the
        // code involved. Blanking the cargo slot to air does not stop it: a plain vanilla chest
        // minecart summoned with DisplayState air still shows its chest, so the cause is vanilla's
        // and neither Polymer's nor this mod's. A plain minecart has no cargo of its own to draw
        // and is otherwise the same entity to a client — one renderer, one body mesh
        // (LayerDefinitions.java:190 for every cart type), one texture, same hitbox, same 0.1875
        // passenger attachment — so nothing is given up. The interaction this costs is paid for in
        // CargoMinecartChest#interact.
        type = CargoMinecarts.register(PlankedChests.MOD_ID, ID,
                CargoMinecarts.containerCartBuilder(PlankedChestMinecart::new),
                EntityTypes.MINECART);
        item = ItemRegistration.register(PlankedChests.MOD_ID, ID, key -> new ChestMinecartItem(
                type, cargoComponent, new Item.Properties().stacksTo(1).setId(key)));

        // Not cosmetic parity: since S6 gave this item vanilla's chest + minecart recipe, a
        // dispenser that used to place a chest minecart is now loaded with this one, and without
        // this would drop it on the floor instead.
        CargoMinecarts.registerDispenseBehaviour(item, type);
    }

    /** The default cargo: vanilla's chest, which a cart with no component of its own carries. */
    public static Holder<Block> defaultCargo() {
        return Blocks.CHEST.builtInRegistryHolder();
    }

    /**
     * The chest block a wood's cart carries, or null when this server did not register one — which
     * happens for a wood whose own mod is absent.
     */
    public static @Nullable Holder<Block> cargoFor(WoodType wood) {
        PlankedChestBlock chest = ChestBlocks.chests().get(wood);
        return chest == null ? null : chest.builtInRegistryHolder();
    }

    /** The wood a cargo's chest is made of, or null for any block this mod did not register. */
    public static @Nullable WoodType woodOf(Holder<Block> cargo) {
        return WOODS_BY_CHEST.get(cargo.value());
    }

    /**
     * The chest id a copper chest cargo's cart draws, or null for any other cargo.
     *
     * <p>Eight blocks, four ids: a waxed chest draws as its unwaxed twin, which is what vanilla
     * does too — waxing stops a copper block ageing, it does not change how it looks.
     */
    public static @Nullable String copperChestId(Holder<Block> cargo) {
        String variant = COPPER_CHEST_VARIANTS.get(cargo.value());
        return variant == null ? null : ChestModels.chestId(variant);
    }

    /**
     * The cart icon for a cargo this build drew one for, or null for anything else — see
     * {@link #DRAWN_CARGO} for why that is not the same question as what the cargo tag holds.
     */
    public static @Nullable Identifier cartIcon(Holder<Block> cargo) {
        Block block = cargo.value();
        return DRAWN_CARGO.contains(block)
                ? ChestModels.cargoCartModel(BuiltInRegistries.BLOCK.getKey(block).getPath())
                : null;
    }

    /**
     * The cargo block's registry path when it is one of the seventeen shulker boxes this build
     * drew, or null for anything else — which is also the question "does this cart draw its own
     * animated box, or leave the cargo to vanilla".
     */
    public static @Nullable String shulkerBoxPath(Holder<Block> cargo) {
        Block box = cargo.value();
        return DRAWN_SHULKER_BOXES.contains(box)
                ? BuiltInRegistries.BLOCK.getKey(box).getPath()
                : null;
    }

    /** A cart item stack carrying the given wood's chest, or the vanilla chest for {@code null}. */
    public static ItemStack stack(@Nullable WoodType wood) {
        return item.stack(wood == null ? null : cargoFor(wood));
    }

    /**
     * Display name for a cart and its item, e.g. "Minecart with Birch Chest".
     *
     * <p>Composed by {@code rkcore} from the cargo block's own name, so the 27 woods need no naming
     * of their own beyond the chest names the blocks already carry. The one exception is vanilla's
     * chest, which is named outright: composing it would read "Minecart with Chest" via a pattern
     * this mod only ships in English, and this is the variant meant to pass for vanilla's.
     *
     * <p>Both live on the item, in {@link ChestMinecartItem#cartName}, so that every name a cart
     * wears comes from one method — the item's own, the entity's hover name and its menu title
     * alike. This is the one way in rather than a second copy of the rule.
     */
    public static Component displayName(@Nullable Holder<Block> cargo) {
        return item.cartName(cargo);
    }

    /**
     * Every cart this server can hand out, for the creative tab: the plain chest first, then one
     * per wood in {@link WoodType} order, then the seventeen shulker boxes.
     *
     * <p>Only woods this build registered a chest for, so a server without a wood's mod offers no
     * cart for it — the same gate {@code ChestBlocks#init} applies to the chests themselves. The
     * shulker carts need no such gate, being vanilla's own blocks, and come last because they are
     * the odd ones out: a chest cart is this mod's subject and a shulker cart is a guest.
     *
     * <p>Barrel and copper chest carts are deliberately absent. Both are craftable, but neither has
     * an icon of its own, so a tab entry for either would be a plain chest cart wearing someone
     * else's name — worse than not offering it, since the tab is also where a player learns what a
     * cart looks like. Give them icons and they belong here.
     *
     * <p>Each stack carries its own name and model, which no other cart stack has to. Everywhere
     * else a cart reaches a client through Polymer's item conversion, which reads both off the
     * server stack and writes them onto the client copy; a Polymer creative tab does not convert
     * at all, and sends the server stacks through vanilla's codec — which strips {@code cart_cargo}
     * on the way, since it is registered server-side-only. A client left to work the name out from
     * the stack it receives therefore has nothing to work from, and every entry reads "Minecart
     * with Chest" wearing vanilla's icon. Baking is the fix rather than syncing the component
     * because a tab's contents are cached once for all players, not built per connection.
     */
    public static List<ItemStack> carts() {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(presented(null));
        for (WoodType wood : WoodType.values()) {
            Holder<Block> cargo = cargoFor(wood);
            if (cargo != null) {
                stacks.add(presented(cargo));
            }
        }

        // The same two collections the icons and the recipes are generated from, in their own
        // order, so the tab reads the way vanilla's own shulker box run does.
        stacks.add(presented(Blocks.SHULKER_BOX.builtInRegistryHolder()));
        Blocks.DYED_SHULKER_BOX.forEach(box -> stacks.add(presented(box.builtInRegistryHolder())));
        return stacks;
    }

    // One tab entry: the ordinary cart stack, plus the name and icon it would have been given on
    // the way to a client. ITEM_NAME rather than CUSTOM_NAME, so the stack does not count as
    // renamed; no ITEM_MODEL at all for a cargo with no icon of its own, which leaves the fallback
    // item's model in place exactly as getPolymerItemModel would.
    private static ItemStack presented(@Nullable Holder<Block> cargo) {
        ItemStack stack = item.stack(cargo);
        stack.set(DataComponents.ITEM_NAME, displayName(cargo));
        Identifier model = item.cargoModel(cargo == null ? defaultCargo() : cargo);
        if (model != null) {
            stack.set(DataComponents.ITEM_MODEL, model);
        }
        return stack;
    }
}
