package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;

public final class Info {

    private final ObjectNode node;

    public Info(ObjectNode node) {
        this.node = node;
    }

    public String getTitle() {
        return JsonNodes.text(node, "title");
    }

    public void setTitle(String title) {
        JsonNodes.setText(node, "title", title);
    }

    public String getVersion() {
        return JsonNodes.text(node, "version");
    }

    public void setVersion(String version) {
        JsonNodes.setText(node, "version", version);
    }

    public String getDescription() {
        return JsonNodes.text(node, "description");
    }

    public void setDescription(String description) {
        JsonNodes.setText(node, "description", description);
    }

    public String getTermsOfService() {
        return JsonNodes.text(node, "termsOfService");
    }

    public void setTermsOfService(String termsOfService) {
        JsonNodes.setText(node, "termsOfService", termsOfService);
    }

    /** OAS 3.1: a short summary of the API. */
    public String getSummary() {
        return JsonNodes.text(node, "summary");
    }

    public void setSummary(String summary) {
        JsonNodes.setText(node, "summary", summary);
    }

    /** Reading never adds {@code contact}; see {@link Contact}. */
    public Contact getContact() {
        return new Contact(node);
    }

    /** Reading never adds {@code license}; see {@link License}. */
    public License getLicense() {
        return new License(node);
    }
}
