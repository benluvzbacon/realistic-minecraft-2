package dev.virtualplanet.client;

import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.config.PlanetConfigManager;
import dev.virtualplanet.math.Angles;
import dev.virtualplanet.math.PlanetGeometry;
import dev.virtualplanet.math.PlanetTime;
import dev.virtualplanet.sky.LocalSkyModel;
import dev.virtualplanet.sky.LocalSkyView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.border.WorldBorder;

/**
 * The client's single source of truth for the local sky.
 *
 * <p>Everything the client needs (the sun/moon phase for the viewer, the light map brightness) is derived
 * here from two inputs that the mod never modifies:
 *
 * <ul>
 *   <li>the <b>shared world time</b> ({@link ClientWorld#getTimeOfDay()}, which is exactly the value
 *       vanilla feeds into {@code LunarWorldView#getSkyAngle} as "lunar time"), and</li>
 *   <li>the <b>viewer's coordinates</b> plus the authoritative world border.</li>
 * </ul>
 *
 * <p>The configuration comes from the server when one is connected (see the networking package) and from
 * the local config file otherwise.
 *
 * <p>Only the client render thread touches the cache; the configuration itself is volatile so that a
 * network update can land safely.
 */
public final class LocalSky {
    /**
     * Partial ticks are quantised for the cache key. One 64th of a tick is far below one pixel of sky
     * movement (a full rotation takes 24000 ticks), so this is invisible but avoids recomputing the
     * whole sky for every single frame.
     */
    private static final int PARTIAL_TICK_STEPS = 64;

    /** Configuration pushed by the server; {@code null} means "use the local config file". */
    private static volatile PlanetConfig serverConfig;
    /** Bumped whenever the active configuration changes so that the view cache is discarded. */
    private static volatile long revision;

    private static volatile LocalSkyModel model;
    private static volatile PlanetConfig modelConfig;

    // --- one-entry cache, keyed by everything the local sky depends on ---------------------------------
    private static long cachedRevision = Long.MIN_VALUE;
    private static long cachedTime = Long.MIN_VALUE;
    private static long cachedBlockX = Long.MIN_VALUE;
    private static long cachedBlockZ = Long.MIN_VALUE;
    private static int cachedPartialStep = Integer.MIN_VALUE;
    private static boolean cachedSmooth;
    private static double cachedCenterX = Double.NaN;
    private static double cachedCenterZ = Double.NaN;
    private static double cachedSize = Double.NaN;
    private static LocalSkyView cachedView;

    private LocalSky() {
    }

    /** The configuration currently in force (server configuration first, local file second). */
    public static PlanetConfig config() {
        PlanetConfig synced = serverConfig;
        return synced != null ? synced : PlanetConfigManager.get();
    }

    /** Installs the configuration received from the server. */
    public static void acceptServerConfig(PlanetConfig config) {
        serverConfig = config == null ? null : config;
        invalidate();
    }

    /** Drops the server configuration and falls back to the local file. */
    public static void clearServerConfig() {
        serverConfig = null;
        invalidate();
    }

    /** Invalidates the cached view (config reload, border move, dimension change, ...). */
    public static void invalidate() {
        revision++;
        cachedView = null;
        cachedRevision = Long.MIN_VALUE;
        model = null;
        modelConfig = null;
    }

    /** The view computed for the most recent frame, or {@code null} before the first one. */
    public static LocalSkyView currentView() {
        return cachedView;
    }

    /** Celestial phase of the viewer in turns, {@code 0.0} = the viewer's own solar noon. */
    public static float skyAngle(ClientWorld world, float tickDelta) {
        return (float) view(world, tickDelta).localSkyAngleTurns();
    }

    /** The viewer's local moon phase index ({@code 0..7}), or vanilla's when the option is off. */
    public static int moonPhase(ClientWorld world) {
        PlanetConfig config = config();
        long time = world.getTimeOfDay();
        if (!config.enabled || !config.localMoonPhase) {
            return LocalSkyModel.vanillaMoonPhase(time);
        }
        LocalSkyView view = cachedView;
        if (view != null && cachedTime == time) {
            return view.moonPhase();
        }
        double offset = offsetTurns(world, config);
        return LocalSkyModel.localMoonPhase(time, offset);
    }

    /**
     * Whether the light map must stay on vanilla time: the light map follows the local sky by default
     * (this is what {@code localSkyBrightness} controls), so only the "off" case needs an override.
     */
    public static boolean usesVanillaBrightness() {
        PlanetConfig config = config();
        return !config.enabled || !config.localSkyBrightness;
    }

    /** Vanilla's own brightness formula, evaluated for the shared world time (verified 1.21.1 formula). */
    public static float vanillaSkyBrightness(ClientWorld world, float tickDelta) {
        double angle = PlanetTime.skyAngleTurns(world.getTimeOfDay(), PlanetTime.VANILLA_DAY_TICKS);
        return LocalSkyModel.skyBrightness(angle, world.getRainGradient(tickDelta),
                world.getThunderGradient(tickDelta));
    }

    /** The local sky for the viewer, recomputed at most once per tick (and per block of movement). */
    public static LocalSkyView view(ClientWorld world, float tickDelta) {
        PlanetConfig config = config();
        WorldBorder border = world.getWorldBorder();
        double centerX = border == null ? 0.0 : border.getCenterX();
        double centerZ = border == null ? 0.0 : border.getCenterZ();
        double size = border == null ? 0.0 : border.getSize();

        Entity camera = MinecraftClient.getInstance().getCameraEntity();
        if (camera == null) {
            camera = MinecraftClient.getInstance().player;
        }
        // Without a camera (world still loading) the reference meridian is the honest fallback.
        double x = camera == null ? centerX : camera.getX();
        double z = camera == null ? centerZ : camera.getZ();

        long time = world.getTimeOfDay();
        boolean smooth = config.smoothCelestialMotion;
        int partialStep = smooth
                ? (int) Math.round(Angles.clamp(tickDelta, 0.0, 1.0) * PARTIAL_TICK_STEPS)
                : 0;
        long blockX = (long) Math.floor(x);
        long blockZ = (long) Math.floor(z);
        long currentRevision = revision;

        if (cachedView != null && cachedRevision == currentRevision && cachedTime == time
                && cachedBlockX == blockX && cachedBlockZ == blockZ
                && cachedPartialStep == partialStep && cachedSmooth == smooth
                && cachedCenterX == centerX && cachedCenterZ == centerZ && cachedSize == size) {
            return cachedView;
        }

        LocalSkyView view = model(config).view(time, x, z, partialStep / (double) PARTIAL_TICK_STEPS,
                centerX, centerZ, size);
        cachedView = view;
        cachedRevision = currentRevision;
        cachedTime = time;
        cachedBlockX = blockX;
        cachedBlockZ = blockZ;
        cachedPartialStep = partialStep;
        cachedSmooth = smooth;
        cachedCenterX = centerX;
        cachedCenterZ = centerZ;
        cachedSize = size;
        return view;
    }

    /** Longitude offset in turns for the viewer's current position. */
    private static double offsetTurns(ClientWorld world, PlanetConfig config) {
        WorldBorder border = world.getWorldBorder();
        double centerX = border == null ? 0.0 : border.getCenterX();
        double centerZ = border == null ? 0.0 : border.getCenterZ();
        double size = border == null ? 0.0 : border.getSize();
        Entity camera = MinecraftClient.getInstance().getCameraEntity();
        if (camera == null) {
            camera = MinecraftClient.getInstance().player;
        }
        double x = camera == null ? centerX : camera.getX();
        PlanetGeometry geometry = config.createGeometry(centerX, centerZ, size);
        return geometry.longitudeOffsetTurns(x);
    }

    private static LocalSkyModel model(PlanetConfig config) {
        LocalSkyModel current = model;
        if (current == null || modelConfig != config) {
            current = new LocalSkyModel(config);
            modelConfig = config;
            model = current;
        }
        return current;
    }
}
