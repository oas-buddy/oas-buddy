package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class JsonNodes {

    private JsonNodes() {
    }

    public static ObjectNode objectChild(ObjectNode parent, String field) {
        var existing = parent.get(field);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = JsonNodeFactory.instance.objectNode();
        parent.set(field, created);
        return created;
    }

    public static ArrayNode arrayChild(ObjectNode parent, String field) {
        var existing = parent.get(field);
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = JsonNodeFactory.instance.arrayNode();
        parent.set(field, created);
        return created;
    }

    /** The node itself when it is an object, otherwise {@code null} (absent, null, array, scalar). */
    public static ObjectNode objectOrNull(JsonNode node) {
        return node instanceof ObjectNode objectNode ? objectNode : null;
    }

    /** {@code factory} applied to the node when it is an object, otherwise {@code null}. */
    public static <T> T wrap(JsonNode node, Function<ObjectNode, T> factory) {
        return node instanceof ObjectNode objectNode ? factory.apply(objectNode) : null;
    }

    /**
     * The field names of an object in document order; empty for anything that is not an object, so
     * a caller can pass {@code parent.get(FIELD)} without checking it first. A snapshot, not a view:
     * fields may be removed while the result is being walked.
     */
    public static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(names::add);
        }
        return names;
    }

    public static String text(ObjectNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public static void setText(ObjectNode node, String field, String value) {
        if (value == null) {
            node.remove(field);
        } else {
            node.put(field, value);
        }
    }

    public static Boolean bool(ObjectNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    public static void setBool(ObjectNode node, String field, Boolean value) {
        if (value == null) {
            node.remove(field);
        } else {
            node.put(field, value);
        }
    }

    /**
     * Renames a field without moving it or copying its value, keeping every other field's
     * position. Jackson has no in-place rename, and remove-then-add would push the field to the
     * end of the map — every rename would reorder the saved file, which is exactly what the
     * ordered tree exists to prevent (the same technique {@code OAuthFlow.renameScope} used before
     * this was pulled out as the one place that does it).
     *
     * @return {@code false}, changing nothing, when {@code from} is blank or not present, or
     *         {@code to} is blank, unchanged, or already present
     */
    public static boolean renameField(ObjectNode parent, String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank() || from.equals(to)
                || !parent.has(from) || parent.has(to)) {
            return false;
        }
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        for (String name : fieldNames(parent)) {
            entries.add(Map.entry(name.equals(from) ? to : name, parent.get(name)));
        }
        parent.removeAll();
        entries.forEach(entry -> parent.set(entry.getKey(), entry.getValue()));
        return true;
    }
}
