package dev.virtualplanet.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dependency-free JSON reader/writer used for the configuration file. */
class SimpleJsonTest {

    @Test
    void parsesASimpleObject() {
        Map<String, Object> map = SimpleJson.parseObject("{\"enabled\": true, \"count\": 12, \"ratio\": 0.5, \"name\": \"planet\"}");
        assertEquals(Boolean.TRUE, map.get("enabled"));
        assertEquals(12.0, (Double) map.get("count"), 1.0e-9);
        assertEquals(0.5, (Double) map.get("ratio"), 1.0e-9);
        assertEquals("planet", map.get("name"));
    }

    @Test
    void parsesEscapesNestedStructuresAndNull() {
        Map<String, Object> map = SimpleJson.parseObject(
                "{\"text\": \"line\\nbreak\\t\\\"quoted\\\"\", \"nested\": {\"a\": [1, 2, 3]}, \"nothing\": null}");
        assertEquals("line\nbreak\t\"quoted\"", map.get("text"));
        assertTrue(map.get("nested") instanceof Map);
        assertNull(map.get("nothing"));
    }

    @Test
    void rejectsMalformedDocuments() {
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject(""));
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject("{"));
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject("{\"a\": }"));
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject("{\"a\": 1"));
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject("{\"a\" 1}"));
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject("[1, 2]"));
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject("{\"a\": 1} trailing"));
        assertThrows(SimpleJson.JsonException.class, () -> SimpleJson.parseObject(null));
    }

    @Test
    void roundTripsThroughText() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("enabled", Boolean.TRUE);
        original.put("circumference", 40000.0);
        original.put("ticks", 24000L);
        original.put("axis", "Z");
        original.put("nothing", null);
        String json = SimpleJson.write(original);
        Map<String, Object> parsed = SimpleJson.parseObject(json);
        assertEquals(Boolean.TRUE, parsed.get("enabled"));
        assertEquals(40000.0, (Double) parsed.get("circumference"), 1.0e-6);
        assertEquals("Z", parsed.get("axis"));
        assertNull(parsed.get("nothing"));
    }

    @Test
    void writesNonFiniteNumbersAsZeroInsteadOfProducingInvalidJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("nan", Double.NaN);
        map.put("infinite", Double.POSITIVE_INFINITY);
        String json = SimpleJson.write(map);
        Map<String, Object> parsed = SimpleJson.parseObject(json);
        assertEquals(0.0, (Double) parsed.get("nan"), 1.0e-9);
        assertEquals(0.0, (Double) parsed.get("infinite"), 1.0e-9);
    }
}
