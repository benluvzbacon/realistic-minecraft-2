package dev.virtualplanet.math;

import java.util.Locale;

/**
 * Local apparent position of the sun for one observer.
 *
 * <p>Immutable value object. {@code elevation} is positive above the horizon, azimuth is a compass
 * bearing ({@code 0} north, {@code 90} east, {@code 180} south, {@code 270} west) and the hour angle is
 * zero at local solar noon.
 */
public final class SolarPosition {
    private final double elevationRadians;
    private final double azimuthRadians;
    private final double hourAngleRadians;
    private final double declinationRadians;

    public SolarPosition(double elevationRadians, double azimuthRadians, double hourAngleRadians, double declinationRadians) {
        this.elevationRadians = elevationRadians;
        this.azimuthRadians = Angles.wrapRadians(azimuthRadians);
        this.hourAngleRadians = hourAngleRadians;
        this.declinationRadians = declinationRadians;
    }

    public double elevationRadians() {
        return elevationRadians;
    }

    public double azimuthRadians() {
        return azimuthRadians;
    }

    public double hourAngleRadians() {
        return hourAngleRadians;
    }

    public double declinationRadians() {
        return declinationRadians;
    }

    public double elevationDegrees() {
        return Angles.radiansToDegrees(elevationRadians);
    }

    public double azimuthDegrees() {
        return Angles.radiansToDegrees(azimuthRadians);
    }

    public double declinationDegrees() {
        return Angles.radiansToDegrees(declinationRadians);
    }

    public boolean isAboveHorizon() {
        return elevationRadians > 0.0;
    }

    /** Converts the position into a unit vector in Minecraft's axes ({@code +X} east, {@code +Y} up, {@code +Z} south). */
    public Direction direction() {
        double horizontal = Math.cos(elevationRadians);
        double east = horizontal * Math.sin(azimuthRadians);
        double south = -horizontal * Math.cos(azimuthRadians);
        return new Direction(east, Math.sin(elevationRadians), south);
    }

    /** Azimuth as one of the eight compass points, for readable command output. */
    public String compassPoint() {
        String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round(azimuthDegrees() / 45.0) % 8;
        return points[index];
    }

    /** Unit direction in Minecraft axes. */
    public static final class Direction {
        private final double east;
        private final double up;
        private final double south;

        public Direction(double east, double up, double south) {
            this.east = east;
            this.up = up;
            this.south = south;
        }

        public double east() {
            return east;
        }

        public double up() {
            return up;
        }

        public double south() {
            return south;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "(east=%.4f, up=%.4f, south=%.4f)", east, up, south);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "elevation=%.2fdeg azimuth=%.2fdeg (%s) declination=%.2fdeg",
                elevationDegrees(), azimuthDegrees(), compassPoint(), declinationDegrees());
    }
}
