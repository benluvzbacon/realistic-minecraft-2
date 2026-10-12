package dev.virtualplanet.math;

/**
 * Mapping between Minecraft world time and the planet's rotation.
 *
 * <p>Minecraft's own day is {@code 24000} ticks long. Vanilla converts the time of day into a
 * {@code skyAngle} in {@code [0, 1)} with the (verified) formula used by
 * {@code net.minecraft.world.dimension.DimensionType#getSkyAngle(long)}:
 *
 * <pre>
 *   d          = fractionalPart(time / 24000.0 - 0.25)          // uniform rotation, in turns
 *   skyAngle   = (d * 2.0 + (0.5 - cos(d * PI) / 2.0)) / 3.0    // vanilla "long day, short night" easing
 * </pre>
 *
 * <p>Turn {@code 0.0} is solar noon, {@code 0.25} sunset, {@code 0.5} midnight and {@code 0.75} sunrise -
 * again matching vanilla ({@code skyAngle == 0} draws the sun at the zenith).
 *
 * <p>This mod keeps the shared world time untouched. The <b>only</b> change is the phase: every player's
 * celestial phase is shifted by their longitude offset. A configurable {@code rotationPeriodTicks} can
 * make a full revolution longer or shorter than one Minecraft day; by default it is {@code 24000}, which
 * reproduces vanilla timing exactly at the reference coordinate.
 */
public final class PlanetTime {
    /** Ticks in a vanilla Minecraft day. */
    public static final long VANILLA_DAY_TICKS = 24000L;

    private PlanetTime() {
    }

    /** Uniform rotation turns for a time, ignoring the planet's configured period. */
    public static double uniformTurns(long time, long rotationPeriodTicks) {
        long period = rotationPeriodTicks <= 0 ? VANILLA_DAY_TICKS : rotationPeriodTicks;
        return Angles.wrapTurns((double) time / (double) period - 0.25);
    }

    /** Vanilla's day/night easing, applied to uniform turns in {@code [0, 1)}. */
    public static double easedTurns(double uniformTurns) {
        double d = Angles.wrapTurns(uniformTurns);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return Angles.wrapTurns((d * 2.0 + e) / 3.0);
    }

    /**
     * Celestial phase of the shared world time at the reference coordinate, in turns.
     * Identical to vanilla {@code DimensionType.getSkyAngle(time)} whenever
     * {@code rotationPeriodTicks == 24000}.
     */
    public static double skyAngleTurns(long time, long rotationPeriodTicks) {
        return easedTurns(uniformTurns(time, rotationPeriodTicks));
    }

    /**
     * Local celestial phase for a longitude offset.
     *
     * <p>The offset is a plain phase shift, taken from the longitude offset in turns
     * ({@code (x - referenceX) / circumference}), which is the same thing as the specification's
     * {@code localOffset = longitude / (2*PI) * rotationPeriod} expressed in turns instead of ticks.
     * Because it is a phase shift, opposite longitudes are exactly half a rotation apart regardless of
     * the configured rotation period.
     */
    public static double localSkyAngleTurns(long time, long rotationPeriodTicks, double longitudeOffsetTurns) {
        return Angles.wrapTurns(skyAngleTurns(time, rotationPeriodTicks) + longitudeOffsetTurns);
    }

    /**
     * Local solar time in turns: {@code 0.0} midnight, {@code 0.25} sunrise, {@code 0.5} noon,
     * {@code 0.75} sunset.
     */
    public static double solarTimeTurns(double skyAngleTurns) {
        return Angles.wrapTurns(skyAngleTurns + 0.5);
    }

    /** Human readable clock string for a solar time in turns (00:00 - 23:59). */
    public static String formatSolarTime(double solarTimeTurns) {
        double hours = Angles.wrapTurns(solarTimeTurns) * 24.0;
        int hour = (int) Math.floor(hours);
        int minute = (int) Math.floor((hours - hour) * 60.0);
        return String.format("%02d:%02d", hour, minute);
    }
}
