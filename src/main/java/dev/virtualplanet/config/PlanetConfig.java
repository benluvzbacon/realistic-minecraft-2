package dev.virtualplanet.config;

import dev.virtualplanet.math.Angles;
import dev.virtualplanet.math.LatitudeAxis;
import dev.virtualplanet.math.PlanetGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Validated configuration of the virtual planet.
 *
 * <p>Pure Java on purpose: the same object is used on the dedicated server (authoritative copy), sent to
 * clients over the network, and used by the client to compute the local sky. {@link #sanitize()} repairs
 * every field and returns human readable warnings, so a malformed configuration file can never crash the
 * game - the worst case is that a nonsense value is replaced by a default.
 */
public final class PlanetConfig {
    /** Version of this configuration layout; also written into the configuration file. */
    public static final int SCHEMA_VERSION = 1;

    /** Earth-like axial tilt, used as the default. */
    public static final double DEFAULT_AXIAL_TILT_DEGREES = 23.44;

    /** Smallest accepted rotation period (ticks) - one revolution per 12 seconds. */
    public static final long MIN_ROTATION_PERIOD_TICKS = 240L;
    /** Largest accepted rotation period (ticks) - roughly 13.9 real days per revolution. */
    public static final long MAX_ROTATION_PERIOD_TICKS = 24_000_000L;

    // --- master switch -------------------------------------------------------------------------
    /** Whether the virtual planet is active at all. When false the client renders vanilla skies. */
    public boolean enabled = true;

    // --- geometry ------------------------------------------------------------------------------
    /** Derive the circumference from the world border size (recommended, handles border resizing). */
    public boolean deriveCircumferenceFromWorldBorder = true;
    /** Explicit circumference in blocks. Used when not derived from the border, or before any border is known. */
    public double circumferenceBlocks = 0.0;
    /** Multiplier applied to the world border side length when deriving the circumference. */
    public double borderCircumferenceFactor = 1.0;
    /** Keep the reference coordinate on the world border centre (recommended). */
    public boolean referenceFollowsWorldBorderCenter = true;
    /** Reference X coordinate, the longitude-zero meridian. */
    public double referenceX = 0.0;
    /** Reference Z coordinate, the latitude-zero parallel when the latitude axis is Z. */
    public double referenceZ = 0.0;
    /** Longitude assigned to the reference coordinate, in degrees. */
    public double referenceLongitudeDegrees = 0.0;

    // --- planetary rotation --------------------------------------------------------------------
    /** Ticks for one full planetary rotation. 24000 keeps vanilla Minecraft timing exactly. */
    public long rotationPeriodTicks = 24000L;
    /** Interpolate the celestial phase between ticks for smoother motion than vanilla. */
    public boolean smoothCelestialMotion = true;
    /** Let the rendered moon phase follow the local sky angle (players see different moon phases). */
    public boolean localMoonPhase = true;

    // --- latitude, axial tilt and seasons ------------------------------------------------------
    /** Which horizontal axis carries latitude. */
    public LatitudeAxis latitudeAxis = LatitudeAxis.Z;
    /** Highest absolute latitude reached at the mapped extent (66.5 = polar circles on Earth). */
    public double latitudeRangeDegrees = 66.5;
    /** Axial tilt of the virtual planet, in degrees. */
    public double axialTiltDegrees = DEFAULT_AXIAL_TILT_DEGREES;
    /** Enable the seasonal cycle (solar declination changes over the planetary year). */
    public boolean seasonsEnabled = false;
    /** Length of the planetary year in Minecraft days. */
    public double yearLengthDays = 365.0;
    /** Shifts the seasonal cycle, in days. */
    public double seasonOffsetDays = 0.0;
    /**
     * EXPERIMENTAL: additionally tilt the rendered celestial arc by {@code latitude - declination} so that
     * the rendered sun matches the computed elevation and azimuth instead of always passing through the
     * zenith. See README - this changes the visible sky and is disabled by default.
     */
    public boolean renderLatitudeTilt = false;

    // --- atmosphere ----------------------------------------------------------------------------
    /**
     * Let the client's sky brightness (the light map) follow the local sky angle, so a player standing in
     * the local night gets night-coloured lighting while another player elsewhere still has daylight.
     * This is a client-side cosmetic effect only: block light levels and server logic stay shared.
     */
    public boolean localSkyBrightness = true;

    // --- diagnostics ---------------------------------------------------------------------------
    /** Verbose logging and the extra debug command output. */
    public boolean debug = false;

    public static PlanetConfig defaults() {
        return new PlanetConfig();
    }

    public PlanetConfig copy() {
        PlanetConfig copy = new PlanetConfig();
        copy.enabled = enabled;
        copy.deriveCircumferenceFromWorldBorder = deriveCircumferenceFromWorldBorder;
        copy.circumferenceBlocks = circumferenceBlocks;
        copy.borderCircumferenceFactor = borderCircumferenceFactor;
        copy.referenceFollowsWorldBorderCenter = referenceFollowsWorldBorderCenter;
        copy.referenceX = referenceX;
        copy.referenceZ = referenceZ;
        copy.referenceLongitudeDegrees = referenceLongitudeDegrees;
        copy.rotationPeriodTicks = rotationPeriodTicks;
        copy.smoothCelestialMotion = smoothCelestialMotion;
        copy.localMoonPhase = localMoonPhase;
        copy.latitudeAxis = latitudeAxis;
        copy.latitudeRangeDegrees = latitudeRangeDegrees;
        copy.axialTiltDegrees = axialTiltDegrees;
        copy.seasonsEnabled = seasonsEnabled;
        copy.yearLengthDays = yearLengthDays;
        copy.seasonOffsetDays = seasonOffsetDays;
        copy.renderLatitudeTilt = renderLatitudeTilt;
        copy.localSkyBrightness = localSkyBrightness;
        copy.debug = debug;
        return copy;
    }

    /**
     * Clamps/repairs every field and returns warnings describing what was changed. Never throws.
     */
    public List<String> sanitize() {
        List<String> warnings = new ArrayList<>();

        if (Double.isNaN(circumferenceBlocks) || Double.isInfinite(circumferenceBlocks)) {
            warnings.add("circumferenceBlocks was not a finite number, using the world border instead");
            circumferenceBlocks = 0.0;
        } else if (circumferenceBlocks != 0.0 && circumferenceBlocks < PlanetGeometry.MIN_CIRCUMFERENCE) {
            warnings.add("circumferenceBlocks (" + circumferenceBlocks + ") is smaller than the minimum "
                    + PlanetGeometry.MIN_CIRCUMFERENCE + ", clamped");
            circumferenceBlocks = PlanetGeometry.MIN_CIRCUMFERENCE;
        } else if (circumferenceBlocks > PlanetGeometry.MAX_CIRCUMFERENCE) {
            warnings.add("circumferenceBlocks (" + circumferenceBlocks + ") is larger than the maximum "
                    + PlanetGeometry.MAX_CIRCUMFERENCE + ", clamped");
            circumferenceBlocks = PlanetGeometry.MAX_CIRCUMFERENCE;
        }

        if (Double.isNaN(borderCircumferenceFactor) || Double.isInfinite(borderCircumferenceFactor)
                || borderCircumferenceFactor <= 0.0) {
            warnings.add("borderCircumferenceFactor must be a positive number, using 1.0");
            borderCircumferenceFactor = 1.0;
        } else if (borderCircumferenceFactor < 0.001) {
            warnings.add("borderCircumferenceFactor was below 0.001, clamped");
            borderCircumferenceFactor = 0.001;
        } else if (borderCircumferenceFactor > 1000.0) {
            warnings.add("borderCircumferenceFactor was above 1000, clamped");
            borderCircumferenceFactor = 1000.0;
        }

        double fixedReferenceX = finiteOr(referenceX, 0.0, "referenceX", warnings);
        double fixedReferenceZ = finiteOr(referenceZ, 0.0, "referenceZ", warnings);
        referenceX = fixedReferenceX;
        referenceZ = fixedReferenceZ;

        if (Double.isNaN(referenceLongitudeDegrees) || Double.isInfinite(referenceLongitudeDegrees)) {
            warnings.add("referenceLongitudeDegrees was not a finite number, using 0");
            referenceLongitudeDegrees = 0.0;
        } else if (referenceLongitudeDegrees < -180.0 || referenceLongitudeDegrees >= 180.0) {
            double wrapped = Angles.radiansToDegrees(Angles.wrapRadiansSigned(Angles.degreesToRadians(referenceLongitudeDegrees)));
            warnings.add("referenceLongitudeDegrees (" + referenceLongitudeDegrees + ") was wrapped to " + wrapped);
            referenceLongitudeDegrees = wrapped;
        }

        if (rotationPeriodTicks < MIN_ROTATION_PERIOD_TICKS) {
            warnings.add("rotationPeriodTicks (" + rotationPeriodTicks + ") is below the minimum "
                    + MIN_ROTATION_PERIOD_TICKS + ", clamped");
            rotationPeriodTicks = MIN_ROTATION_PERIOD_TICKS;
        } else if (rotationPeriodTicks > MAX_ROTATION_PERIOD_TICKS) {
            warnings.add("rotationPeriodTicks (" + rotationPeriodTicks + ") is above the maximum "
                    + MAX_ROTATION_PERIOD_TICKS + ", clamped");
            rotationPeriodTicks = MAX_ROTATION_PERIOD_TICKS;
        }

        if (latitudeAxis == null) {
            warnings.add("latitudeAxis was missing, using Z");
            latitudeAxis = LatitudeAxis.Z;
        }

        if (Double.isNaN(latitudeRangeDegrees) || Double.isInfinite(latitudeRangeDegrees)) {
            warnings.add("latitudeRangeDegrees was not a finite number, using 66.5");
            latitudeRangeDegrees = 66.5;
        } else if (latitudeRangeDegrees < 0.0 || latitudeRangeDegrees > 90.0) {
            warnings.add("latitudeRangeDegrees (" + latitudeRangeDegrees + ") must be between 0 and 90, clamped");
            latitudeRangeDegrees = Angles.clamp(latitudeRangeDegrees, 0.0, 90.0);
        }

        if (Double.isNaN(axialTiltDegrees) || Double.isInfinite(axialTiltDegrees)) {
            warnings.add("axialTiltDegrees was not a finite number, using " + DEFAULT_AXIAL_TILT_DEGREES);
            axialTiltDegrees = DEFAULT_AXIAL_TILT_DEGREES;
        } else if (axialTiltDegrees < -90.0 || axialTiltDegrees > 90.0) {
            warnings.add("axialTiltDegrees (" + axialTiltDegrees + ") must be between -90 and 90, clamped");
            axialTiltDegrees = Angles.clamp(axialTiltDegrees, -90.0, 90.0);
        }

        if (Double.isNaN(yearLengthDays) || Double.isInfinite(yearLengthDays)) {
            warnings.add("yearLengthDays was not a finite number, using 365");
            yearLengthDays = 365.0;
        } else if (yearLengthDays <= 0.0) {
            warnings.add("yearLengthDays must be positive, using 365 (seasons disabled)");
            yearLengthDays = 365.0;
            seasonsEnabled = false;
        } else if (yearLengthDays < 0.25) {
            warnings.add("yearLengthDays (" + yearLengthDays + ") is below 0.25, clamped");
            yearLengthDays = 0.25;
        } else if (yearLengthDays > 1_000_000.0) {
            warnings.add("yearLengthDays (" + yearLengthDays + ") is above 1000000, clamped");
            yearLengthDays = 1_000_000.0;
        }

        seasonOffsetDays = finiteOr(seasonOffsetDays, 0.0, "seasonOffsetDays", warnings);

        return warnings;
    }

    private static double finiteOr(double value, double fallback, String name, List<String> warnings) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            warnings.add(name + " was not a finite number, using " + fallback);
            return fallback;
        }
        return value;
    }

    // --- derived values ------------------------------------------------------------------------

    /** Circumference actually used, taking the world border into account. */
    public double effectiveCircumference(double borderSize) {
        if (deriveCircumferenceFromWorldBorder && borderSize > 0.0) {
            return borderSize * borderCircumferenceFactor;
        }
        if (circumferenceBlocks > 0.0) {
            return circumferenceBlocks;
        }
        if (borderSize > 0.0) {
            return borderSize * borderCircumferenceFactor;
        }
        return PlanetGeometry.MIN_CIRCUMFERENCE;
    }

    public double effectiveReferenceX(double borderCenterX) {
        return referenceFollowsWorldBorderCenter ? borderCenterX : referenceX;
    }

    public double effectiveReferenceZ(double borderCenterZ) {
        return referenceFollowsWorldBorderCenter ? borderCenterZ : referenceZ;
    }

    /** Half of the mapped north-south extent, normally half the world border. */
    public double latitudeHalfExtent(double borderSize) {
        double extent = borderSize > 0.0 ? borderSize * 0.5 : effectiveCircumference(borderSize) * 0.5;
        return extent > 0.0 ? extent : 1.0;
    }

    public double latitudeRangeRadians() {
        return Angles.degreesToRadians(latitudeRangeDegrees);
    }

    public double axialTiltRadians() {
        return Angles.degreesToRadians(axialTiltDegrees);
    }

    public double referenceLongitudeRadians() {
        return Angles.wrapRadiansSigned(Angles.degreesToRadians(referenceLongitudeDegrees));
    }

    /** Builds the geometry for the current world border state, with all validation applied. */
    public PlanetGeometry createGeometry(double borderCenterX, double borderCenterZ, double borderSize) {
        return new PlanetGeometry(
                effectiveCircumference(borderSize),
                effectiveReferenceX(borderCenterX),
                effectiveReferenceZ(borderCenterZ),
                referenceLongitudeRadians());
    }

    /**
     * Signed tilt of the rendered celestial arc, in degrees: {@code declination - latitude}.
     *
     * <p>Zero puts the noon sun at the zenith (vanilla behaviour); a non-zero value moves the arc so the
     * noon sun is {@code |tilt|} degrees away from the zenith, towards the southern horizon for positive
     * northern latitudes - the way it works on a real planet.
     */
    public double celestialTiltDegrees(double latitudeRadians, double declinationRadians) {
        return Angles.radiansToDegrees(declinationRadians - latitudeRadians);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlanetConfig)) {
            return false;
        }
        PlanetConfig that = (PlanetConfig) other;
        return enabled == that.enabled
                && deriveCircumferenceFromWorldBorder == that.deriveCircumferenceFromWorldBorder
                && Double.compare(circumferenceBlocks, that.circumferenceBlocks) == 0
                && Double.compare(borderCircumferenceFactor, that.borderCircumferenceFactor) == 0
                && referenceFollowsWorldBorderCenter == that.referenceFollowsWorldBorderCenter
                && Double.compare(referenceX, that.referenceX) == 0
                && Double.compare(referenceZ, that.referenceZ) == 0
                && Double.compare(referenceLongitudeDegrees, that.referenceLongitudeDegrees) == 0
                && rotationPeriodTicks == that.rotationPeriodTicks
                && smoothCelestialMotion == that.smoothCelestialMotion
                && localMoonPhase == that.localMoonPhase
                && latitudeAxis == that.latitudeAxis
                && Double.compare(latitudeRangeDegrees, that.latitudeRangeDegrees) == 0
                && Double.compare(axialTiltDegrees, that.axialTiltDegrees) == 0
                && seasonsEnabled == that.seasonsEnabled
                && Double.compare(yearLengthDays, that.yearLengthDays) == 0
                && Double.compare(seasonOffsetDays, that.seasonOffsetDays) == 0
                && renderLatitudeTilt == that.renderLatitudeTilt
                && localSkyBrightness == that.localSkyBrightness
                && debug == that.debug;
    }

    public int hashCode() {
        return Objects.hash(enabled, deriveCircumferenceFromWorldBorder, circumferenceBlocks, borderCircumferenceFactor,
                referenceFollowsWorldBorderCenter, referenceX, referenceZ, referenceLongitudeDegrees,
                rotationPeriodTicks, smoothCelestialMotion, localMoonPhase, latitudeAxis, latitudeRangeDegrees,
                axialTiltDegrees, seasonsEnabled, yearLengthDays, seasonOffsetDays, renderLatitudeTilt,
                localSkyBrightness, debug);
    }

    public String toDebugString() {
        return String.format(Locale.ROOT,
                "enabled=%s borderDerivedCircumference=%s circumference=%.1f factor=%.3f reference=(%.1f,%.1f) "
                        + "referenceLongitude=%.2fdeg rotationPeriod=%d ticks smooth=%s localMoonPhase=%s latitudeAxis=%s "
                        + "latitudeRange=%.2fdeg axialTilt=%.2fdeg seasons=%s yearLength=%.2fd seasonOffset=%.2fd "
                        + "renderLatitudeTilt=%s localSkyBrightness=%s debug=%s",
                enabled, deriveCircumferenceFromWorldBorder, circumferenceBlocks, borderCircumferenceFactor,
                referenceX, referenceZ, referenceLongitudeDegrees, rotationPeriodTicks, smoothCelestialMotion,
                localMoonPhase, latitudeAxis, latitudeRangeDegrees, axialTiltDegrees, seasonsEnabled, yearLengthDays,
                seasonOffsetDays, renderLatitudeTilt, localSkyBrightness, debug);
    }

    /** True when this configuration differs from the supplied one in any field that affects the sky. */
    public boolean skyRelevantDifference(PlanetConfig other) {
        return other == null || !equals(other);
    }
}
