package dev.virtualplanet.config;

import dev.virtualplanet.math.LatitudeAxis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Configuration files and the network payload: stable, self-describing and hostile to bad input. */
class PlanetConfigSerializationTest {

    private static PlanetConfig interestingConfig() {
        PlanetConfig config = PlanetConfig.defaults();
        config.enabled = true;
        config.deriveCircumferenceFromWorldBorder = false;
        config.circumferenceBlocks = 60000.0;
        config.borderCircumferenceFactor = 1.5;
        config.referenceFollowsWorldBorderCenter = false;
        config.referenceX = -1234.5;
        config.referenceZ = 987.25;
        config.referenceLongitudeDegrees = -45.0;
        config.rotationPeriodTicks = 36000L;
        config.smoothCelestialMotion = false;
        config.localMoonPhase = false;
        config.latitudeAxis = LatitudeAxis.X;
        config.latitudeRangeDegrees = 45.0;
        config.axialTiltDegrees = 12.5;
        config.seasonsEnabled = true;
        config.yearLengthDays = 120.0;
        config.seasonOffsetDays = 5.5;
        config.localSkyBrightness = false;
        config.debug = true;
        config.sanitize();
        return config;
    }

    @Test
    void jsonRoundTripPreservesEveryField() {
        PlanetConfig original = interestingConfig();
        String json = PlanetConfigJson.toJson(original);
        List<String> warnings = new ArrayList<>();
        PlanetConfig parsed = PlanetConfigJson.fromJson(json, warnings);
        assertEquals(original, parsed, "json round trip must not change any field: " + warnings);
        assertTrue(warnings.isEmpty(), "a file written by the mod must load without warnings: " + warnings);
    }

    @Test
    void binaryRoundTripPreservesEveryField() {
        PlanetConfig original = interestingConfig();
        PlanetConfig decoded = PlanetConfigBinary.decode(PlanetConfigBinary.encode(original));
        assertEquals(original, decoded);
    }

    @Test
    void everyClientComputesTheSameConfigurationFromThePayload() {
        PlanetConfig serverCopy = interestingConfig();
        byte[] payload = PlanetConfigBinary.encode(serverCopy);
        PlanetConfig clientA = PlanetConfigBinary.decode(payload);
        PlanetConfig clientB = PlanetConfigBinary.decode(payload);
        assertEquals(clientA, clientB);
        assertEquals(serverCopy, clientA);
    }

    @Test
    void corruptedOrTruncatedPayloadsAreRejectedCleanly() {
        byte[] payload = PlanetConfigBinary.encode(interestingConfig());

        byte[] corrupted = payload.clone();
        corrupted[corrupted.length - 3] ^= 0x5A;
        assertThrows(IllegalArgumentException.class, () -> PlanetConfigBinary.decode(corrupted));

        byte[] truncated = new byte[8];
        System.arraycopy(payload, 0, truncated, 0, 8);
        assertThrows(IllegalArgumentException.class, () -> PlanetConfigBinary.decode(truncated));

        assertThrows(IllegalArgumentException.class, () -> PlanetConfigBinary.decode(null));
        assertThrows(IllegalArgumentException.class, () -> PlanetConfigBinary.decode(new byte[16]));
    }

    @Test
    void badPayloadIsIgnoredInsteadOfCrashing() {
        PlanetConfig fallback = PlanetConfig.defaults();
        PlanetConfig result;
        try {
            result = PlanetConfigBinary.decode(new byte[32]);
        } catch (IllegalArgumentException expected) {
            result = fallback;
        }
        assertEquals(fallback, result);
    }

    @Test
    void unknownAndWrongTypedJsonKeysFallBackToDefaults() {
        String json = "{\"enabled\": \"yes\", \"circumferenceBlocks\": \"big\", \"unknownKey\": 42, \"debug\": true}";
        List<String> warnings = new ArrayList<>();
        PlanetConfig config = PlanetConfigJson.fromJson(json, warnings);
        assertTrue(config.enabled, "wrong type must fall back to the default");
        assertEquals(0.0, config.circumferenceBlocks);
        assertTrue(config.debug);
        assertTrue(warnings.size() >= 3, "each repair must be reported: " + warnings);
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("unknownKey")));
    }

    @Test
    void partiallyWrittenJsonStillLoads() {
        List<String> warnings = new ArrayList<>();
        PlanetConfig config = PlanetConfigJson.fromJson("{\"rotationPeriodTicks\": 48000}", warnings);
        assertEquals(48000L, config.rotationPeriodTicks);
        assertEquals(PlanetConfig.defaults().circumferenceBlocks, config.circumferenceBlocks, 1.0e-9);
        assertFalse(config == null);
    }
}
