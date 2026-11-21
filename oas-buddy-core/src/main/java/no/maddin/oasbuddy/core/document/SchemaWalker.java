package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Visits every Schema Object in a document, found by where OAS puts schemas rather than by looking
 * for schema keywords anywhere: a property may well be <em>named</em> {@code minLength} or
 * {@code nullable}, and that is not a keyword.
 *
 * <p>Reusable schemas live under {@code components.schemas} in 3.x and under {@code definitions} in
 * Swagger 2.0; everywhere else every version reaches a schema through a {@code schema} key (a
 * parameter, a media type, a header — including inside callbacks and webhooks). Within a schema the
 * walk follows {@code properties}, {@code items}, {@code additionalProperties}, {@code not},
 * {@code allOf}, {@code anyOf} and {@code oneOf}. Example data ({@code example}, {@code examples},
 * {@code default}, {@code enum}) is never entered: it may contain anything.
 *
 * <p>An {@code x-} extension is never entered: whatever it holds is not an OAS object, and a
 * conversion keeps keywords the target version lacks under {@code x-oas-buddy-…} precisely so they
 * stay untouched. (Inside {@code properties} a key is a property name, so {@code x-foo} is walked
 * there like any other.)
 *
 * <p>A schema is visited before its children, so a visitor may rewrite the schema's own keys
 * (rename, replace, remove) and the walk then descends into what it left.
 */
public final class SchemaWalker {

    /** Receives each schema and the field names leading to it from the root ({@code [i]} for an array element). */
    @FunctionalInterface
    public interface Visitor {
        void visit(ObjectNode schema, List<String> location);
    }

    private static final Set<String> SCHEMA_MAPS = Set.of("properties");
    private static final Set<String> SCHEMA_FIELDS = Set.of("schema", "items", "additionalProperties", "not");
    private static final Set<String> SCHEMA_LISTS = Set.of("allOf", "anyOf", "oneOf");
    private static final Set<String> DATA = Set.of("example", "examples", "default", "enum");

    private static final String DEFINITIONS = "definitions";
    private static final String COMPONENTS = "components";
    private static final String SCHEMAS = "schemas";

    private final Visitor visitor;
    private final Deque<String> location = new ArrayDeque<>();

    private SchemaWalker(Visitor visitor) {
        this.visitor = visitor;
    }

    public static void walk(ObjectNode root, OasVersion version, Visitor visitor) {
        new SchemaWalker(visitor).walkRoot(root, version);
    }

    private void walkRoot(ObjectNode root, OasVersion version) {
        boolean swagger20 = version == OasVersion.V2_0;
        if (swagger20) {
            descend(DEFINITIONS, () -> eachSchemaIn(root.get(DEFINITIONS)));
        } else {
            descend(COMPONENTS, () -> descend(SCHEMAS, () -> eachSchemaIn(root.path(COMPONENTS).get(SCHEMAS))));
        }
        for (String key : JsonNodes.fieldNames(root)) {
            if (key.equals(COMPONENTS)) {
                descend(key, () -> {
                    for (String section : JsonNodes.fieldNames(root.get(COMPONENTS))) {
                        if (!section.equals(SCHEMAS) && !isExtension(section)) {
                            descend(section, () -> findSchemaFields(root.get(COMPONENTS).get(section)));
                        }
                    }
                });
            } else if (!key.equals(DEFINITIONS) && !isExtension(key)) {
                descend(key, () -> findSchemaFields(root.get(key)));
            }
        }
    }

    private void eachSchemaIn(JsonNode map) {
        for (String name : JsonNodes.fieldNames(map)) {
            descend(name, () -> schema(map.get(name)));
        }
    }

    /** Outside component schemas, everything reaches its schemas through a {@code schema} key. */
    private void findSchemaFields(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            for (String key : JsonNodes.fieldNames(objectNode)) {
                if (key.equals("schema")) {
                    descend(key, () -> schema(objectNode.get(key)));
                } else if (!DATA.contains(key) && !isExtension(key)) {
                    descend(key, () -> findSchemaFields(objectNode.get(key)));
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode element = arrayNode.get(i);
                descend("[" + i + "]", () -> findSchemaFields(element));
            }
        }
    }

    private void schema(JsonNode node) {
        if (!(node instanceof ObjectNode schema)) {
            return;
        }
        visitor.visit(schema, List.copyOf(location));
        for (Map.Entry<String, JsonNode> entry : schema.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (SCHEMA_FIELDS.contains(key)) {
                descend(key, () -> schema(value));
            } else if (SCHEMA_MAPS.contains(key)) {
                descend(key, () -> eachSchemaIn(value));
            } else if (SCHEMA_LISTS.contains(key) && value instanceof ArrayNode list) {
                descend(key, () -> {
                    for (int i = 0; i < list.size(); i++) {
                        JsonNode element = list.get(i);
                        descend("[" + i + "]", () -> schema(element));
                    }
                });
            }
        }
    }

    private static boolean isExtension(String key) {
        return key.startsWith("x-");
    }

    private void descend(String segment, Runnable action) {
        location.addLast(segment);
        try {
            action.run();
        } finally {
            location.removeLast();
        }
    }
}
