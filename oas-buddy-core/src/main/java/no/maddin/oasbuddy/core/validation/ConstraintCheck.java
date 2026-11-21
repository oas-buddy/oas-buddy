package no.maddin.oasbuddy.core.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.document.SchemaWalker;
import no.maddin.oasbuddy.core.model.Constraint;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks schema validation keywords, which swagger-parser leaves almost entirely alone: it accepts
 * a {@code minLength} above its {@code maxLength}, a negative {@code minItems}, a string
 * {@code minimum} and a numeric {@code exclusiveMinimum}. The editor lets the user enter all of
 * these — editing an invalid spec is always allowed — so this is where they get reported.
 *
 * <p>Schemas are found by {@link SchemaWalker}, by where OAS puts them rather than by looking for
 * the keywords anywhere: a property may well be <em>named</em> {@code minLength}, and that is not a
 * constraint.
 *
 * <p>What kind of value a keyword takes can depend on the version — {@code exclusiveMinimum} is a
 * boolean in 2.0 and 3.0 but a number in 3.1 — which {@link Constraint} states, so the version is
 * passed along to it.
 */
final class ConstraintCheck {

    private ConstraintCheck() {
    }

    static List<ValidationMessage> check(JsonNode root, OasVersion version) {
        List<ValidationMessage> messages = new ArrayList<>();
        if (root instanceof ObjectNode document) {
            SchemaWalker.walk(document, version, (schema, location) -> {
                String rendered = render(location);
                checkValues(schema, rendered, version, messages);
                checkRanges(schema, rendered, version, messages);
            });
        }
        return messages;
    }

    /** {@code components.schemas.Pet.allOf[0]}: dotted, with an array index attached to its field. */
    private static String render(List<String> location) {
        StringBuilder text = new StringBuilder();
        for (String segment : location) {
            if (!text.isEmpty() && !segment.startsWith("[")) {
                text.append('.');
            }
            text.append(segment);
        }
        return text.toString();
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
