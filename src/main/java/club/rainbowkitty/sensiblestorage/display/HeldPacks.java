package club.rainbowkitty.sensiblestorage.display;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.utils.PolymerUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import club.rainbowkitty.sensiblestorage.SensibleStorage;

/**
 * The proxy's word that a player already holds another server's pack, on a server that sends no
 * pack of its own: the lobby keeps survival's pack across a server switch, which draws vanilla's
 * chests invisible, so this server has to draw them for that player too.
 *
 * <p>rkcore-proxy sends {@code rkcore:held_pack} to a backend that pushed the player no pack, when
 * the player holds one another backend pushed (Velocity's applied packs). The pack's id is marked
 * held for Polymer, and so is this server's main pack, so every Polymer mod here treats the player
 * as holding it. The proxy drops the channel from clients, so only it can send this.
 */
public final class HeldPacks {
    /** The channel, shared with rkcore-proxy's {@code HeldPackVouch}. */
    public static final CustomPacketPayload.Type<Vouch> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("rkcore", "held_pack"));

    // Pack ids the proxy has vouched for since start; who holds one is Polymer's per-connection.
    private static final Set<UUID> VOUCHED = ConcurrentHashMap.newKeySet();

    /**
     * One vouch: the held pack's id and SHA-1, as the backend that pushed it gave them.
     *
     * @param packId the pack's id
     * @param hash the pack's SHA-1, 20 bytes
     */
    public record Vouch(UUID packId, byte[] hash) implements CustomPacketPayload {
        /** Two longs for the id, then a varint-prefixed hash. */
        public static final StreamCodec<FriendlyByteBuf, Vouch> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Vouch::packId,
                ByteBufCodecs.BYTE_ARRAY, Vouch::hash,
                Vouch::new);

        @Override
        public Type<Vouch> type() {
            return TYPE;
        }
    }

    private HeldPacks() {}

    /** Takes the vouch in configuration, where the proxy sends it, and late in play. */
    public static void register() {
        PayloadTypeRegistry.serverboundConfiguration().register(TYPE, Vouch.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TYPE, Vouch.CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(TYPE,
                (vouch, context) -> hold(vouch, context.packetContext()));
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (vouch, context) -> {
            hold(vouch, context.packetContext());
            // Chunks already sent passed their chest holders by; send them again.
            PolymerUtils.reloadWorld(context.player());
        });
    }

    /** Whether the proxy vouched that this player holds a pack drawing vanilla chests invisible. */
    public static boolean vouched(ServerGamePacketListenerImpl player) {
        for (UUID pack : VOUCHED) {
            if (PolymerResourcePackUtils.hasPack(player, pack)) {
                return true;
            }
        }
        return false;
    }

    // Marks the vouched pack, and this server's main pack, held on the player's connection.
    private static void hold(Vouch vouch, PacketContext context) {
        VOUCHED.add(vouch.packId());
        PolymerCommonUtils.setHasResourcePack(context, vouch.packId(), true);
        PolymerCommonUtils.setHasResourcePack(
                context, PolymerResourcePackUtils.getMainUuid(), true);
        SensibleStorage.LOGGER.debug("Proxy vouched for held pack {}", vouch.packId());
    }
}
