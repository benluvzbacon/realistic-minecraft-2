package dev.virtualplanet.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads and stores {@code config/virtualplanet.json}.
 *
 * <p>Deliberately defensive: a missing, unreadable, malformed or half-written file results in the
 * defaults plus warnings, never in a crash. On a dedicated server this is the authoritative copy that is
 * sent to every client.
 */
public final class PlanetConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("virtualplanet");
    /** File name inside the Fabric config directory. */
    public static final String FILE_NAME = "virtualplanet.json";

    private static volatile PlanetConfig current = PlanetConfig.defaults();

    private PlanetConfigManager() {
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** The configuration currently in use. Never null; do not mutate it, use {@link #edit()} or {@link #replace}. */
    public static PlanetConfig get() {
        return current;
    }

    /** Replaces the active configuration. The supplied object is copied and sanitised. */
    public static List<String> replace(PlanetConfig config) {
        PlanetConfig copy = config == null ? PlanetConfig.defaults() : config.copy();
        List<String> warnings = new ArrayList<>(copy.sanitize());
        current = copy;
        return warnings;
    }

    /** Returns a mutable copy of the active configuration. */
    public static PlanetConfig edit() {
        return current.copy();
    }

    /**
     * Reads the configuration file, creating it with defaults when missing.
     *
     * @return warnings about anything that had to be repaired or defaulted
     */
    public static synchronized List<String> load() {
        Path path = configPath();
        List<String> warnings = new ArrayList<>();
        if (!Files.exists(path)) {
            replace(PlanetConfig.defaults());
            save();
            warnings.add("no configuration file found, wrote defaults to " + path.getFileName());
            return warnings;
        }
        try {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            PlanetConfig parsed = PlanetConfigJson.fromJson(text, warnings);
            warnings.addAll(replace(parsed));
            LOGGER.info("Loaded {} ({} warnings)", path.getFileName(), warnings.size());
        } catch (IOException exception) {
            warnings.add("configuration file could not be read (" + exception.getMessage() + "), using defaults");
            replace(PlanetConfig.defaults());
        } catch (RuntimeException exception) {
            warnings.add("unexpected problem while reading the configuration (" + exception.getMessage() + "), using defaults");
            replace(PlanetConfig.defaults());
        }
        for (String warning : warnings) {
            LOGGER.warn("configuration: {}", warning);
        }
        return Collections.unmodifiableList(warnings);
    }

    /** Writes the active configuration to disk. IO problems are logged, never thrown. */
    public static synchronized void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, PlanetConfigJson.toJson(current).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            LOGGER.error("Could not write {}: {}", path, exception.getMessage());
        }
    }

    /** Reloads from disk (used by {@code /virtualplanet reload}). */
    public static synchronized List<String> reload() {
        return load();
    }
}
