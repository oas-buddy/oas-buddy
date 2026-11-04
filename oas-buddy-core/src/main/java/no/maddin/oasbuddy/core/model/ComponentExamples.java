package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.LazyObjectNode;

import java.util.List;

/** The named map at {@code components.examples}: examples defined once and referenced from media types. */
public final class ComponentExamples {

    /** The {@code components} field these live in, as {@link ComponentReferences} names it. */
    public static final String SECTION = "examples";

    private final LazyObjectNode node;

    public ComponentExamples(LazyObjectNode node) {
        this.node = node;
    }

    public List<String> names() {
        return JsonNodes.fieldNames(node.peek());
    }

    public Example getExample(String name) {
        ObjectNode existing = node.peek();
        return existing != null && existing.get(name) instanceof ObjectNode exampleNode
                ? new Example(exampleNode)
                : null;
    }

    public Example addExample(String name) {
        return new Example(JsonNodes.objectChild(node.create(), name));
    }

    public void removeExample(String name) {
        ObjectNode existing = node.peek();
        if (existing != null) {
            existing.remove(name);
        }
    }
}
