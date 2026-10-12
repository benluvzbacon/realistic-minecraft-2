package dev.virtualplanet.net;

import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.config.PlanetConfigBinary;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server to client configuration sync.
 *
 * <p>The server owns the configuration; the client never invents one. The payload carries the binary
 * encoding of {@link PlanetConfig} (magic, schema version, length and an FNV-1a checksum, see
 * {@link PlanetConfigBinary}) plus a monotonically increasing revision so that the client can tell a
 * genuine change from a repeat.
 *
 * <p>A payload that does not decode - a different mod version, a corrupted packet - falls back to the
 * defaults instead of throwing, and the channel id is namespaced so that it can never collide with
 * another mod.
 */
public final class PlanetConfigPayload implements CustomPayload {
    /** {@code virtualplanet:config} on the play (server -> client) channel. */
    public static final CustomPayload.Id<PlanetConfigPayload> ID =
            new CustomPayload.Id<>(Identifier.of("virtualplanet", "config"));

    public static final PacketCodec<RegistryByteBuf, PlanetConfigPayload> CODEC =
            CustomPayload.codecOf(PlanetConfigPayload::write, PlanetConfigPayload::new);

    private final byte[] data;
    private final long revision;

    private PlanetConfigPayload(RegistryByteBuf buf) {
        this.revision = buf.readLong();
        this.data = buf.readByteArray();
    }

    public PlanetConfigPayload(PlanetConfig config, long revision) {
        this(PlanetConfigBinary.encode(config), revision);
    }

    public PlanetConfigPayload(byte[] data, long revision) {
        this.data = data == null ? new byte[0] : data;
        this.revision = revision;
    }

    /** Called by the codec; the parameter order is "value first" as required by {@code codecOf}. */
    public void write(RegistryByteBuf buf) {
        buf.writeLong(revision);
        buf.writeByteArray(data);
    }

    public long revision() {
        return revision;
    }

    /** Never throws: anything unreadable falls back to the default configuration. */
    public PlanetConfig decodeConfig() {
        try {
            return PlanetConfigBinary.decode(data);
        } catch (RuntimeException error) {
            return PlanetConfig.defaults();
        }
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
