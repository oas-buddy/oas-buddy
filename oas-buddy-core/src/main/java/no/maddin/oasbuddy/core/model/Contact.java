package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@code info.contact}. Taken through {@code info} so reading never adds an empty {@code contact},
 * and clearing every field removes it again; see {@link OptionalObject}.
 */
public final class Contact extends OptionalObject {

    Contact(ObjectNode parent) {
        super(parent, "contact");
    }

    public String getName() {
        return read("name");
    }

    public void setName(String name) {
        write("name", name);
    }

    public String getEmail() {
        return read("email");
    }

    public void setEmail(String email) {
        write("email", email);
    }
}
