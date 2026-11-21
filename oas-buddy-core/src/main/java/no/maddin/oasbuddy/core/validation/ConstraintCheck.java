package no.maddin.oasbuddy.core.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.model.Constraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Checks schema validation keywords, which swagger-parser leaves almost entirely alone: it accepts
 * a {@code minLength} above its {@code maxLength}, a negative {@code minItems}, a string
 * {@code minimum} and a numeric {@code exclusiveMinimum}. The editor lets the user enter all of
 * these — editing an invalid spec is always allowed — so this is where they get reported.
 *
 * <p>Schemas are found by where OAS puts them rather than by looking for the keywords anywhere:
 * a property may well be <em>named</em> {@code minLength}, and that is not a constraint.
 *
 * <p>Reusable schemas live under {@code components.schemas} in 3.x and under {@code definitions} in
 * Swagger 2.0; everywhere else both versions reach a schema through a {@code schema} key.
 *
 * <p>What kind of value a keyword takes can depend on the version — {@code exclusiveMinimum} is a
 * boolean in 2.0 and 3.0 but a number in 3.1 — which {@link Constraint} states, so the version is
 * passed along to it.
 */
final class ConstraintCheck {

    /** Keys whose value is a map of name to schema. */
    private static final Set<String> SCHEMA_MAPS = Set.of("properties");
    /** Keys whose value is a single schema. */
    private static final Set<String> SCHEMA_FIELDS = Set.of("schema", "items", "additionalProperties", "not");
    /** Keys whose value is a list of schemas. */
    private static final Set<String> SCHEMA_LISTS = Set.of("allOf", "anyOf", "oneOf");
    /** Keys holding example data, which may contain anything and is never a schema. */
    private static final Set<String> DATA = Stream.concat(Check.DATA_FIELDS.stream(), Stream.of("examples"))
            .collect(Collectors.toUnmodifiableSet());

    /** Where Swagger 2.0 keeps its reusable schemas; no 3.x document has this root key. */
    private static final String DEFINITIONS = "definitions";

    private ConstraintCheck() {
    }

    static List<ValidationMessage> check(JsonNode root, OasVersion version) {
        List<ValidationMessage> messages = new ArrayList<>();
        boolean swagger20 = version == OasVersion.V2_0;
        JsonNode schemas = swagger20 ? root.path(DEFINITIONS) : root.path("components").path("schemas");
        String prefix = swagger20 ? DEFINITIONS + "." : "components.schemas.";
        if (schemas instanceof ObjectNode schemaMap) {
            schemaMap.properties().forEach(entry ->
                    checkSchema(entry.getValue(), prefix + entry.getKey(), version, messages));
        }
        findSchemaFields(root, "", version, messages);
        return messages;
    }

    /** Everything outside component schemas reaches its schemas through a {@code schema} key. */
    private static void findSchemaFields(JsonNode node, String location, OasVersion version,
                                         List<ValidationMessage> messages) {
        if (node instanceof ObjectNode objectNode) {
            for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
                String key = entry.getKey();
                String childLocation = join(location, key);
                if (location.isEmpty() && key.equals(DEFINITIONS)) {
                    continue;
                }
                if (location.isEmpty() && key.equals("components")) {
                    findSchemaFieldsOutsideComponentSchemas(entry.getValue(), childLocation, version, messages);
                } else if (key.equals("schema")) {
                    checkSchema(entry.getValue(), childLocation, version, messages);
                } else if (!DATA.contains(key)) {
                    findSchemaFields(entry.getValue(), childLocation, version, messages);
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                findSchemaFields(arrayNode.get(i), location + "[" + i + "]", version, messages);
            }
        }
    }

    private static void findSchemaFieldsOutsideComponentSchemas(JsonNode components, String location,
                                                                OasVersion version,
                                                                List<ValidationMessage> messages) {
        if (components instanceof ObjectNode objectNode) {
            for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
                if (!entry.getKey().equals("schemas")) {
                    findSchemaFields(entry.getValue(), join(location, entry.getKey()), version, messages);
                }
            }
        }
    }

    private static void checkSchema(JsonNode node, String location, OasVersion version,
                                    List<ValidationMessage> messages) {
        if (!(node instanceof ObjectNode schema)) {
            return;
        }
        checkValues(schema, location, version, messages);
        checkRanges(schema, location, version, messages);

        for (Map.Entry<String, JsonNode> entry : schema.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            String childLocation = join(location, key);
            if (SCHEMA_FIELDS.contains(key)) {
                checkSchema(value, childLocation, version, messages);
            } else if (SCHEMA_MAPS.contains(key) && value instanceof ObjectNode map) {
                map.properties().forEach(property ->
                        checkSchema(property.getValue(), join(childLocation, property.getKey()), version, messages));
            } else if (SCHEMA_LISTS.contains(key) && value instanceof ArrayNode list) {
                for (int i = 0; i < list.size(); i++) {
                    checkSchema(list.get(i), childLocation + "[" + i + "]", version, messages);
                }
            }
        }
    }

    private static void checkValues(ObjectNode schema, String location, OasVersion version,
                                    List<ValidationMessage> messages) {
        for (Constraint constraint : Constraint.values()) {
            JsonNode value = schema.get(constraint.key());
            if (value != null && !constraint.accepts(value, version)) {
                messages.add(error(join(location, constraint.key()) + " must be " + describe(constraint.kind(version))
                        + ", but is " + value));
            }
        }
    }

    private static void checkRanges(ObjectNode schema, String location, OasVersion version,
                                    List<ValidationMessage> messages) {
        for (List<Constraint> range : Constraint.ranges(version)) {
            Constraint lower = range.get(0);
            Constraint upper = range.get(1);
            JsonNode low = schema.get(lower.key());
            JsonNode high = schema.get(upper.key());
            if (low != null && high != null && low.isNumber() && high.isNumber()
                    && low.decimalValue().compareTo(high.decimalValue()) > 0) {
                messages.add(error(location + ": " + lower.key() + " (" + low + ") is greater than "
                        + upper.key() + " (" + high + "), so no value can satisfy it"));
            }
        }
    }

    private static String describe(Constraint.Kind kind) {
        return switch (kind) {
            case NUMBER -> "a number";
            case COUNT -> "a non-negative integer";
            case BOOLEAN -> "a boolean";
            case TEXT -> "a string";
        };
    }

    private static String join(String location, String key) {
        return location.isEmpty() ? key : location + "." + key;
    }

    private static ValidationMessage error(String message) {
        return new ValidationMessage(ValidationSeverity.ERROR, message);
    }
}
