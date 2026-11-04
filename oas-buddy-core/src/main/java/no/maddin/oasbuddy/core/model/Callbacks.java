package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** An operation's named {@code callbacks}, each inline or a reference to {@code components.callbacks}. */
public final class Callbacks extends ReferableObjects<Callback> {

    Callbacks(ObjectNode parent) {
        super(parent, "callbacks", ComponentCallbacks.SECTION, Callback::new, Callback::node);
    }
}
