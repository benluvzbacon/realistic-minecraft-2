package dev.virtualplanet.math;

/**
 * Angle helpers used throughout the virtual planet.
 *
 * <p>Two representations are used consistently:
 * <ul>
 *   <li><b>turns</b> - a fraction of a full revolution, wrapped into {@code [0, 1)}.</li>
 *   <li><b>radians</b> - standard radians, wrapped into {@code [0, 2*PI)} or {@code [-PI, PI)}.</li>
 * </ul>
 *
 * <p>In turn space the value {@code 0.0} means "sun at the zenith / solar noon" for sky angles, which
 * matches Minecraft's own {@code skyAngle} convention (Minecraft's own day starts at 06:00, which is
 * turn {@code 0.75} in this convention).
 */
public final class Angles {
    /** A full revolution in radians. */
    public static final double TAU = Math.PI * 2.0;

    private Angles() {
    }

    /** Wraps a turn value into {@code [0, 1)}. Handles negatives, NaN-free infinities and huge values. */
    public static double wrapTurns(double turns) {
        if (Double.isNaN(turns)) {
            return 0.0;
        }
        double wrapped = turns - Math.floor(turns);
        // Math.floor() of an infinite value is infinite, so the subtraction can yield NaN.
        if (Double.isNaN(wrapped)) {
            return 0.0;
        }
        return wrapped >= 1.0 ? 0.0 : wrapped;
    }

    /** Wraps a turn value into {@code [-0.5, 0.5)}. */
    public static double wrapTurnsSigned(double turns) {
        double wrapped = wrapTurns(turns);
        return wrapped >= 0.5 ? wrapped - 1.0 : wrapped;
    }

    /** Wraps radians into {@code [0, 2*PI)}. */
    public static double wrapRadians(double radians) {
        return wrapTurns(radians / TAU) * TAU;
    }

    /** Wraps radians into {@code [-PI, PI)}. */
    public static double wrapRadiansSigned(double radians) {
        return wrapTurnsSigned(radians / TAU) * TAU;
    }

    public static double turnsToRadians(double turns) {
        return turns * TAU;
    }

    public static double radiansToTurns(double radians) {
        return radians / TAU;
    }

    public static double degreesToRadians(double degrees) {
        return degrees * Math.PI / 180.0;
    }

    public static double radiansToDegrees(double radians) {
        return radians * 180.0 / Math.PI;
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return value < min ? min : (value > max ? max : value);
    }

    public static long clamp(long value, long min, long max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** Linearly interpolates between {@code a} and {@code b} using the raw (non-wrapped) shorter path in turns. */
    public static double lerpTurns(double a, double b, double delta) {
        double difference = wrapTurnsSigned(b - a);
        return wrapTurns(a + difference * delta);
    }
}
