package dev.virtualplanet.config;

import dev.virtualplanet.math.LatitudeAxis;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Binary serialisation of {@link PlanetConfig}, used to send the server's authoritative configuration to
 * clients.
 *
 * <p>The payload is a small, self-describing frame: magic, schema version, payload length and an FNV-1a
 * checksum. {@link #decode(byte[])} never throws anything other than {@link IllegalArgumentException}, and
 * callers keep their previous (default) configuration when that happens, so a corrupt packet cannot crash
 * the game.
 */
public final class PlanetConfigBinary {
    /** "VPLT" */
    public static final int MAGIC = 0x56504C54;
    private static final int MAX_PAYLOAD = 8192;
    private static final int FNV_OFFSET_BASIS = 0x811C9DC5;
    private static final int FNV_PRIME = 0x01000193;

    private PlanetConfigBinary() {
    }

    public static byte[] encode(PlanetConfig config) {
        ByteBuffer payload = ByteBuffer.allocate(256);
        payload.put((byte) (config.enabled ? 1 : 0));
        payload.put((byte) (config.deriveCircumferenceFromWorldBorder ? 1 : 0));
        payload.putDouble(config.circumferenceBlocks);
        payload.putDouble(config.borderCircumferenceFactor);
        payload.put((byte) (config.referenceFollowsWorldBorderCenter ? 1 : 0));
        payload.putDouble(config.referenceX);
        payload.putDouble(config.referenceZ);
        payload.putDouble(config.referenceLongitudeDegrees);
        payload.putLong(config.rotationPeriodTicks);
        payload.put((byte) (config.smoothCelestialMotion ? 1 : 0));
        payload.put((byte) (config.localMoonPhase ? 1 : 0));
        putString(payload, (config.latitudeAxis == null ? LatitudeAxis.Z : config.latitudeAxis).name());
        payload.putDouble(config.latitudeRangeDegrees);
        payload.putDouble(config.axialTiltDegrees);
        payload.put((byte) (config.seasonsEnabled ? 1 : 0));
        payload.putDouble(config.yearLengthDays);
        payload.putDouble(config.seasonOffsetDays);
        payload.put((byte) (config.renderLatitudeTilt ? 1 : 0));
        payload.put((byte) (config.localSkyBrightness ? 1 : 0));
        payload.put((byte) (config.debug ? 1 : 0));

        byte[] payloadBytes = new byte[payload.position()];
        payload.flip();
        payload.get(payloadBytes);

        ByteBuffer frame = ByteBuffer.allocate(16 + payloadBytes.length);
        frame.putInt(MAGIC);
        frame.putInt(PlanetConfig.SCHEMA_VERSION);
        frame.putInt(payloadBytes.length);
        frame.putInt(checksum(payloadBytes));
        frame.put(payloadBytes);
        return frame.array();
    }

    public static PlanetConfig decode(byte[] data) {
        if (data == null || data.length < 16) {
            throw new IllegalArgumentException("payload too short: " + (data == null ? "null" : data.length + " bytes"));
        }
        ByteBuffer frame = ByteBuffer.wrap(data);
        int magic = frame.getInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("bad magic: 0x" + Integer.toHexString(magic));
        }
        int version = frame.getInt();
        if (version != PlanetConfig.SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schema version " + version);
        }
        int length = frame.getInt();
        if (length < 0 || length > MAX_PAYLOAD || length > frame.remaining() - 4) {
            throw new IllegalArgumentException("bad payload length " + length);
        }
        int expectedChecksum = frame.getInt();
        byte[] payloadBytes = new byte[length];
        frame.get(payloadBytes);
        int actualChecksum = checksum(payloadBytes);
        if (expectedChecksum != actualChecksum) {
            throw new IllegalArgumentException("checksum mismatch");
        }

        PlanetConfig config = PlanetConfig.defaults();
        ByteBuffer payload = ByteBuffer.wrap(payloadBytes);
        try {
            config.enabled = payload.get() != 0;
            config.deriveCircumferenceFromWorldBorder = payload.get() != 0;
            config.circumferenceBlocks = payload.getDouble();
            config.borderCircumferenceFactor = payload.getDouble();
            config.referenceFollowsWorldBorderCenter = payload.get() != 0;
            config.referenceX = payload.getDouble();
            config.referenceZ = payload.getDouble();
            config.referenceLongitudeDegrees = payload.getDouble();
            config.rotationPeriodTicks = payload.getLong();
            config.smoothCelestialMotion = payload.get() != 0;
            config.localMoonPhase = payload.get() != 0;
            config.latitudeAxis = LatitudeAxis.byName(getString(payload), LatitudeAxis.Z);
            config.latitudeRangeDegrees = payload.getDouble();
            config.axialTiltDegrees = payload.getDouble();
            config.seasonsEnabled = payload.get() != 0;
            config.yearLengthDays = payload.getDouble();
            config.seasonOffsetDays = payload.getDouble();
            config.renderLatitudeTilt = payload.get() != 0;
            config.localSkyBrightness = payload.get() != 0;
            config.debug = payload.get() != 0;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("truncated payload", exception);
        }
        config.sanitize();
        return config;
    }

    private static void putString(ByteBuffer buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
    }

    private static String getString(ByteBuffer buffer) {
        int length = buffer.getShort() & 0xFFFF;
        if (length > 64 || length > buffer.remaining()) {
            throw new IllegalArgumentException("bad string length " + length);
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int checksum(byte[] bytes) {
        int hash = FNV_OFFSET_BASIS;
        for (byte b : bytes) {
            hash ^= (b & 0xFF);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
