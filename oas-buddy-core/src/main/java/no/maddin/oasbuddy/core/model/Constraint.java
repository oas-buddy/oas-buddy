package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import no.maddin.oasbuddy.core.document.OasVersion;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The JSON-Schema validation keywords OAS keeps, each with the kind of value it takes and the types
 * it constrains. The one place that says so: the editor shows a constraint only for its own types,
 * and {@code validation.ConstraintCheck} checks values against the same kinds.
 *
 * <p>The kind can depend on the version. {@code exclusiveMinimum}/{@code exclusiveMaximum} are
 * booleans modifying {@code minimum}/{@code maximum} in Swagger 2.0 and OAS 3.0, which follow JSON
 * Schema draft 4; in OAS 3.1 (JSON Schema 2020-12) they are numbers, bounds of their own.
 */
public enum Constraint {
    MINIMUM("minimum", "Minimum", Kind.NUMBER, Group.NUMERIC),
    EXCLUSIVE_MINIMUM("exclusiveMinimum", "Exclusive minimum", Kind.BOOLEAN, Kind.NUMBER, Group.NUMERIC),
    MAXIMUM("maximum", "Maximum", Kind.NUMBER, Group.NUMERIC),
    EXCLUSIVE_MAXIMUM("exclusiveMaximum", "Exclusive maximum", Kind.BOOLEAN, Kind.NUMBER, Group.NUMERIC),
    MULTIPLE_OF("multipleOf", "Multiple of", Kind.NUMBER, Group.NUMERIC),
    MIN_LENGTH("minLength", "Min length", Kind.COUNT, Group.STRING),
    MAX_LENGTH("maxLength", "Max length", Kind.COUNT, Group.STRING),
    PATTERN("pattern", "Pattern", Kind.TEXT, Group.STRING),
    MIN_ITEMS("minItems", "Min items", Kind.COUNT, Group.ARRAY),
    MAX_ITEMS("maxItems", "Max items", Kind.COUNT, Group.ARRAY),
    UNIQUE_ITEMS("uniqueItems", "Unique items", Kind.BOOLEAN, Group.ARRAY),
    MIN_PROPERTIES("minProperties", "Min properties", Kind.COUNT, Group.OBJECT),
    MAX_PROPERTIES("maxProperties", "Max properties", Kind.COUNT, Group.OBJECT);

    /** What a constraint's value is. */
    public enum Kind {
        /** Any number. */
        NUMBER,
        /** A non-negative integer. */
        COUNT,
        /** Only ever written as {@code true}; {@code false} is the default and is left out. */
        BOOLEAN,
        TEXT
    }

    private enum Group {
        NUMERIC("integer", "number"),
        STRING("string"),
        ARRAY("array"),
        OBJECT("object");

        private final Set<String> types;

        Group(String... types) {
            this.types = Set.of(types);
        }
    }

    private static final List<List<Constraint>> RANGES = List.of(
            List.of(MINIMUM, MAXIMUM),
            List.of(MIN_LENGTH, MAX_LENGTH),
            List.of(MIN_ITEMS, MAX_ITEMS),
            List.of(MIN_PROPERTIES, MAX_PROPERTIES));

    /** In 3.1 the exclusive bounds are numbers, so they pair up with the bounds too. */
    private static final List<List<Constraint>> NUMERIC_EXCLUSIVE_RANGES = List.of(
            List.of(EXCLUSIVE_MINIMUM, EXCLUSIVE_MAXIMUM),
            List.of(MINIMUM, EXCLUSIVE_MAXIMUM),
            List.of(EXCLUSIVE_MINIMUM, MAXIMUM));

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final String key;
    private final String label;
    private final Kind kind;
    private final Kind oas31Kind;
    private final Group group;

    Constraint(String key, String label, Kind kind, Group group) {
        this(key, label, kind, kind, group);
    }

    Constraint(String key, String label, Kind kind, Kind oas31Kind, Group group) {
        this.key = key;
        this.label = label;
        this.kind = kind;
        this.oas31Kind = oas31Kind;
        this.group = group;
    }

    /**
     * Lower/upper bound pairs, where the lower one above the upper one admits no value at all. Only
     * a pair whose values are both numbers can be compared.
     */
    public static List<List<Constraint>> ranges(OasVersion version) {
        if (version != OasVersion.V3_1) {
            return RANGES;
        }
        return Stream.concat(RANGES.stream(), NUMERIC_EXCLUSIVE_RANGES.stream()).toList();
    }

    /** The constraints that apply to a type, in display order; none for a missing or other type. */
    public static List<Constraint> forType(String type) {
        return Arrays.stream(values()).filter(c -> c.appliesTo(type)).toList();
    }

    public boolean appliesTo(String type) {
        return type != null && group.types.contains(type);
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** What this constraint's value is in a document of the given version. */
    public Kind kind(OasVersion version) {
        return version == OasVersion.V3_1 ? oas31Kind : kind;
    }

    /** Whether a value in a document of the given version is of this constraint's kind. */
    public boolean accepts(JsonNode value, OasVersion version) {
        return switch (kind(version)) {
            case NUMBER -> value.isNumber();
            case COUNT -> value.isIntegralNumber() && value.asLong() >= 0;
            case BOOLEAN -> value.isBoolean();
            case TEXT -> value.isTextual();
        };
    }

    /**
     * Reads what a user typed. A number is written as a number, never as a quoted string — unlike
     * an example value there is no literal fallback, because a string {@code minLength} means
     * nothing to anyone. Any number is taken, though, even for a count: a negative or fractional
     * {@code minLength} can still be written as what it is, and validation reports it — editing an
     * invalid spec is always allowed. Only text that is no number at all is refused.
     *
     * @return the value, or {@code null} for blank text
     * @throws IllegalArgumentException if the text cannot be written as this kind of value
     */
    public JsonNode parse(String text, OasVersion version) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Kind expected = kind(version);
        if (expected == Kind.TEXT) {
            return TextNode.valueOf(text);
        }
        try {
            JsonNode value = JSON.readTree(text.strip());
            boolean writable = expected == Kind.BOOLEAN ? value.isBoolean() : value.isNumber();
            if (writable) {
                return value;
            }
        } catch (JsonProcessingException _) {
            // Falls through to the rejection below.
        }
        throw new IllegalArgumentException("\"" + text + "\" is not a valid " + key);
    }
}
