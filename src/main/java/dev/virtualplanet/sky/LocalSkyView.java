package dev.virtualplanet.sky;

import dev.virtualplanet.math.Angles;
import dev.virtualplanet.math.PlanetTime;
import dev.virtualplanet.math.SolarPosition;

import java.util.Locale;

/**
 * A complete, immutable description of what one player should see in the sky at one instant.
 *
 * <p>Everything here is computed from the shared world time plus the player's coordinates, so it can be
 * unit tested without Minecraft and compared between players.
 */
public final class LocalSkyView {
    private final long sharedTime;
    private final double x;
    private final double z;
    private final double longitudeRadians;
    private final double latitudeRadians;
    private final double vanillaSkyAngleTurns;
    private final double localSkyAngleTurns;
    private final double solarTimeTurns;
    private final double declinationRadians;
    private final SolarPosition computedPosition;
    private final SolarPosition renderedPosition;
    private final int moonPhase;
    private final int vanillaMoonPhase;
    private final double celestialTiltDegrees;
    private final double sunriseTurns;
    private final double sunsetTurns;
    private final double civilDawnTurns;
    private final double civilDuskTurns;
    private final double daylightFraction;
    private final boolean polarDay;
    private final boolean polarNight;

    LocalSkyView(long sharedTime, double x, double z, double longitudeRadians, double latitudeRadians,
                 double vanillaSkyAngleTurns, double localSkyAngleTurns, double solarTimeTurns,
                 double declinationRadians, SolarPosition computedPosition, SolarPosition renderedPosition,
                 int moonPhase, int vanillaMoonPhase, double celestialTiltDegrees, double sunriseTurns,
                 double sunsetTurns, double civilDawnTurns, double civilDuskTurns, double daylightFraction,
                 boolean polarDay, boolean polarNight) {
        this.sharedTime = sharedTime;
        this.x = x;
        this.z = z;
        this.longitudeRadians = longitudeRadians;
        this.latitudeRadians = latitudeRadians;
        this.vanillaSkyAngleTurns = vanillaSkyAngleTurns;
        this.localSkyAngleTurns = localSkyAngleTurns;
        this.solarTimeTurns = solarTimeTurns;
        this.declinationRadians = declinationRadians;
        this.computedPosition = computedPosition;
        this.renderedPosition = renderedPosition;
        this.moonPhase = moonPhase;
        this.vanillaMoonPhase = vanillaMoonPhase;
        this.celestialTiltDegrees = celestialTiltDegrees;
        this.sunriseTurns = sunriseTurns;
        this.sunsetTurns = sunsetTurns;
        this.civilDawnTurns = civilDawnTurns;
        this.civilDuskTurns = civilDuskTurns;
        this.daylightFraction = daylightFraction;
        this.polarDay = polarDay;
        this.polarNight = polarNight;
    }

    public long sharedTime() {
        return sharedTime;
    }

    public double x() {
        return x;
    }

    public double z() {
        return z;
    }

    /** Longitude of the player's position, radians in {@code [-PI, PI)}. */
    public double longitudeRadians() {
        return longitudeRadians;
    }

    public double longitudeDegrees() {
        return Angles.radiansToDegrees(longitudeRadians);
    }

    public double latitudeRadians() {
        return latitudeRadians;
    }

    public double latitudeDegrees() {
        return Angles.radiansToDegrees(latitudeRadians);
    }

    /** The celestial phase every player would see if the mod were off. */
    public double vanillaSkyAngleTurns() {
        return vanillaSkyAngleTurns;
    }

    /** The celestial phase of this player: the value the renderer uses. */
    public double localSkyAngleTurns() {
        return localSkyAngleTurns;
    }

    /** Local solar time in turns: 0 midnight, 0.25 sunrise, 0.5 noon, 0.75 sunset. */
    public double solarTimeTurns() {
        return solarTimeTurns;
    }

    public String solarClock() {
        return PlanetTime.formatSolarTime(solarTimeTurns);
    }

    public double declinationDegrees() {
        return Angles.radiansToDegrees(declinationRadians);
    }

    /** Astronomically computed position (latitude, declination and hour angle). */
    public SolarPosition computedPosition() {
        return computedPosition;
    }

    /** Position vanilla geometry produces for {@link #localSkyAngleTurns()}. */
    public SolarPosition renderedPosition() {
        return renderedPosition;
    }

    /** Signed tilt of the rendered arc: {@code declination - latitude}. */
    public double celestialTiltDegrees() {
        return celestialTiltDegrees;
    }

    /** Local moon phase index (0 = full moon, 4 = new moon), or the vanilla index when the option is off. */
    public int moonPhase() {
        return moonPhase;
    }

    public int vanillaMoonPhase() {
        return vanillaMoonPhase;
    }

    /** Solar time of sunrise, or {@code NaN} during polar day/night. */
    public double sunriseTurns() {
        return sunriseTurns;
    }

    public double sunsetTurns() {
        return sunsetTurns;
    }

    public double civilDawnTurns() {
        return civilDawnTurns;
    }

    public double civilDuskTurns() {
        return civilDuskTurns;
    }

    /** Fraction of the rotation with the sun above the horizon (0 = polar night, 1 = midnight sun). */
    public double daylightFraction() {
        return daylightFraction;
    }

    public boolean isPolarDay() {
        return polarDay;
    }

    public boolean isPolarNight() {
        return polarNight;
    }

    public boolean isDaylight() {
        return computedPosition.isAboveHorizon();
    }

    private static String clock(double turns) {
        return Double.isNaN(turns) ? "n/a" : PlanetTime.formatSolarTime(turns);
    }

    /** Multi-line debug report used by {@code /virtualplanet debug} and the debug overlay. */
    public String toDebugString() {
        return String.format(Locale.ROOT,
                "sharedTime=%d x=%.2f z=%.2f%n"
                        + "  longitude=%.3f deg  latitude=%.3f deg%n"
                        + "  local solar time=%s  daylight fraction=%.3f%n"
                        + "  computed sun: elevation=%.2f deg azimuth=%.2f deg (%s)%n"
                        + "  rendered sun: elevation=%.2f deg azimuth=%.2f deg (%s)%n"
                        + "  celestial phase=%.6f turns (vanilla at this time: %.6f) tilt=%.2f deg%n"
                        + "  sunrise=%s sunset=%s  civil dawn=%s civil dusk=%s%n"
                        + "  moon phase=%d (vanilla %d)%s%s",
                sharedTime, x, z,
                longitudeDegrees(), latitudeDegrees(),
                solarClock(), daylightFraction,
                computedPosition.elevationDegrees(), computedPosition.azimuthDegrees(), computedPosition.compassPoint(),
                renderedPosition.elevationDegrees(), renderedPosition.azimuthDegrees(), renderedPosition.compassPoint(),
                localSkyAngleTurns, vanillaSkyAngleTurns, celestialTiltDegrees,
                clock(sunriseTurns), clock(sunsetTurns), clock(civilDawnTurns), clock(civilDuskTurns),
                moonPhase, vanillaMoonPhase,
                polarDay ? "  [polar day]" : "", polarNight ? "  [polar night]" : "");
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "LocalSkyView[lon=%.3f lat=%.3f solar=%s elevation=%.2f azimuth=%.2f phase=%.6f]",
                longitudeDegrees(), latitudeDegrees(), solarClock(),
                computedPosition.elevationDegrees(), computedPosition.azimuthDegrees(), localSkyAngleTurns);
    }
}
