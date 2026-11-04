package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** A media type's (or parameter's) named {@code examples}, each inline or a reference to {@code components.examples}. */
public final class MediaTypeExamples extends ReferableObjects<Example> {

    MediaTypeExamples(ObjectNode parent) {
        super(parent, "examples", ComponentExamples.SECTION, Example::new, Example::node);
    }
}
