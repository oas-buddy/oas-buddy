package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;

import java.util.List;

/**
 * A parameter: either defined inline, or a {@code $ref} to one in {@code components.parameters}.
 * Switching between the two replaces the whole object, so that is {@link Parameters}' business.
 */
public final class Parameter {

    private static final String REF = "$ref";
    private static final String STYLE = "style";
    private static final String ALLOW_RESERVED = "allowReserved";
    private static final String ALLOW_EMPTY_VALUE = "allowEmptyValue";
    private static final String EXAMPLE = "example";

    private final ObjectNode node;

    public Parameter(ObjectNode node) {
        this.node = node;
    }

    ObjectNode node() {
        return node;
    }

    public String getRef() {
        return JsonNodes.text(node, REF);
    }

    /** The component parameter this refers to, or {@code null} if it is inline (or refers elsewhere). */
    public String getReferencedParameterName() {
        return ComponentReferences.nameOf(ComponentParameters.SECTION, getRef());
    }

    public boolean isReference() {
        return node.has(REF);
    }

    public String getName() {
        return JsonNodes.text(node, "name");
    }

    public void setName(String name) {
        JsonNodes.setText(node, "name", name);
    }

    public String getIn() {
        return JsonNodes.text(node, "in");
    }

    /**
     * Changing the location re-validates what depends on it: a {@code style} the new location does
     * not allow is removed, as are {@code allowReserved} and {@code allowEmptyValue} when the
     * parameter is no longer a query one — leaving them would be state the form no longer shows
     * yet the file still carries. A location this class does not know touches nothing.
     */
    public void setIn(String in) {
        JsonNodes.setText(node, "in", in);
        List<String> valid = ParameterStyles.validFor(in);
        if (!valid.isEmpty() && getStyle() != null && !valid.contains(getStyle())) {
            node.remove(STYLE);
        }
        if (!valid.isEmpty() && !ParameterStyles.allowsReservedAndEmpty(in)) {
            node.remove(ALLOW_RESERVED);
            node.remove(ALLOW_EMPTY_VALUE);
        }
    }

    /** The written {@code style}; {@code null} when absent, meaning {@link ParameterStyles#defaultFor}. */
    public String getStyle() {
        return JsonNodes.text(node, STYLE);
    }

    public void setStyle(String style) {
        JsonNodes.setText(node, STYLE, style);
    }

    /** The written {@code explode}; {@code null} when absent, meaning {@link ParameterStyles#defaultExplode}. */
    public Boolean getExplode() {
        return JsonNodes.bool(node, "explode");
    }

    /** {@code null} removes the key, and with it the explicit choice. */
    public void setExplode(Boolean explode) {
        JsonNodes.setBool(node, "explode", explode);
    }

    public boolean isAllowReserved() {
        return Boolean.TRUE.equals(JsonNodes.bool(node, ALLOW_RESERVED));
    }

    /** Only {@code true} is written; false removes the key, like the schema flags. */
    public void setAllowReserved(boolean value) {
        JsonNodes.setBool(node, ALLOW_RESERVED, value ? Boolean.TRUE : null);
    }

    public boolean isAllowEmptyValue() {
        return Boolean.TRUE.equals(JsonNodes.bool(node, ALLOW_EMPTY_VALUE));
    }

    public void setAllowEmptyValue(boolean value) {
        JsonNodes.setBool(node, ALLOW_EMPTY_VALUE, value ? Boolean.TRUE : null);
    }

    public boolean isDeprecated() {
        return Boolean.TRUE.equals(JsonNodes.bool(node, "deprecated"));
    }

    public void setDeprecated(boolean value) {
        JsonNodes.setBool(node, "deprecated", value ? Boolean.TRUE : null);
    }

    public JsonNode getExample() {
        return node.get(EXAMPLE);
    }

    /** {@code null} removes the key. */
    public void setExample(JsonNode example) {
        if (example == null) {
            node.remove(EXAMPLE);
        } else {
            node.set(EXAMPLE, example);
        }
    }

    /** The named {@code examples}, each inline or a reference to a component example. */
    public MediaTypeExamples getExamples() {
        return new MediaTypeExamples(node);
    }

    /**
     * The {@code content} alternative to {@code schema}. A parameter using it has no {@code schema};
     * the editor preserves it and marks it as not yet editable.
     */
    public Content getContent() {
        return new Content(node);
    }

    public boolean hasContent() {
        return node.get("content") instanceof ObjectNode;
    }

    public String getDescription() {
        return JsonNodes.text(node, "description");
    }

    public void setDescription(String description) {
        JsonNodes.setText(node, "description", description);
    }

    public Boolean isRequired() {
        return JsonNodes.bool(node, "required");
    }

    public void setRequired(Boolean required) {
        JsonNodes.setBool(node, "required", required);
    }

    /** The schema, creating it if absent. */
    public Schema getSchema() {
        return new Schema(JsonNodes.objectChild(node, "schema"));
    }

    /** The schema if there is one, otherwise {@code null}. Reads only. */
    public Schema findSchema() {
        return JsonNodes.wrap(node.get("schema"), Schema::new);
    }
}
