package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** A response's named {@code headers}, each inline or a reference to {@code components.headers}. */
public final class ResponseHeaders extends ReferableObjects<Header> {

    ResponseHeaders(ObjectNode parent) {
        super(parent, "headers", ComponentHeaders.SECTION, Header::new, Header::node);
    }
}
