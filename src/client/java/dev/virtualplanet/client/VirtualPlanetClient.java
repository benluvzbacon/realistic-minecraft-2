package dev.virtualplanet.client;

import dev.virtualplanet.VirtualPlanet;
import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.config.PlanetConfigManager;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entry point.
 *
 * <p>The local sky itself is computed lazily in {@link LocalSky} and applied by
 * {@code dev.virtualplanet.client.mixin.ClientWorldMixin}, so the initialiser only loads the
 * configuration and reports what the client will do.
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
    }
}
