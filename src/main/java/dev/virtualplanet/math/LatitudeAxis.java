package dev.virtualplanet.math;

/** Which horizontal Minecraft axis is interpreted as the planet's north-south (latitude) axis. */
public enum LatitudeAxis {
    /** No latitude mapping: every player is treated as standing on the planet's equator. */
    NONE,
    /** North-south latitude is derived from the world Z coordinate (default). */
    Z,
    /** North-south latitude is derived from the world X coordinate. */
    X;

    public static LatitudeAxis byName(String name, LatitudeAxis fallback) {
        if (name == null) {
            return fallback;
        }
        for (LatitudeAxis axis : values()) {
            if (axis.name().equalsIgnoreCase(name.trim())) {
                return axis;
            }
        }
        return fallback;
    }
}
