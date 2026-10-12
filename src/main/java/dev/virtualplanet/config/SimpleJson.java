package dev.virtualplanet.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A tiny, dependency-free JSON reader/writer for the mod's configuration file.
 *
 * <p>Only what the configuration needs is supported: a JSON object containing strings, numbers,
 * booleans, {@code null}, and (for robustness) nested objects and arrays. Malformed input never
 * escapes as a surprise type: {@link #parseObject(String)} throws {@link JsonException} and the config
 * loader falls back to defaults, keeping the game running.
 */
public final class SimpleJson {
    private SimpleJson() {
    }

    /** Thrown when a document cannot be parsed. */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    /**
     * Parses a JSON object into an insertion ordered map.
     * Values are {@link String}, {@link Double}, {@link Boolean}, {@code null}, {@link Map} or {@link List}.
     */
    public static Map<String, Object> parseObject(String text) {
        if (text == null) {
            throw new JsonException("input is null");
        }
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("trailing content at index " + parser.index);
        }
        if (!(value instanceof Map)) {
            throw new JsonException("top level value is not an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    /** Serialises a flat map of scalars (strings, numbers, booleans, null) as pretty JSON. */
    public static String write(Map<String, Object> object) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        int index = 0;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            builder.append("  ");
            writeString(builder, entry.getKey());
            builder.append(": ");
            writeValue(builder, entry.getValue());
            if (++index < object.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("}\n");
        return builder.toString();
    }

    private static void writeValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String) {
            writeString(builder, (String) value);
        } else if (value instanceof Boolean) {
            builder.append(value.toString());
        } else if (value instanceof Number) {
            Number number = (Number) value;
            if (number instanceof Double || number instanceof Float) {
                double d = number.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    builder.append('0');
                } else {
                    builder.append(String.format(Locale.ROOT, "%.6f", d));
                }
            } else {
                builder.append(number.toString());
            }
        } else {
            writeString(builder, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
            }
        }
        builder.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new JsonException("unexpected end of input");
            }
            char c = text.charAt(index);
            switch (c) {
                case '{':
                    return parseObjectValue();
                case '[':
                    return parseArrayValue();
                case '"':
                    return parseStringValue();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return null;
                default:
                    return parseNumberValue();
            }
        }

        private Map<String, Object> parseObjectValue() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++; // '{'
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == '}') {
                index++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(index) != '"') {
                    throw new JsonException("expected an object key at index " + index);
                }
                String key = parseStringValue();
                skipWhitespace();
                if (atEnd() || text.charAt(index) != ':') {
                    throw new JsonException("expected ':' at index " + index);
                }
                index++;
                map.put(key, parseValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonException("unexpected end of object");
                }
                char next = text.charAt(index);
                if (next == ',') {
                    index++;
                } else if (next == '}') {
                    index++;
                    return map;
                } else {
                    throw new JsonException("expected ',' or '}' at index " + index);
                }
            }
        }

        private List<Object> parseArrayValue() {
            List<Object> list = new ArrayList<>();
            index++; // '['
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == ']') {
                index++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonException("unexpected end of array");
                }
                char next = text.charAt(index);
                if (next == ',') {
                    index++;
                } else if (next == ']') {
                    index++;
                    return list;
                } else {
                    throw new JsonException("expected ',' or ']' at index " + index);
                }
            }
        }

        private String parseStringValue() {
            if (atEnd() || text.charAt(index) != '"') {
                throw new JsonException("expected a string at index " + index);
            }
            index++;
            StringBuilder builder = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("unterminated string");
                }
                char c = text.charAt(index++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c != '\\') {
                    builder.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw new JsonException("unterminated escape sequence");
                }
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"':
                        builder.append('"');
                        break;
                    case '\\':
                        builder.append('\\');
                        break;
                    case '/':
                        builder.append('/');
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        if (index + 4 > text.length()) {
                            throw new JsonException("truncated unicode escape");
                        }
                        String hex = text.substring(index, index + 4);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException exception) {
                            throw new JsonException("bad unicode escape '" + hex + "'");
                        }
                        index += 4;
                        break;
                    default:
                        throw new JsonException("unknown escape '\\" + escape + "'");
                }
            }
        }

        private Double parseNumberValue() {
            int start = index;
            if (!atEnd() && (text.charAt(index) == '-' || text.charAt(index) == '+')) {
                index++;
            }
            while (!atEnd()) {
                char c = text.charAt(index);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                    index++;
                } else {
                    break;
                }
            }
            String number = text.substring(start, index);
            if (number.isEmpty() || number.equals("-") || number.equals("+")) {
                throw new JsonException("expected a value at index " + start);
            }
            try {
                return Double.valueOf(number);
            } catch (NumberFormatException exception) {
                throw new JsonException("bad number '" + number + "'");
            }
        }

        private void expectLiteral(String literal) {
            if (!text.startsWith(literal, index)) {
                throw new JsonException("expected '" + literal + "' at index " + index);
            }
            index += literal.length();
        }
    }
}
