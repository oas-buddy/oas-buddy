package no.maddin.oasbuddy.core.model;

import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.LazyObjectNode;

public final class Schemas extends ComponentSection<Schema> {

    public Schemas(LazyObjectNode node) {
        super(node, Schema::new);
    }

    public Schema getSchema(String name) {
        return get(name);
    }

    public Schema addSchema(String name) {
        return add(name);
    }

    public void removeSchema(String name) {
        remove(name);
    }

    /**
     * Renames a schema, keeping its position in the document. Does not touch any {@code $ref}
     * that points at it — see {@link SchemaReferences#rewrite}.
     *
     * @return {@code false}, changing nothing, if {@code from} does not exist or {@code to} is
     *         already taken (a collision is refused, never silently overwritten)
     */
    public boolean renameSchema(String from, String to) {
        return peek() != null && JsonNodes.renameField(peek(), from, to);
    }
}
