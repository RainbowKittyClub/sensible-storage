package club.rainbowkitty.sensiblestorage;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import club.rainbowkitty.kalic0re.common.label.HoverScanner;
import club.rainbowkitty.kalic0re.common.net.PolymerClientHandshake;
import club.rainbowkitty.sensiblestorage.armorstands.ArmorStandLabels;
import club.rainbowkitty.sensiblestorage.block.BookshelfBlocks;
import club.rainbowkitty.sensiblestorage.block.ChestBlocks;
import club.rainbowkitty.sensiblestorage.block.CraftingTableBlocks;
import club.rainbowkitty.sensiblestorage.block.CraftingTableClaims;
import club.rainbowkitty.sensiblestorage.block.CraftingTableGrids;
import club.rainbowkitty.sensiblestorage.block.CraftingTableRules;
import club.rainbowkitty.sensiblestorage.block.Flammability;
import club.rainbowkitty.sensiblestorage.books.BookText;
import club.rainbowkitty.sensiblestorage.books.ShelfLabels;
import club.rainbowkitty.sensiblestorage.cart.ChestCarts;
import club.rainbowkitty.sensiblestorage.frame.FrameLabels;
import club.rainbowkitty.sensiblestorage.recipe.PlankFallbackRecipe;

/** Mod entrypoint. */
public class SensibleStorage implements ModInitializer {
    public static final String MOD_ID = "sensiblestorage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Per-connection "does this client also run this mod" flag; drives real-vs-fallback render. */
    public static final PolymerClientHandshake HANDSHAKE = PolymerClientHandshake.create(MOD_ID);

    /** Builds a {@code sensiblestorage:<path>} identifier. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        HANDSHAKE.register();
        PlankFallbackRecipe.register();
        ChestBlocks.init();
        CraftingTableBlocks.init();
        CraftingTableRules.init();
        CraftingTableGrids.init();
        CraftingTableClaims.init();
        BookshelfBlocks.init();
        Flammability.register();
        ChestCarts.init();
        CreativeTabs.register();
        BuiltinDataPacks.register();

        // Advertise the woods this installation actually registered. A client that registered a
        // different set — a different mix of the wood mods — is sent the fallback for the woods it
        // is missing, which it could otherwise not decode at all.
        ChestBlocks.chests().keySet().forEach(wood -> HANDSHAKE.register(wood.id()));

        // Hover labels: one per-player raycast feeds every provider, so the scanner starts first
        // and each provider registers itself into it.
        HoverScanner.init();
        BookText.register();
        ShelfLabels.register();
        FrameLabels.register();
        ArmorStandLabels.register();

        PolymerResourcePackUtils.addModAssets(MOD_ID);
        LOGGER.info("Sensible Storage loaded");
    }
}
