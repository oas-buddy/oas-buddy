package no.maddin.oasbuddy.core.model;

import no.maddin.oasbuddy.core.document.LazyObjectNode;

/** The named map at {@code components.callbacks}: callbacks defined once and referenced from operations. */
public final class ComponentCallbacks extends ComponentSection<Callback> {

    /** The {@code components} field these live in, as {@link ComponentReferences} names it. */
    public static final String SECTION = "callbacks";

    public ComponentCallbacks(LazyObjectNode node) {
        super(node, Callback::new);
    }

    public Callback getCallback(String name) {
        return get(name);
    }

    public Callback addCallback(String name) {
        return add(name);
    }

    public void removeCallback(String name) {
        remove(name);
    }
}
