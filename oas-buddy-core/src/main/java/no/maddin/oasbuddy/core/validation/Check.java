package no.maddin.oasbuddy.core.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

/** One check run over the parsed document, on top of what swagger-parser reports. Read-only. */
@FunctionalInterface
interface Check {

    /**
     * Keys that hold example data rather than OAS objects, so nothing inside them is a schema or a
     * reference. Each check adds what else is data for it ({@code examples} for constraints, an
     * Example object's {@code value} for references).
     */
    Set<String> DATA_FIELDS = Set.of("example", "default", "enum");

    List<ValidationMessage> check(JsonNode root);
}
