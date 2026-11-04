package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** A link's {@code parameters}: target parameter name → runtime expression (or constant). */
public final class LinkParameters extends NamedMap {

    LinkParameters(ObjectNode parent) {
        super(parent, "parameters");
    }

    /** The value, or {@code null} if there is no such parameter. */
    public JsonNode get(String name) {
        return peek() == null ? null : peek().get(name);
    }

    /** Adds or replaces; an existing key keeps its position. */
    public void put(String name, JsonNode value) {
        create().set(name, value);
    }
}
