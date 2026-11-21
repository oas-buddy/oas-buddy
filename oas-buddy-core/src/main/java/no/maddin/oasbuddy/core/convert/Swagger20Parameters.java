package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import no.maddin.oasbuddy.core.document.JsonNodes;

import java.util.List;

/**
 * The Swagger 2.0 parameter and header rules of {@link Swagger20To30}.
 *
 * <p>2.0 writes a non-body parameter's type directly on the parameter ({@code type}, {@code format},
 * {@code items}, {@code enum}, the validation keywords); 3.0 moves all of that into a
 * {@code schema}, placed where the first of those keys stood. How an array is serialized,
 * {@code collectionFormat}, becomes {@code style}/{@code explode}. A response header follows the same
 * rule.
 */
final class Swagger20Parameters {

    /** The keys of a 2.0 parameter, header or Items object that describe its value: a schema in 3.0. */
    static final List<String> SCHEMA_KEYS = List.of(
            "type", "format", "items", "default", "maximum", "exclusiveMaximum", "minimum", "exclusiveMinimum",
            "maxLength", "minLength", "pattern", "maxItems", "minItems", "uniqueItems", "enum", "multipleOf");

    static final String COLLECTION_FORMAT = "collectionFormat";
    static final String SCHEMA = "schema";
    static final String TYPE = "type";
    private static final String ITEMS = "items";
    private static final String CSV = "csv";
    private static final String MULTI = "multi";

    private Swagger20Parameters() {
    }

    /**
     * Converts a query, header or path parameter in place: its value keys become a {@code schema},
     * its {@code collectionFormat} a {@code style}/{@code explode} after it.
     */
    static void convertParameter(ObjectNode parameter, List<String> location, List<Conflict> conflicts) {
        boolean array = "array".equals(parameter.path(TYPE).asText());
        moveIntoSchema(parameter, location, conflicts);
        String format = parameter.path(COLLECTION_FORMAT).asText(CSV);
        if (!array) {
            parameter.remove(COLLECTION_FORMAT);
            return;
        }
        boolean query = "query".equals(parameter.path("in").asText());
        if (query && format.equals(MULTI)) {
            // form with explode: both are the 3.0 query defaults
            parameter.remove(COLLECTION_FORMAT);
        } else if (query && format.equals(CSV)) {
            parameter.remove(COLLECTION_FORMAT);
            JsonNodes.setAfter(parameter, SCHEMA, "explode", BooleanNode.FALSE);
        } else if (query && (format.equals("ssv") || format.equals("pipes"))) {
            parameter.remove(COLLECTION_FORMAT);
            JsonNodes.setAfter(parameter, SCHEMA, "style",
                    TextNode.valueOf(format.equals("ssv") ? "spaceDelimited" : "pipeDelimited"));
        } else if (format.equals(CSV)) {
            // simple without explode: the 3.0 path and header default
            parameter.remove(COLLECTION_FORMAT);
        } else {
            unsupportedCollectionFormat(parameter, format, location, conflicts);
        }
    }

    /** Converts a response header in place, the way {@link #convertParameter} does a header parameter. */
    static void convertHeader(ObjectNode header, List<String> location, List<Conflict> conflicts) {
        moveIntoSchema(header, location, conflicts);
        String format = header.path(COLLECTION_FORMAT).asText(CSV);
        if (format.equals(CSV)) {
            header.remove(COLLECTION_FORMAT);
        } else {
            unsupportedCollectionFormat(header, format, location, conflicts);
        }
    }

    /**
     * A 3.0 schema built from the value keys of a 2.0 parameter, header or Items object, which is
     * left alone. {@code type: file} becomes {@code type: string, format: binary}.
     */
    static ObjectNode schemaOf(ObjectNode source, List<String> location, List<Conflict> conflicts) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        for (String key : JsonNodes.fieldNames(source)) {
            if (!SCHEMA_KEYS.contains(key)) {
                continue;
            }
            JsonNode value = source.get(key);
            if (key.equals(ITEMS) && value instanceof ObjectNode items) {
                List<String> itemsLocation = Kept.append(location, ITEMS);
                ObjectNode itemsSchema = schemaOf(items, itemsLocation, conflicts);
                if (items.has(COLLECTION_FORMAT)) {
                    itemsSchema.set(COLLECTION_FORMAT, items.get(COLLECTION_FORMAT));
                    Kept.setAside(itemsSchema, COLLECTION_FORMAT, itemsLocation,
                            "a nested array's collectionFormat has no OAS 3.0 equivalent", null, conflicts);
                }
                schema.set(key, itemsSchema);
            } else {
                schema.set(key, value.deepCopy());
            }
        }
        if ("file".equals(schema.path(TYPE).asText())) {
            schema.put(TYPE, "string");
            JsonNodes.setAfter(schema, TYPE, "format", TextNode.valueOf("binary"));
        }
        return schema;
    }

    private static void moveIntoSchema(ObjectNode owner, List<String> location, List<Conflict> conflicts) {
        if (SCHEMA_KEYS.stream().noneMatch(owner::has)) {
            return;
        }
        ObjectNode schema = schemaOf(owner, Kept.append(location, SCHEMA), conflicts);
        JsonNodes.replaceFields(owner, SCHEMA_KEYS, SCHEMA, schema);
    }

    private static void unsupportedCollectionFormat(ObjectNode owner, String format, List<String> location,
                                                    List<Conflict> conflicts) {
        Kept.setAside(owner, COLLECTION_FORMAT, location,
                "collectionFormat " + format + " has no OAS 3.0 equivalent here",
                "the default serialization applies", conflicts);
    }
}
