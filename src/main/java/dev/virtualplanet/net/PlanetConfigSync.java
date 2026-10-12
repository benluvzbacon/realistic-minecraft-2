package dev.virtualplanet.net;

import dev.virtualplanet.VirtualPlanet;
import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.config.PlanetConfigManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.border.WorldBorder;

/**
 * Server side of the configuration sync.
 *
 * <p>The traffic model is deliberately boring:
 *
 * <ul>
 *   <li>the payload type is registered once, at mod initialisation;</li>
 *   <li>every player receives the configuration when they join;</li>
 *   <li>the world borders are checked <b>once per second</b> in the server tick, never per frame;</li>
 *   <li>a packet is sent only when the revision actually changed, and only to players whose client
 *       declared the channel ({@code ServerPlayNetworking.canSend}), so vanilla clients are untouched.</li>
 * </ul>
 *
 * <p>The mod never changes the world time or the world border: it only reads them.
 */
public final class PlanetConfigSync {
    /** Border checks per second; one is plenty and keeps the tick work negligible. */
    private static final int BORDER_CHECK_INTERVAL_TICKS = 20;

    private static long revision = 1L;
    private static long publishedRevision = Long.MIN_VALUE;
    private static long borderHash = Long.MIN_VALUE;
    private static int tickCounter;

    private PlanetConfigSync() {
    }

    /** Current configuration revision, also sent to the client. */
    public static long revision() {
        return revision;
    }

    /** Flags the configuration as changed; the next server tick publishes it once. */
    public static void markDirty() {
        revision++;
        publishedRevision = Long.MIN_VALUE;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(PlanetConfigPayload.ID, PlanetConfigPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendTo(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(PlanetConfigSync::onEndTick);
    }

    private static void onEndTick(MinecraftServer server) {
        if (++tickCounter >= BORDER_CHECK_INTERVAL_TICKS) {
            tickCounter = 0;
            long currentHash = borderHash(server);
            if (currentHash != borderHash) {
                borderHash = currentHash;
                markDirty();
                if (PlanetConfigManager.get().debug) {
                    VirtualPlanet.LOGGER.info("Virtual Planet: world border changed, resending configuration");
                }
            }
        }
        if (publishedRevision != revision) {
            publish(server);
        }
    }

    /** Sends the current configuration to every player that understands it. */
    private static void publish(MinecraftServer server) {
        PlanetConfig config = PlanetConfigManager.get();
        PlanetConfigPayload payload = new PlanetConfigPayload(config, revision);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, PlanetConfigPayload.ID)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
        publishedRevision = revision;
    }

    private static void sendTo(ServerPlayerEntity player) {
        if (player == null || !ServerPlayNetworking.canSend(player, PlanetConfigPayload.ID)) {
            return;
        }
        ServerPlayNetworking.send(player, new PlanetConfigPayload(PlanetConfigManager.get(), revision));
    }

    /** Cheap fingerprint of every world border the server knows about. */
    private static long borderHash(MinecraftServer server) {
        long hash = 17L;
        for (ServerWorld world : server.getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            if (border == null) {
                continue;
            }
            hash = hash * 31L + Double.doubleToLongBits(border.getCenterX());
            hash = hash * 31L + Double.doubleToLongBits(border.getCenterZ());
            hash = hash * 31L + Double.doubleToLongBits(border.getSize());
        }
        return hash;
    }
}
