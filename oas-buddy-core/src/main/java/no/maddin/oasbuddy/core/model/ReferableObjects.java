package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.Function;

/**
 * Named objects that can each be inline or a {@code $ref} to a component. Switching replaces the
 * entry in place — {@code ObjectNode.set} on an existing key keeps its position, so nothing reorders.
 */
public class ReferableObjects<T> extends NamedObjects<T> {

    private final String section;
    private final Function<T, ObjectNode> unwrapper;

    ReferableObjects(ObjectNode parent, String field, String section,
                     Function<ObjectNode, T> wrapper, Function<T, ObjectNode> unwrapper) {
        super(parent, field, wrapper);
        this.section = section;
        this.unwrapper = unwrapper;
    }

    /**
     * Makes the named entry a reference to a component, replacing whatever was there — an inline
     * definition included, so a caller that cares asks first. Added at the end if the name is new.
     */
    public T referTo(String name, String componentName) {
        ObjectNode ref = JsonNodeFactory.instance.objectNode();
        ref.put("$ref", ComponentReferences.ref(section, componentName));
        create().set(name, ref);
        return wrap(ref);
    }

    /** Makes the named entry inline, in place, from a copy of {@code template} (what the reference pointed at). */
    public T defineInline(String name, T template) {
        ObjectNode inline = template != null ? unwrapper.apply(template).deepCopy() : JsonNodeFactory.instance.objectNode();
        create().set(name, inline);
        return wrap(inline);
    }
}
