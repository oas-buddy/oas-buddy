package no.maddin.oasbuddy.core.validation;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.OasVersion;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reports a local {@code $ref} that points at nothing — what removing a still-referenced schema,
 * response or other component deliberately leaves behind for this panel to report.
 *
 * <p>swagger-parser only half does this with resolving switched off (verified): a dangling
 * {@code #/components/schemas/...} is reported ({@code ... is missing}), but a dangling response,
 * parameter or example reference produces no message at all. Without this check the removal
 * dialogs' promise that the validation panel reports what they leave dangling would not hold. Schema
 * references are skipped here, since reporting them again would show one problem twice — the same
 * reason {@code TagCheck} leaves duplicate tag names alone.
 *
 * <p>Only local references ({@code #/...}) are checked; external ones are out of scope for the MVP.
 * {@code example}, {@code default} and {@code enum} hold data rather than OAS objects, as does an
 * Example object's {@code value}, so a {@code $ref} key inside them is not a reference and is
 * skipped — the same distinction {@code ConstraintCheck} draws.
 *
 * <p>Swagger 2.0 differs in two ways. swagger-parser reports no dangling reference in a 2.0
 * document at all (verified), so {@code #/definitions/...} is checked here too rather than skipped.
 * And a 2.0 response's {@code examples} maps media types straight to example data, so the whole of
 * it is data.
 */
final class ReferenceCheck {

    private static final String REF = "$ref";
    private static final String LOCATION_SEPARATOR = " → ";
    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";
    private static final String EXAMPLES = "examples";

    private ReferenceCheck() {
    }

    static List<ValidationMessage> check(JsonNode root, OasVersion version) {
        List<ValidationMessage> messages = new ArrayList<>();
        walk(root, root, version, new ArrayList<>(), messages);
        return messages;
    }

    private static void walk(JsonNode root, JsonNode node, OasVersion version, List<String> location,
                             List<ValidationMessage> messages) {
        if (node instanceof ObjectNode object) {
            JsonNode ref = object.get(REF);
            if (ref != null && ref.isTextual()) {
                checkRef(root, ref.asText(), version, location, messages);
                return;
            }
            for (Map.Entry<String, JsonNode> field : object.properties()) {
                if (isData(field.getKey(), version, location)) {
                    continue;
                }
                location.add(field.getKey());
                walk(root, field.getValue(), version, location, messages);
                location.removeLast();
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                location.add("[" + i + "]");
                walk(root, array.get(i), version, location, messages);
                location.removeLast();
            }
        }
    }

    /** An Example object's {@code value} sits two levels below an {@code examples} map. */
    private static boolean isData(String field, OasVersion version, List<String> location) {
        if (version == OasVersion.V2_0) {
            return Check.DATA_FIELDS.contains(field) || EXAMPLES.equals(field);
        }
        return Check.DATA_FIELDS.contains(field)
                || "value".equals(field) && location.size() >= 2
                        && EXAMPLES.equals(location.get(location.size() - 2));
    }

    private static void checkRef(JsonNode root, String ref, OasVersion version, List<String> location,
                                 List<ValidationMessage> messages) {
        boolean reportedBySwaggerParser = version != OasVersion.V2_0 && ref.startsWith(SCHEMA_REF_PREFIX);
        if (!ref.startsWith("#/") || reportedBySwaggerParser) {
            return;
        }
        if (!resolves(root, ref)) {
            messages.add(new ValidationMessage(ValidationSeverity.ERROR,
                    "Reference " + ref + " does not resolve (at " + String.join(LOCATION_SEPARATOR, location) + ")"));
        }
    }

    private static boolean resolves(JsonNode root, String ref) {
        try {
            String pointer = URLDecoder.decode(ref.substring(1), StandardCharsets.UTF_8);
            return !root.at(JsonPointer.compile(pointer)).isMissingNode();
        } catch (IllegalArgumentException _) {
            return false;
        }
    }
}
