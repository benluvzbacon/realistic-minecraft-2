package dev.virtualplanet.client;

import dev.virtualplanet.VirtualPlanet;
import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.config.PlanetConfigManager;
import dev.virtualplanet.net.PlanetConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entry point.
 *
 * <p>The local sky itself is computed lazily in {@link LocalSky} and applied by
 * {@code dev.virtualplanet.client.mixin.ClientWorldMixin}; this class wires the two things that are not
 * lazy: it loads the configuration the client falls back to, and it listens for the server's authoritative
 * configuration.
 *
 * <p>Leaving a server drops the synced configuration, so a client that later joins a vanilla server (or
 * plays single player) is back on its own config file instead of a stale copy of somebody else's planet.
 */
public class VirtualPlanetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PlanetConfig config = PlanetConfigManager.get();
        LocalSky.invalidate();
        VirtualPlanet.LOGGER.info(
                "Virtual Planet: Local Skies {} - circumference {}, rotation period {} ticks, latitude axis {}, "
                        + "seasons {}, local moon {}, local light map {}",
                config.enabled ? "enabled" : "disabled",
                config.deriveCircumferenceFromWorldBorder
                        ? "derived from the world border"
                        : config.circumferenceBlocks + " blocks",
                config.rotationPeriodTicks,
                config.latitudeAxis,
                config.seasonsEnabled ? "on" : "off",
                config.localMoonPhase ? "on" : "off",
                config.localSkyBrightness ? "on" : "off");

        ClientPlayNetworking.registerGlobalReceiver(PlanetConfigPayload.ID, (payload, context) -> {
            PlanetConfig synced = payload.decodeConfig();
            LocalSky.acceptServerConfig(synced);
            if (synced.debug) {
                VirtualPlanet.LOGGER.info("Virtual Planet: received configuration revision {} from the server",
                        payload.revision());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LocalSky.clearServerConfig();
            if (PlanetConfigManager.get().debug) {
                VirtualPlanet.LOGGER.info("Virtual Planet: disconnected, falling back to the local configuration");
            }
        });
    }
}
