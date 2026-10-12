package dev.virtualplanet.config;

import dev.virtualplanet.math.LatitudeAxis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts {@link PlanetConfig} to and from a flat JSON object.
 *
 * <p>All reads are defensive: unknown keys are ignored, wrong types fall back to the default and the
 * result is always passed through {@link PlanetConfig#sanitize()}. A hand-edited configuration file with
 * a typo therefore cannot crash the game.
 */
public final class PlanetConfigJson {
    private PlanetConfigJson() {
    }

    public static Map<String, Object> toMap(PlanetConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", PlanetConfig.SCHEMA_VERSION);
        map.put("enabled", config.enabled);
        map.put("deriveCircumferenceFromWorldBorder", config.deriveCircumferenceFromWorldBorder);
        map.put("circumferenceBlocks", config.circumferenceBlocks);
        map.put("borderCircumferenceFactor", config.borderCircumferenceFactor);
        map.put("referenceFollowsWorldBorderCenter", config.referenceFollowsWorldBorderCenter);
        map.put("referenceX", config.referenceX);
        map.put("referenceZ", config.referenceZ);
        map.put("referenceLongitudeDegrees", config.referenceLongitudeDegrees);
        map.put("rotationPeriodTicks", config.rotationPeriodTicks);
        map.put("smoothCelestialMotion", config.smoothCelestialMotion);
        map.put("localMoonPhase", config.localMoonPhase);
        map.put("latitudeAxis", config.latitudeAxis == null ? LatitudeAxis.Z.name() : config.latitudeAxis.name());
        map.put("latitudeRangeDegrees", config.latitudeRangeDegrees);
        map.put("axialTiltDegrees", config.axialTiltDegrees);
        map.put("seasonsEnabled", config.seasonsEnabled);
        map.put("yearLengthDays", config.yearLengthDays);
        map.put("seasonOffsetDays", config.seasonOffsetDays);
        map.put("localSkyBrightness", config.localSkyBrightness);
        map.put("debug", config.debug);
        return map;
    }

    public static String toJson(PlanetConfig config) {
        return SimpleJson.write(toMap(config));
    }

    /** Parses a configuration document. Returns the defaults plus warnings instead of throwing. */
    public static PlanetConfig fromJson(String json, List<String> warnings) {
        PlanetConfig config = PlanetConfig.defaults();
        Map<String, Object> map;
        try {
            map = SimpleJson.parseObject(json);
        } catch (RuntimeException exception) {
            warnings.add("configuration file could not be parsed (" + exception.getMessage() + "), using defaults");
            return config;
        }

        config.enabled = readBoolean(map, "enabled", config.enabled, warnings);
        config.deriveCircumferenceFromWorldBorder = readBoolean(map, "deriveCircumferenceFromWorldBorder",
                config.deriveCircumferenceFromWorldBorder, warnings);
        config.circumferenceBlocks = readDouble(map, "circumferenceBlocks", config.circumferenceBlocks, warnings);
        config.borderCircumferenceFactor = readDouble(map, "borderCircumferenceFactor",
                config.borderCircumferenceFactor, warnings);
        config.referenceFollowsWorldBorderCenter = readBoolean(map, "referenceFollowsWorldBorderCenter",
                config.referenceFollowsWorldBorderCenter, warnings);
        config.referenceX = readDouble(map, "referenceX", config.referenceX, warnings);
        config.referenceZ = readDouble(map, "referenceZ", config.referenceZ, warnings);
        config.referenceLongitudeDegrees = readDouble(map, "referenceLongitudeDegrees",
                config.referenceLongitudeDegrees, warnings);
        config.rotationPeriodTicks = readLong(map, "rotationPeriodTicks", config.rotationPeriodTicks, warnings);
        config.smoothCelestialMotion = readBoolean(map, "smoothCelestialMotion", config.smoothCelestialMotion, warnings);
        config.localMoonPhase = readBoolean(map, "localMoonPhase", config.localMoonPhase, warnings);
        config.latitudeAxis = LatitudeAxis.byName(readString(map, "latitudeAxis", config.latitudeAxis.name(), warnings),
                config.latitudeAxis);
        config.latitudeRangeDegrees = readDouble(map, "latitudeRangeDegrees", config.latitudeRangeDegrees, warnings);
        config.axialTiltDegrees = readDouble(map, "axialTiltDegrees", config.axialTiltDegrees, warnings);
        config.seasonsEnabled = readBoolean(map, "seasonsEnabled", config.seasonsEnabled, warnings);
        config.yearLengthDays = readDouble(map, "yearLengthDays", config.yearLengthDays, warnings);
        config.seasonOffsetDays = readDouble(map, "seasonOffsetDays", config.seasonOffsetDays, warnings);
        config.localSkyBrightness = readBoolean(map, "localSkyBrightness", config.localSkyBrightness, warnings);
        config.debug = readBoolean(map, "debug", config.debug, warnings);

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!toMap(PlanetConfig.defaults()).containsKey(entry.getKey())) {
                warnings.add("unknown configuration key '" + entry.getKey() + "' ignored");
            }
        }

        List<String> sanitizeWarnings = config.sanitize();
        warnings.addAll(new ArrayList<>(sanitizeWarnings));
        return config;
    }

    private static boolean readBoolean(Map<String, Object> map, String key, boolean fallback, List<String> warnings) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        warnings.add("'" + key + "' should be true or false, using " + fallback);
        return fallback;
    }

    private static double readDouble(Map<String, Object> map, String key, double fallback, List<String> warnings) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        warnings.add("'" + key + "' should be a number, using " + fallback);
        return fallback;
    }

    private static long readLong(Map<String, Object> map, String key, long fallback, List<String> warnings) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        warnings.add("'" + key + "' should be an integer, using " + fallback);
        return fallback;
    }

    private static String readString(Map<String, Object> map, String key, String fallback, List<String> warnings) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String) {
            return (String) value;
        }
        warnings.add("'" + key + "' should be a string, using " + fallback);
        return fallback;
    }
}
