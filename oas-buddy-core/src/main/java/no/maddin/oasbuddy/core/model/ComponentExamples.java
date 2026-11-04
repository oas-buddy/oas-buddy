package no.maddin.oasbuddy.core.model;

import no.maddin.oasbuddy.core.document.LazyObjectNode;

/** The named map at {@code components.examples}: examples defined once and referenced from media types and parameters. */
public final class ComponentExamples extends ComponentSection<Example> {

    /** The {@code components} field these live in, as {@link ComponentReferences} names it. */
    public static final String SECTION = "examples";

    public ComponentExamples(LazyObjectNode node) {
        super(node, Example::new);
    }

    public Example getExample(String name) {
        return get(name);
    }

    public Example addExample(String name) {
        return add(name);
    }

    public void removeExample(String name) {
        remove(name);
    }
}
