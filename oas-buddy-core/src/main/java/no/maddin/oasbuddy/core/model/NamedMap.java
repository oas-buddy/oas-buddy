package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;

import java.util.List;

/**
 * A named map under a parent node ({@code headers}, {@code links}, {@code variables}, …). Lazy: a
 * read never adds the key, and removing the last entry takes it out again rather than leaving
 * {@code {}}. Plain class, not a record — the parent node must not be exposed.
 */
public class NamedMap {

    private final ObjectNode parent;
    private final String field;

    NamedMap(ObjectNode parent, String field) {
        this.parent = parent;
        this.field = field;
    }

    /** Every key, including one whose value is not an object, so the file is never hidden from the user. */
    public List<String> names() {
        return JsonNodes.fieldNames(parent.get(field));
    }

    public void remove(String name) {
        if (peek() != null) {
            peek().remove(name);
            if (peek().isEmpty()) {
                parent.remove(field);
            }
        }
    }

    /** In place, so the saved file is not reordered; {@code false} means refused. See {@link JsonNodes#renameField}. */
    public boolean rename(String from, String to) {
        return peek() != null && JsonNodes.renameField(peek(), from, to);
    }

    /** The map, or {@code null} when absent or not an object. Never creates it. */
    final ObjectNode peek() {
        return JsonNodes.objectOrNull(parent.get(field));
    }

    /** The map, added (replacing a non-object value) if need be. */
    final ObjectNode create() {
        return JsonNodes.objectChild(parent, field);
    }
}
