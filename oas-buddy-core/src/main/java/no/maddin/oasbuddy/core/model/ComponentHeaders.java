package no.maddin.oasbuddy.core.model;

import no.maddin.oasbuddy.core.document.LazyObjectNode;

/** The named map at {@code components.headers}: headers defined once and referenced from responses. */
public final class ComponentHeaders extends ComponentSection<Header> {

    /** The {@code components} field these live in, as {@link ComponentReferences} names it. */
    public static final String SECTION = "headers";

    public ComponentHeaders(LazyObjectNode node) {
        super(node, Header::new);
    }

    public Header getHeader(String name) {
        return get(name);
    }

    public Header addHeader(String name) {
        return add(name);
    }

    public void removeHeader(String name) {
        remove(name);
    }
}
