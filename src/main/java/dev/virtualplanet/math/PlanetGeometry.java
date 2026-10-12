package dev.virtualplanet.math;

/**
 * Maps flat Minecraft coordinates onto a virtual spherical planet.
 *
 * <p>The Minecraft world stays flat and untouched; this class only *interprets* horizontal coordinates
 * as positions on a sphere of a configurable circumference. Longitude is derived from the player's
 * east-west coordinate and is the primary mapping (it is what makes two players see different skies):
 *
 * <pre>
 *   longitude        = referenceLongitude + 2*PI * (x - referenceX) / circumference
 *   radius           = circumference / (2*PI)
 *   longitudeOffset  = wrapSigned((x - referenceX) / circumference)   // in turns
 * </pre>
 *
 * <p>Latitude is a <b>separate, deliberately simple</b> mapping (see {@link #latitudeRadians}). A
 * rectangular world cannot be mapped onto a sphere without distortion: the linear mapping below keeps
 * distances along one axis intact and compresses everything towards the poles, so it must not be read
 * as a conformal projection. It is documented, configurable and can be disabled.
 */
public final class PlanetGeometry {
    /** Smallest accepted circumference (blocks). Roughly a 5-block-radius planet. */
    public static final double MIN_CIRCUMFERENCE = 16.0;
    /** Largest accepted circumference (blocks). Larger than any generated Minecraft world. */
    public static final double MAX_CIRCUMFERENCE = 1.0e9;

    private final double circumference;
    private final double referenceX;
    private final double referenceZ;
    private final double referenceLongitudeRadians;

    public PlanetGeometry(double circumference, double referenceX, double referenceZ, double referenceLongitudeRadians) {
        if (Double.isNaN(circumference) || Double.isInfinite(circumference)
                || circumference < MIN_CIRCUMFERENCE || circumference > MAX_CIRCUMFERENCE) {
            throw new IllegalArgumentException("circumference out of range: " + circumference);
        }
        if (Double.isNaN(referenceX) || Double.isInfinite(referenceX) || Double.isNaN(referenceZ) || Double.isInfinite(referenceZ)) {
            throw new IllegalArgumentException("reference coordinates must be finite");
        }
        this.circumference = circumference;
        this.referenceX = referenceX;
        this.referenceZ = referenceZ;
        this.referenceLongitudeRadians = Angles.wrapRadiansSigned(referenceLongitudeRadians);
    }

    /**
     * Builds a geometry whose circumference is derived from a (possibly non-zero-centred) world border.
     *
     * @param borderCenterX   centre of the world border on the X axis
     * @param borderCenterZ   centre of the world border on the Z axis
     * @param borderSize      side length of the world border
     * @param circumferenceFactor multiplier applied to {@code borderSize}; 1.0 makes one full revolution
     *                            equal one traversal of the border
     */
    public static PlanetGeometry fromWorldBorder(double borderCenterX, double borderCenterZ, double borderSize,
                                                 double circumferenceFactor, double referenceLongitudeRadians) {
        double circumference = borderSize * circumferenceFactor;
        return new PlanetGeometry(circumference, borderCenterX, borderCenterZ, referenceLongitudeRadians);
    }

    public double circumference() {
        return circumference;
    }

    /** Radius of the virtual planet in blocks. */
    public double radius() {
        return circumference / Angles.TAU;
    }

    public double referenceX() {
        return referenceX;
    }

    public double referenceZ() {
        return referenceZ;
    }

    public double referenceLongitudeRadians() {
        return referenceLongitudeRadians;
    }

    /**
     * Longitude of a horizontal coordinate, in radians, wrapped into {@code [-PI, PI)}.
     * Negative and positive coordinates, non-zero border centres and very large coordinates are all valid.
     */
    public double longitudeRadians(double x) {
        return Angles.wrapRadiansSigned(referenceLongitudeRadians + Angles.TAU * ((x - referenceX) / circumference));
    }

    /**
     * The part of a full planetary rotation that separates {@code x} from the reference coordinate,
     * in turns, wrapped into {@code [-0.5, 0.5)}.
     */
    public double longitudeOffsetTurns(double x) {
        return Angles.wrapTurnsSigned((x - referenceX) / circumference);
    }

    /** Longitudinal distance between two coordinates in turns, wrapped into {@code [0, 0.5]}. */
    public double angularSeparationTurns(double x1, double x2) {
        double difference = Math.abs(longitudeOffsetTurns(x2) - longitudeOffsetTurns(x1));
        return Math.min(difference, 1.0 - difference);
    }

    /**
     * Latitude mapping (documented approximation).
     *
     * <p>Latitude is derived linearly from one horizontal axis and clamped to {@code +-latitudeRange}:
     *
     * <pre>
     *   fraction = clamp((coord - referenceCoord) / halfExtent, -1, 1)
     *   latitude = fraction * latitudeRange
     * </pre>
     *
     * <p>Because the flat world is rectangular this compresses an entire hemisphere into half of the
     * border and is not distance preserving: travelling 100 blocks near the virtual pole covers far
     * more latitude than near the equator. Set {@link LatitudeAxis#NONE} to treat everybody as being on
     * the equator instead.
     *
     * @param coordinate  the coordinate selected by {@code axis} (Z by default)
     * @param halfExtent  half of the mapped extent (normally half the world border), must be positive
     */
    public double latitudeRadians(double coordinate, LatitudeAxis axis, double halfExtent, double latitudeRangeRadians) {
        if (axis == null || axis == LatitudeAxis.NONE || !(halfExtent > 0.0) || !(latitudeRangeRadians > 0.0)) {
            return 0.0;
        }
        double referenceCoord = axis == LatitudeAxis.Z ? referenceZ : referenceX;
        double fraction = Angles.clamp((coordinate - referenceCoord) / halfExtent, -1.0, 1.0);
        return fraction * latitudeRangeRadians;
    }

    /** Latitude of a coordinate on the Z axis (north-south), the default mapping. */
    public double latitudeRadiansFromZ(double z, double halfExtent, double latitudeRangeRadians) {
        return latitudeRadians(z, LatitudeAxis.Z, halfExtent, latitudeRangeRadians);
    }

    /** Latitude of a coordinate on the X axis, for worlds configured with {@link LatitudeAxis#X}. */
    public double latitudeRadiansFromX(double x, double halfExtent, double latitudeRangeRadians) {
        return latitudeRadians(x, LatitudeAxis.X, halfExtent, latitudeRangeRadians);
    }

    @Override
    public String toString() {
        return "PlanetGeometry[circumference=" + circumference + ", referenceX=" + referenceX
                + ", referenceZ=" + referenceZ
                + ", referenceLongitude=" + Angles.radiansToDegrees(referenceLongitudeRadians) + "deg]";
    }
}
