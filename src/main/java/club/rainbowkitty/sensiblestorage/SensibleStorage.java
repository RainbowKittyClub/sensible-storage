package club.rainbowkitty.sensiblestorage;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import club.rainbowkitty.sensiblestorage.block.ChestBlocks;
import club.rainbowkitty.sensiblestorage.cart.ChestCarts;
import club.rainbowkitty.kalic0re.common.net.PolymerClientHandshake;

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
        ChestBlocks.init();
        ChestCarts.init();
        ChestBlocks.registerCreativeTab();
        BuiltinDataPacks.register();

        // Advertise the woods this installation actually registered. A client that registered a
        // different set — a different mix of the wood mods — is sent the fallback for the woods it
        // is missing, which it could otherwise not decode at all.
        ChestBlocks.chests().keySet().forEach(wood -> HANDSHAKE.register(wood.id()));

        PolymerResourcePackUtils.addModAssets(MOD_ID);
        LOGGER.info("Sensible Storage loaded");
    }
}
