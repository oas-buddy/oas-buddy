package no.maddin.oasbuddy.core.model;

import no.maddin.oasbuddy.core.document.LazyObjectNode;

/** The named map at {@code components.securitySchemes}. */
public final class SecuritySchemes extends ComponentSection<SecurityScheme> {

    public SecuritySchemes(LazyObjectNode node) {
        super(node, SecurityScheme::new);
    }

    public SecurityScheme getScheme(String name) {
        return get(name);
    }

    public SecurityScheme addScheme(String name) {
        return add(name);
    }

    public void removeScheme(String name) {
        remove(name);
    }
}
