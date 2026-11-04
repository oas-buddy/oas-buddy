package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.LazyObjectNode;

import java.util.List;
import java.util.function.Function;

/**
 * One named map under {@code components} ({@code schemas}, {@code headers}, …). Reads go through
 * {@link LazyObjectNode#peek}, so looking never adds {@code components} or the section; only
 * {@link #add} does. Unlike {@link NamedMap}, removing the last entry leaves the section in place.
 * The per-kind subclasses keep their own method names ({@code getHeader}, …) as thin wrappers.
 */
public class ComponentSection<T> {

    private final LazyObjectNode node;
    private final Function<ObjectNode, T> wrapper;

    ComponentSection(LazyObjectNode node, Function<ObjectNode, T> wrapper) {
        this.node = node;
        this.wrapper = wrapper;
    }

    /** Every key, including one whose value is not an object, so the file is never hidden from the user. */
    public List<String> names() {
        return JsonNodes.fieldNames(node.peek());
    }

    /** The named entry, or {@code null} if it is absent or not an object. */
    public T get(String name) {
        ObjectNode existing = node.peek();
        return existing == null ? null : JsonNodes.wrap(existing.get(name), wrapper);
    }

    /** Adds an empty entry, or returns the existing one of that name. */
    public T add(String name) {
        return wrapper.apply(JsonNodes.objectChild(node.create(), name));
    }

    public void remove(String name) {
        ObjectNode existing = node.peek();
        if (existing != null) {
            existing.remove(name);
        }
    }

    final ObjectNode peek() {
        return node.peek();
    }
}
