package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;

import java.util.function.Function;

/** A {@link NamedMap} whose entries are objects, exposed through the facade {@code T}. */
public class NamedObjects<T> extends NamedMap {

    private final Function<ObjectNode, T> wrapper;

    NamedObjects(ObjectNode parent, String field, Function<ObjectNode, T> wrapper) {
        super(parent, field);
        this.wrapper = wrapper;
    }

    /** The named entry, or {@code null} if it is absent or not an object. */
    public T get(String name) {
        return peek() == null ? null : JsonNodes.wrap(peek().get(name), wrapper);
    }

    /** Adds an empty entry, or returns the existing one of that name. */
    public T add(String name) {
        return wrapper.apply(JsonNodes.objectChild(create(), name));
    }

    final T wrap(ObjectNode node) {
        return wrapper.apply(node);
    }
}
