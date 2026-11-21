package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@code info.license}. Taken through {@code info} so reading never adds an empty {@code license},
 * and clearing every field removes it again; see {@link OptionalObject}.
 *
 * <p>{@code identifier} (an SPDX expression) exists in OAS 3.1 only, and is mutually exclusive with
 * {@code url} there. Both are editable; a document carrying both, or an identifier in 3.0, is left
 * for validation to report rather than repaired here.
 */
public final class License extends OptionalObject {

    License(ObjectNode parent) {
        super(parent, "license");
    }

    public String getName() {
        return read("name");
    }

    public void setName(String name) {
        write("name", name);
    }

    /** OAS 3.1: the license as an SPDX expression. */
    public String getIdentifier() {
        return read("identifier");
    }

    public void setIdentifier(String identifier) {
        write("identifier", identifier);
    }
}
