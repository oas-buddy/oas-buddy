package no.maddin.oasbuddy.core.model;

import no.maddin.oasbuddy.core.document.LazyObjectNode;

/** The named map at {@code components.links}: links defined once and referenced from responses. */
public final class ComponentLinks extends ComponentSection<Link> {

    /** The {@code components} field these live in, as {@link ComponentReferences} names it. */
    public static final String SECTION = "links";

    public ComponentLinks(LazyObjectNode node) {
        super(node, Link::new);
    }

    public Link getLink(String name) {
        return get(name);
    }

    public Link addLink(String name) {
        return add(name);
    }

    public void removeLink(String name) {
        remove(name);
    }
}
