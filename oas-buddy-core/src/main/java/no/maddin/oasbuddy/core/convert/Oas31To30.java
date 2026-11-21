package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.document.SchemaWalker;

import java.util.ArrayList;
import java.util.List;

/**
 * OAS 3.1 → 3.0, the reverse of {@link Oas30To31}, and the one direction that can lose something.
 *
 * <p>Respelled, in place:
 * <ul>
 *   <li>{@code type: [string, "null"]} becomes {@code type: string} followed by {@code nullable: true};
 *       a one-element list becomes the plain type.</li>
 *   <li>A numeric {@code exclusiveMinimum: n} becomes {@code minimum: n} followed by
 *       {@code exclusiveMinimum: true} (likewise for the maximum).</li>
 * </ul>
 *
 * <p>Set aside as {@code x-oasbuddy-<keyword>} and reported (see {@link Kept}): {@code webhooks},
 * {@code jsonSchemaDialect}, {@code info.summary}, {@code license.identifier},
 * {@code components.pathItems}, the JSON Schema 2020-12 keywords 3.0 does not have
 * ({@link #SCHEMA_KEYWORDS_ONLY_IN_31}), a {@code type} list naming several types or only
 * {@code "null"}, and an exclusive bound beside an inclusive one. A {@code mutualTLS} security scheme
 * cannot stay in {@code securitySchemes}, where 3.0 would read it as a scheme with an unknown type,
 * so the whole scheme moves to {@code components.x-oasbuddy-securitySchemes}.
 *
 * <p>Keywords {@link Oas30To31} set aside are restored first. An exclusive bound is restored only
 * in its 3.0 (boolean) form: a numeric one was set aside by this step on an earlier conversion.
 */
public final class Oas31To30 implements ConversionStep {

    /** Schema keywords of JSON Schema 2020-12 that the OAS 3.0 schema dialect does not have. */
    static final List<String> SCHEMA_KEYWORDS_ONLY_IN_31 = List.of(
            "$schema", "$id", "$anchor", "$dynamicAnchor", "$dynamicRef", "$defs", "$comment",
            "const", "examples", "prefixItems", "contains", "minContains", "maxContains",
            "if", "then", "else", "dependentSchemas", "dependentRequired",
            "unevaluatedItems", "unevaluatedProperties", "propertyNames",
            "contentEncoding", "contentMediaType", "contentSchema");

    /** The components section 3.1 added. */
    static final String PATH_ITEMS = "pathItems";
    static final String SECURITY_SCHEMES = "securitySchemes";
    static final String MUTUAL_TLS = "mutualTLS";

    private static final String NOT_IN_30 = " does not exist in OAS 3.0";
    private static final String NULLABLE = "nullable";
    private static final String TYPE = "type";
    private static final String NULL = "null";

    @Override
    public OasVersion from() {
        return OasVersion.V3_1;
    }

    @Override
    public OasVersion to() {
        return OasVersion.V3_0;
    }

    @Override
    public List<Conflict> apply(ObjectNode root) {
        List<Conflict> conflicts = new ArrayList<>();
        List<String> top = List.of();
        Kept.setAside(root, "webhooks", top, "webhooks" + NOT_IN_30, null, conflicts);
        Kept.setAside(root, "jsonSchemaDialect", top, "jsonSchemaDialect" + NOT_IN_30, null, conflicts);
        if (root.get("info") instanceof ObjectNode info) {
            Kept.setAside(info, "summary", List.of("info"), "info.summary" + NOT_IN_30,
                    "fold it into the description", conflicts);
            if (info.get("license") instanceof ObjectNode license) {
                Kept.setAside(license, "identifier", List.of("info", "license"),
                        "license.identifier" + NOT_IN_30, "give the license a url instead", conflicts);
            }
        }
        if (root.get("components") instanceof ObjectNode components) {
            Kept.setAside(components, PATH_ITEMS, List.of("components"),
                    "components.pathItems" + NOT_IN_30, null, conflicts);
            moveMutualTls(components, conflicts);
        }
        SchemaWalker.walk(root, OasVersion.V3_1, (schema, location) -> {
            Kept.restore(schema, List.of(NULLABLE));
            Kept.restoreIf(schema, Oas30To31.EXCLUSIVE_MINIMUM, JsonNode::isBoolean);
            Kept.restoreIf(schema, Oas30To31.EXCLUSIVE_MAXIMUM, JsonNode::isBoolean);
            for (String keyword : SCHEMA_KEYWORDS_ONLY_IN_31) {
                Kept.setAside(schema, keyword, location, "the schema keyword " + keyword + NOT_IN_30,
                        null, conflicts);
            }
            convertType(schema, location, conflicts);
            convertExclusive(schema, "minimum", Oas30To31.EXCLUSIVE_MINIMUM, location, conflicts);
            convertExclusive(schema, "maximum", Oas30To31.EXCLUSIVE_MAXIMUM, location, conflicts);
        });
        root.put("openapi", OasVersion.V3_0.newDocumentVersion());
        return conflicts;
    }

    private static void moveMutualTls(ObjectNode components, List<Conflict> conflicts) {
        if (!(components.get(SECURITY_SCHEMES) instanceof ObjectNode schemes)) {
            return;
        }
        for (String name : JsonNodes.fieldNames(schemes)) {
            JsonNode scheme = schemes.get(name);
            if (scheme instanceof ObjectNode && MUTUAL_TLS.equals(scheme.path(TYPE).asText())) {
                String kept = Kept.name(SECURITY_SCHEMES);
                JsonNodes.objectChild(components, kept).set(name, schemes.remove(name));
                conflicts.add(Conflict.at(List.of("components", SECURITY_SCHEMES, name),
                        "mutualTLS security schemes do not exist in OAS 3.0",
                        "kept under components → " + kept
                                + "; security requirements naming it now name no scheme"));
            }
        }
    }

    private static void convertType(ObjectNode schema, List<String> location, List<Conflict> conflicts) {
        if (!(schema.get(TYPE) instanceof ArrayNode types)) {
            return;
        }
        List<JsonNode> real = new ArrayList<>();
        boolean nullable = false;
        for (JsonNode type : types) {
            if (NULL.equals(type.asText())) {
                nullable = true;
            } else {
                real.add(type);
            }
        }
        if (real.size() == 1) {
            schema.set(TYPE, real.getFirst());
            if (nullable) {
                JsonNodes.setAfter(schema, TYPE, NULLABLE, BooleanNode.TRUE);
            }
        } else if (real.isEmpty()) {
            Kept.setAside(schema, TYPE, location, "a type that admits only null has no OAS 3.0 equivalent",
                    "the schema now admits any type", conflicts);
        } else {
            Kept.setAside(schema, TYPE, location, "a list of several types has no OAS 3.0 equivalent",
                    "the schema now admits any type; use oneOf to restrict it", conflicts);
        }
    }

    private static void convertExclusive(ObjectNode schema, String bound, String exclusive,
                                         List<String> location, List<Conflict> conflicts) {
        JsonNode value = schema.get(exclusive);
        if (value == null || !value.isNumber()) {
            // absent, or already the boolean 3.0 form
            return;
        }
        if (schema.has(bound)) {
            Kept.setAside(schema, exclusive, location,
                    "OAS 3.0 cannot combine " + bound + " with a separate " + exclusive,
                    bound + " still applies", conflicts);
            return;
        }
        JsonNodes.renameField(schema, exclusive, bound);
        JsonNodes.setAfter(schema, bound, exclusive, BooleanNode.TRUE);
    }
}
