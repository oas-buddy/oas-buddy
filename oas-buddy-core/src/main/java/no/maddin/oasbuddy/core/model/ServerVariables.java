package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** A server's {@code variables} map. */
public final class ServerVariables extends NamedObjects<ServerVariable> {

    public ServerVariables(ObjectNode server) {
        super(server, "variables", ServerVariable::new);
    }

    /** Adds a variable with an empty default (the spec requires one), or returns the existing one. */
    @Override
    public ServerVariable add(String name) {
        ServerVariable existing = get(name);
        if (existing != null) {
            return existing;
        }
        ServerVariable created = super.add(name);
        created.setDefault("");
        return created;
    }
}
