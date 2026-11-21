package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@code info.contact}. Taken through {@code info} so reading never adds an empty {@code contact},
 * and clearing every field removes it again; see {@link OptionalObject}.
 */
public final class Contact {

    private final OptionalObject object;

    Contact(ObjectNode info) {
        this.object = new OptionalObject(info, "contact");
    }

    public boolean isEditable() {
        return object.isEditable();
    }

    public String getName() {
        return object.read("name");
    }

    public void setName(String name) {
        object.write("name", name);
    }

    public String getUrl() {
        return object.read("url");
    }

    public void setUrl(String url) {
        object.write("url", url);
    }

    public String getEmail() {
        return object.read("email");
    }

    public void setEmail(String email) {
        object.write("email", email);
    }
}
