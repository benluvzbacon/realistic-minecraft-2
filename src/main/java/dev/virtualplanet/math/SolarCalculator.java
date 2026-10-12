package dev.virtualplanet.math;

/**
 * Solar mathematics for the virtual planet, independent of Minecraft and of any rendering code.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Latitude {@code phi}: positive north, negative south.</li>
 *   <li>Declination {@code delta}: positive when the sun is over the northern hemisphere.</li>
 *   <li>Local solar time in turns: {@code 0} midnight, {@code 0.5} noon (see {@link PlanetTime#solarTimeTurns}).</li>
 *   <li>Azimuth: compass degrees, {@code 0} north, {@code 90} east, {@code 180} south, {@code 270} west.</li>
 *   <li>Minecraft axes: {@code +X} east, {@code +Z} south.</li>
 * </ul>
 *
 * <p>Standard spherical astronomy is used:
 * <pre>
 *   sin(altitude)  = sin(phi) sin(delta) + cos(phi) cos(delta) cos(H)
 *   azimuth        = atan2(sin(H), cos(H) sin(phi) - tan(delta) cos(phi))   // measured from south, westwards
 *   cos(H0)        = (sin(altitude0) - sin(phi) sin(delta)) / (cos(phi) cos(delta))
 * </pre>
 * where {@code H} is the hour angle ({@code 0} at solar noon).
 */
public final class SolarCalculator {
    /** Axial tilt of Earth, the default for the mod. */
    public static final double EARTH_AXIAL_TILT_DEGREES = 23.44;
    /** Civil twilight: sun six degrees below the horizon. */
    public static final double CIVIL_TWILIGHT_DEGREES = -6.0;
    /** Nautical twilight. */
    public static final double NAUTICAL_TWILIGHT_DEGREES = -12.0;

    private SolarCalculator() {
    }

    /**
     * Solar declination for a day of the year.
     *
     * <p>The year starts at the northern-hemisphere vernal equinox (declination {@code 0} and rising),
     * reaches {@code +axialTilt} at a quarter of the year, {@code 0} again at half and {@code -axialTilt}
     * at three quarters. Disable seasons (or use a zero axial tilt) to keep the sun on the celestial
     * equator all year.
     *
     * @param dayOfYear        days since the start of the planetary year (may be negative or fractional)
     * @param yearLengthDays   length of the planetary year; values {@code <= 0} disable seasons
     */
    public static double declinationRadians(double axialTiltRadians, double dayOfYear, double yearLengthDays,
                                            double seasonOffsetDays, boolean seasonsEnabled) {
        if (!seasonsEnabled || !(yearLengthDays > 0.0)) {
            return 0.0;
        }
        double phase = (dayOfYear + seasonOffsetDays) / yearLengthDays;
        return axialTiltRadians * Math.sin(Angles.TAU * phase);
    }

    /** Hour angle in radians for a solar time in turns; positive in the afternoon. */
    public static double hourAngleRadians(double localSolarTurns) {
        return Angles.TAU * (Angles.wrapTurns(localSolarTurns) - 0.5);
    }

    /**
     * Local apparent position of the sun.
     *
     * @param latitudeRadians     observer latitude, positive north
     * @param declinationRadians  solar declination
     * @param localSolarTurns     local solar time in turns ({@code 0.5} = local noon)
     */
    public static SolarPosition position(double latitudeRadians, double declinationRadians, double localSolarTurns) {
        double hourAngle = hourAngleRadians(localSolarTurns);
        double sinLatitude = Math.sin(latitudeRadians);
        double cosLatitude = Math.cos(latitudeRadians);
        double sinDeclination = Math.sin(declinationRadians);
        double cosDeclination = Math.cos(declinationRadians);

        double sinElevation = sinLatitude * sinDeclination + cosLatitude * cosDeclination * Math.cos(hourAngle);
        double elevation = Math.asin(Angles.clamp(sinElevation, -1.0, 1.0));

        // Azimuth measured from south, increasing westwards.
        double azimuthFromSouth = Math.atan2(
                Math.sin(hourAngle),
                Math.cos(hourAngle) * sinLatitude - Math.tan(declinationRadians) * cosLatitude);
        double azimuth = Angles.wrapRadians(azimuthFromSouth + Math.PI);

        return new SolarPosition(elevation, azimuth, hourAngle, declinationRadians);
    }

    /**
     * Position of the sun for a local sky angle (the value Minecraft's renderer uses).
     * {@code skyAngle == 0} is solar noon.
     */
    public static SolarPosition positionFromSkyAngle(double latitudeRadians, double declinationRadians,
                                                    double skyAngleTurns) {
        return position(latitudeRadians, declinationRadians, PlanetTime.solarTimeTurns(skyAngleTurns));
    }

    /**
     * Hour angle at which the sun crosses the given altitude, in radians in {@code [0, PI]}.
     * Returns {@code NaN} when the sun never reaches that altitude (polar day or polar night).
     */
    public static double hourAngleForAltitude(double latitudeRadians, double declinationRadians, double altitudeRadians) {
        double cosLatitude = Math.cos(latitudeRadians);
        double cosDeclination = Math.cos(declinationRadians);
        double denominator = cosLatitude * cosDeclination;
        if (Math.abs(denominator) < 1.0e-12) {
            return Double.NaN;
        }
        double cosHourAngle = (Math.sin(altitudeRadians) - Math.sin(latitudeRadians) * Math.sin(declinationRadians)) / denominator;
        if (cosHourAngle < -1.0 || cosHourAngle > 1.0) {
            return Double.NaN;
        }
        return Math.acos(cosHourAngle);
    }

    /** Local solar time (turns) at which the sun rises above the horizon, or {@code NaN} if it never does. */
    public static double sunriseSolarTurns(double latitudeRadians, double declinationRadians) {
        double hourAngle = hourAngleForAltitude(latitudeRadians, declinationRadians, 0.0);
        return Double.isNaN(hourAngle) ? Double.NaN : Angles.wrapTurns(0.5 - hourAngle / Angles.TAU);
    }

    /** Local solar time (turns) at which the sun sets, or {@code NaN} if it never does. */
    public static double sunsetSolarTurns(double latitudeRadians, double declinationRadians) {
        double hourAngle = hourAngleForAltitude(latitudeRadians, declinationRadians, 0.0);
        return Double.isNaN(hourAngle) ? Double.NaN : Angles.wrapTurns(0.5 + hourAngle / Angles.TAU);
    }

    /** Local solar time (turns) at which the sun reaches the given altitude in the morning. */
    public static double morningTwilightTurns(double latitudeRadians, double declinationRadians, double altitudeDegrees) {
        double hourAngle = hourAngleForAltitude(latitudeRadians, declinationRadians, Angles.degreesToRadians(altitudeDegrees));
        return Double.isNaN(hourAngle) ? Double.NaN : Angles.wrapTurns(0.5 - hourAngle / Angles.TAU);
    }

    /** Local solar time (turns) at which the sun leaves the given altitude in the evening. */
    public static double eveningTwilightTurns(double latitudeRadians, double declinationRadians, double altitudeDegrees) {
        double hourAngle = hourAngleForAltitude(latitudeRadians, declinationRadians, Angles.degreesToRadians(altitudeDegrees));
        return Double.isNaN(hourAngle) ? Double.NaN : Angles.wrapTurns(0.5 + hourAngle / Angles.TAU);
    }

    /** {@code true} when the sun stays above the horizon for the whole rotation (midnight sun). */
    public static boolean isPolarDay(double latitudeRadians, double declinationRadians) {
        // The sun is up all "night" when even its lowest altitude (hour angle PI) stays positive.
        return Math.sin(latitudeRadians) * Math.sin(declinationRadians)
                - Math.cos(latitudeRadians) * Math.cos(declinationRadians) > 0.0;
    }

    /** {@code true} when the sun never rises (polar night). */
    public static boolean isPolarNight(double latitudeRadians, double declinationRadians) {
        // The sun never rises when even its highest altitude (hour angle 0) stays non-positive.
        return Math.sin(latitudeRadians) * Math.sin(declinationRadians)
                + Math.cos(latitudeRadians) * Math.cos(declinationRadians) <= 0.0;
    }

    /** Length of the day in turns (0 = polar night, 1 = midnight sun). */
    public static double daylightFraction(double latitudeRadians, double declinationRadians) {
        double hourAngle = hourAngleForAltitude(latitudeRadians, declinationRadians, 0.0);
        if (Double.isNaN(hourAngle)) {
            return isPolarNight(latitudeRadians, declinationRadians) ? 0.0 : 1.0;
        }
        return hourAngle / Math.PI;
    }

    /**
     * Direction in which vanilla Minecraft's renderer draws the sun for a given sky angle.
     *
     * <p>Vanilla applies {@code rotateY(-90deg)} followed by {@code rotateX(skyAngle * 360deg)} to the
     * celestial matrix and draws the sun at {@code +Y} of that frame (verified against the 1.21.1
     * renderer), which places the sun on an east-west great circle that always passes through the
     * zenith. This method mirrors that geometry so the mod can be honest about how the rendered sun
     * relates to the astronomically computed one.
     */
    public static SolarPosition vanillaRenderedPosition(double skyAngleTurns) {
        double theta = Angles.turnsToRadians(Angles.wrapTurns(skyAngleTurns));
        // direction = (-sin(theta), cos(theta), 0): east-west circle through the zenith
        double up = Math.cos(theta);
        double elevation = Math.asin(Angles.clamp(up, -1.0, 1.0));
        double sinTheta = Math.sin(theta);
        double azimuth = sinTheta > 0.0 ? Angles.degreesToRadians(270.0) // setting in the west
                : (sinTheta < 0.0 ? Angles.degreesToRadians(90.0) : 0.0); // rising in the east
        return new SolarPosition(elevation, azimuth, 0.0, 0.0);
    }
}
