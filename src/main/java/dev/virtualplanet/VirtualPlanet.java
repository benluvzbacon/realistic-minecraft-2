package dev.virtualplanet;

import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.config.PlanetConfigManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Common (client and server) entry point of <b>Virtual Planet: Local Skies</b>.
 *
 * <p>The mod never changes the Minecraft world itself: it only reinterprets horizontal coordinates as
 * positions on a virtual spherical planet and lets each client compute the local astronomical view of its
 * own player. The server owns the authoritative configuration and the shared world time.
 */
public class VirtualPlanet implements ModInitializer {
    public static final String MOD_ID = "virtualplanet";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        List<String> warnings = PlanetConfigManager.load();
        PlanetConfig config = PlanetConfigManager.get();
        LOGGER.info("Virtual Planet: Local Skies initialised (enabled={}, gameDirectory={})",
                config.enabled, config.debug ? config.toDebugString() : "debug output off");
        if (!warnings.isEmpty()) {
            LOGGER.warn("Virtual Planet configuration reported {} warning(s); see the log above", warnings.size());
        }
    }

    /** Namespaced identifier helper. */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
