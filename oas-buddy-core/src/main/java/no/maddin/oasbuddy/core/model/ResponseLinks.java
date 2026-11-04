package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** A response's named {@code links}, each inline or a reference to {@code components.links}. */
public final class ResponseLinks extends ReferableObjects<Link> {

    ResponseLinks(ObjectNode parent) {
        super(parent, "links", ComponentLinks.SECTION, Link::new, Link::node);
    }
}
