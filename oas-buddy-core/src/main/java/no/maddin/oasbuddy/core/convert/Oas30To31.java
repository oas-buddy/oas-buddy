package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.document.SchemaWalker;

import java.util.ArrayList;
import java.util.List;

/**
 * OAS 3.0 → 3.1. Everything 3.0 can say, 3.1 can say too, so this is lossless; only the spelling of
 * two schema rules changed when 3.1 adopted JSON Schema 2020-12:
 *
 * <ul>
 *   <li>{@code nullable: true} becomes {@code "null"} in the {@code type}: {@code type: string}
 *       turns into {@code type: [string, "null"]}, written in place of the old type.</li>
 *   <li>A boolean {@code exclusiveMinimum: true} beside {@code minimum: n} becomes
 *       {@code exclusiveMinimum: n}, written where {@code minimum} stood (likewise for the maximum).</li>
 * </ul>
 *
 * <p>A {@code false} flag is the default in both versions and is simply dropped. A {@code true}
 * flag that has no effect in 3.0 — {@code nullable} on a schema without a {@code type} (3.0.3 says
 * it then adds nothing), an exclusive flag without its bound — has no 3.1 spelling either. Rather
 * than drop what the author wrote, it is kept as {@code x-oas-buddy-<keyword>} in its place and
 * reported, so converting back restores it.
 */
public final class Oas30To31 implements ConversionStep {

    /** The prefix every keyword this editor sets aside during a conversion is kept under. */
    public static final String KEPT_PREFIX = "x-oas-buddy-";

    static final String NULLABLE = "nullable";
    static final String TYPE = "type";
    static final String NULL = "null";

    @Override
    public OasVersion from() {
        return OasVersion.V3_0;
    }

    @Override
    public OasVersion to() {
        return OasVersion.V3_1;
    }

    @Override
    public List<Conflict> apply(ObjectNode root) {
        List<Conflict> conflicts = new ArrayList<>();
        SchemaWalker.walk(root, OasVersion.V3_0, (schema, location) -> {
            convertNullable(schema, location, conflicts);
            convertExclusive(schema, "minimum", "exclusiveMinimum", location, conflicts);
            convertExclusive(schema, "maximum", "exclusiveMaximum", location, conflicts);
        });
        root.put("openapi", OasVersion.V3_1.newDocumentVersion());
        return conflicts;
    }

    private static void convertNullable(ObjectNode schema, List<String> location, List<Conflict> conflicts) {
        JsonNode nullable = schema.get(NULLABLE);
        if (nullable == null || !nullable.isBoolean()) {
            return;
        }
        if (!nullable.booleanValue()) {
            schema.remove(NULLABLE);
            return;
        }
        JsonNode type = schema.get(TYPE);
        if (type != null && type.isTextual()) {
            schema.set(TYPE, schema.arrayNode().add(type.textValue()).add(NULL));
            schema.remove(NULLABLE);
        } else if (type instanceof ArrayNode types) {
            if (!contains(types, NULL)) {
                types.add(NULL);
            }
            schema.remove(NULLABLE);
        } else {
            keep(schema, NULLABLE, location, conflicts,
                    "nullable: true without a type has no effect in OAS 3.0.3 and no OAS 3.1 equivalent",
                    "admit null with a type that lists \"null\", or anyOf with {type: \"null\"}");
        }
    }

    private static void convertExclusive(ObjectNode schema, String bound, String exclusive,
                                         List<String> location, List<Conflict> conflicts) {
        JsonNode flag = schema.get(exclusive);
        if (flag == null || !flag.isBoolean()) {
            // absent, or already the numeric 3.1 form
            return;
        }
        if (!flag.booleanValue()) {
            schema.remove(exclusive);
            return;
        }
        JsonNode value = schema.get(bound);
        if (value != null && value.isNumber()) {
            schema.remove(exclusive);
            JsonNodes.renameField(schema, bound, exclusive);
        } else {
            keep(schema, exclusive, location, conflicts,
                    exclusive + ": true without " + bound + " has no effect in OAS 3.0 and no OAS 3.1 equivalent",
                    "set " + exclusive + " to the bound itself");
        }
    }

    /** Sets {@code keyword} aside as {@code x-oas-buddy-<keyword>}, in place, and reports it. */
    private static void keep(ObjectNode schema, String keyword, List<String> location, List<Conflict> conflicts,
                             String problem, String hint) {
        String kept = KEPT_PREFIX + keyword;
        if (!JsonNodes.renameField(schema, keyword, kept)) {
            return;
        }
        List<String> at = new ArrayList<>(location);
        at.add(keyword);
        conflicts.add(Conflict.at(at, problem, "kept as " + kept + "; to make it effective, " + hint));
    }

    private static boolean contains(ArrayNode array, String text) {
        for (JsonNode element : array) {
            if (text.equals(element.asText())) {
                return true;
            }
        }
        return false;
    }
}
