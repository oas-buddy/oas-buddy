package no.maddin.oasbuddy.core.model;

import no.maddin.oasbuddy.core.document.LazyObjectNode;

/**
 * The named map at {@code components.parameters}: a parameter defined once and referenced from any
 * number of operations or paths. The key is how references name it; the parameter's own
 * {@code name} is what goes on the wire, and the two need not match.
 */
public final class ComponentParameters extends ComponentSection<Parameter> {

    /** The {@code components} field these live in, as {@link ComponentReferences} names it. */
    public static final String SECTION = "parameters";

    public ComponentParameters(LazyObjectNode node) {
        super(node, Parameter::new);
    }

    public Parameter getParameter(String key) {
        return get(key);
    }

    /** Adds a parameter under {@code key}, with {@code name} and {@code in} filled in. */
    public Parameter addParameter(String key, String name, String in) {
        Parameter parameter = add(key);
        parameter.setName(name);
        parameter.setIn(in);
        return parameter;
    }

    public void removeParameter(String key) {
        remove(key);
    }
}
