package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The {@code externalDocs} object that can hang off the document root, a tag, an operation and a
 * schema: a {@code description} and a {@code url}.
 *
 * <p>It takes the <em>parent</em> node rather than the object itself, the same reason
 * {@link SecurityRequirements} does: a read must never add an empty {@code externalDocs}, and
 * clearing both fields must take the key out again rather than leave {@code {}} behind. An
 * {@code x-} extension inside the object counts as content, so it keeps the object alive.
 *
 * <p>An {@code externalDocs} that exists but is not an object (a hand-edited {@code ~} or string)
 * reads as empty and is {@link #isEditable() not editable}: writing would replace it, and the
 * editor never rewrites what it cannot show.
 */
public final class ExternalDocs {

    private final OptionalObject object;

    public ExternalDocs(ObjectNode parent) {
        this.object = new OptionalObject(parent, "externalDocs");
    }

    /** {@code false} when the key holds something other than an object; see the class comment. */
    public boolean isEditable() {
        return object.isEditable();
    }

    public String getDescription() {
        return object.read("description");
    }

    public void setDescription(String description) {
        object.write("description", description);
    }

    public String getUrl() {
        return object.read("url");
    }

    public void setUrl(String url) {
        object.write("url", url);
    }
}
