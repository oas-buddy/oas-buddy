package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.document.SchemaWalker;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
 * reported, so converting back restores it (see {@link Kept}).
 *
 * <p>Whatever {@link Oas31To30} set aside is restored, so 3.1 → 3.0 → 3.1 gives back the 3.1 keywords.
 */
public final class Oas30To31 implements ConversionStep {

    static final String EXCLUSIVE_MINIMUM = "exclusiveMinimum";
    static final String EXCLUSIVE_MAXIMUM = "exclusiveMaximum";

    /** Everything {@link Oas31To30} sets aside in a schema, which this step restores. */
    private static final List<String> SCHEMA_KEYWORDS_RESTORED = Stream.concat(
            Oas31To30.SCHEMA_KEYWORDS_ONLY_IN_31.stream(), Stream.of("type")).toList();

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
            Kept.restore(schema, SCHEMA_KEYWORDS_RESTORED);
            Kept.restoreIf(schema, EXCLUSIVE_MINIMUM, JsonNode::isNumber);
            Kept.restoreIf(schema, EXCLUSIVE_MAXIMUM, JsonNode::isNumber);
            convertNullable(schema, location, conflicts);
            convertExclusive(schema, "minimum", EXCLUSIVE_MINIMUM, location, conflicts);
            convertExclusive(schema, "maximum", EXCLUSIVE_MAXIMUM, location, conflicts);
        });
        restoreOutsideSchemas(root);
        root.put("openapi", OasVersion.V3_1.newDocumentVersion());
        return conflicts;
    }

    /**
     * Puts back what {@link Oas31To30} set aside outside schemas. Done after the schema walk, so what
     * comes back verbatim from 3.1 is not run through the 3.0 → 3.1 rules a second time.
     */
    private static void restoreOutsideSchemas(ObjectNode root) {
        Kept.restore(root, List.of("webhooks", "jsonSchemaDialect"));
        JsonNode info = root.get("info");
        Kept.restore(info, List.of("summary"));
        if (info != null) {
            Kept.restore(info.get("license"), List.of("identifier"));
        }
        if (root.get("components") instanceof ObjectNode components) {
            Kept.restore(components, List.of(Oas31To30.PATH_ITEMS));
            String kept = Kept.name(Oas31To30.SECURITY_SCHEMES);
            if (components.get(kept) instanceof ObjectNode moved) {
                ObjectNode schemes = JsonNodes.objectChild(components, Oas31To30.SECURITY_SCHEMES);
                for (String name : JsonNodes.fieldNames(moved)) {
                    if (!schemes.has(name)) {
                        schemes.set(name, moved.remove(name));
                    }
                }
                if (moved.isEmpty()) {
                    components.remove(kept);
                }
            }
        }
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
            Kept.setAside(schema, NULLABLE, location,
                    "nullable: true without a type has no effect in OAS 3.0.3 and no OAS 3.1 equivalent",
                    "to make it effective, admit null with a type that lists \"null\", or anyOf with {type: \"null\"}",
                    conflicts);
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
            Kept.setAside(schema, exclusive, location,
                    exclusive + ": true without " + bound + " has no effect in OAS 3.0 and no OAS 3.1 equivalent",
                    "to make it effective, set " + exclusive + " to the bound itself", conflicts);
        }
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
