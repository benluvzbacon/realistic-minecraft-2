package dev.virtualplanet.client;

import dev.virtualplanet.VirtualPlanet;
import dev.virtualplanet.config.PlanetConfigManager;
import net.fabricmc.api.ClientModInitializer;

/** Client entry point: the local astronomical view is computed here from the synced configuration. */
public class VirtualPlanetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        VirtualPlanet.LOGGER.info("Virtual Planet client initialised (synced config: {})",
                PlanetConfigManager.get().enabled ? "enabled" : "disabled");
    }
}
