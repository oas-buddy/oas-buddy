package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;

/**
 * An optional object of text fields hanging off a parent ({@code externalDocs}, {@code contact},
 * {@code license}), addressed through the <em>parent</em> so a read never adds it.
 *
 * <p>Writing a value adds the object; clearing its last field takes the key out again rather than
 * leaving {@code {}} behind (an {@code x-} extension inside counts as content and keeps it alive).
 * A key that exists but holds something other than an object reads as empty and is not
 * {@link #isEditable() editable}: writing would replace it, and the editor never rewrites what it
 * cannot show.
 *
 * <p>All three of these objects carry a {@code url}, so its accessors live here; each subclass adds
 * only its own fields.
 */
abstract class OptionalObject {

    private final ObjectNode parent;
    private final String field;

    OptionalObject(ObjectNode parent, String field) {
        this.parent = parent;
        this.field = field;
    }

    /** {@code false} when the key holds something other than an object; see the class comment. */
    public boolean isEditable() {
        JsonNode existing = parent.get(field);
        return existing == null || existing instanceof ObjectNode;
    }

    public String getUrl() {
        return read("url");
    }

    /** A blank or {@code null} value removes the field. */
    public void setUrl(String url) {
        write("url", url);
    }

    final String read(String name) {
        return parent.get(field) instanceof ObjectNode node ? JsonNodes.text(node, name) : null;
    }

    /** A blank or {@code null} value removes the field. */
    final void write(String name, String value) {
        if (!isEditable()) {
            return;
        }
        String text = value == null || value.isBlank() ? null : value;
        if (parent.get(field) instanceof ObjectNode node) {
            JsonNodes.setText(node, name, text);
            if (node.isEmpty()) {
                parent.remove(field);
            }
        } else if (text != null) {
            JsonNodes.setText(JsonNodes.objectChild(parent, field), name, text);
        }
    }
}
