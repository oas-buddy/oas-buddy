package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.LazyObjectNode;

import java.util.List;

/**
 * OAS 3.1's root {@code webhooks}: incoming requests the API may send, each a {@link PathItem} under
 * a name of the author's choosing. Shaped like {@link ComponentCallbacks}, with one difference: an
 * absent {@code webhooks} and an empty one mean the same thing, so removing the last entry takes the
 * key out again (as {@link Tags} does), rather than leaving {@code webhooks: {}} behind.
 *
 * <p>Reads go through {@link LazyObjectNode#peek}, so looking never adds the key. An entry that is
 * not an object ({@code newPet: ~}) is listed by {@link #names()} but {@link #get} returns
 * {@code null} for it, the same rule as every other named map here.
 */
public final class Webhooks {

    /** The root field holding the webhooks. */
    public static final String FIELD = "webhooks";

    private final LazyObjectNode node;
    private final ObjectNode root;

    public Webhooks(ObjectNode root) {
        this.root = root;
        this.node = LazyObjectNode.of(root, FIELD);
    }

    public List<String> names() {
        return JsonNodes.fieldNames(node.peek());
    }

    /** The named webhook, or {@code null} if it is absent or not an object. */
    public PathItem get(String name) {
        ObjectNode existing = node.peek();
        return existing == null ? null : JsonNodes.wrap(existing.get(name), PathItem::new);
    }

    /** Adds an empty webhook, or returns the existing one of that name. */
    public PathItem add(String name) {
        return new PathItem(JsonNodes.objectChild(node.create(), name));
    }

    public void remove(String name) {
        ObjectNode existing = node.peek();
        if (existing != null) {
            existing.remove(name);
            if (existing.isEmpty()) {
                root.remove(FIELD);
            }
        }
    }

    /**
     * Renames a webhook in place. Nothing can {@code $ref} a webhook by its name, so there is nothing
     * else to fix up.
     *
     * @return {@code false}, changing nothing, on a collision, a blank name or an absent source
     */
    public boolean rename(String from, String to) {
        ObjectNode existing = node.peek();
        return existing != null && JsonNodes.renameField(existing, from, to);
    }
}
