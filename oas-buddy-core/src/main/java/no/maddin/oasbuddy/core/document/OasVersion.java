package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The OpenAPI versions this editor knows. 3.0 and 3.1 are edited; Swagger 2.0 is only ever read,
 * to be converted.
 *
 * <p>Each constant knows the root key that declares it ({@code swagger} for 2.0, {@code openapi}
 * otherwise), which values of that key it accepts, and the value a new document is created with.
 */
public enum OasVersion {
    V2_0("swagger", Pattern.compile("2\\.0"), "2.0", "Swagger 2.0"),
    V3_0("openapi", Pattern.compile("3\\.0(\\.\\d+.*)?"), "3.0.3", "OpenAPI 3.0"),
    V3_1("openapi", Pattern.compile("3\\.1(\\.\\d+.*)?"), "3.1.0", "OpenAPI 3.1");

    private final String key;
    private final Pattern accepts;
    private final String newDocumentVersion;
    private final String label;

    OasVersion(String key, Pattern accepts, String newDocumentVersion, String label) {
        this.key = key;
        this.accepts = accepts;
        this.newDocumentVersion = newDocumentVersion;
        this.label = label;
    }

    /** The name a person knows the version by: {@code Swagger 2.0}, {@code OpenAPI 3.1}. */
    public String label() {
        return label;
    }

    /** The root key that declares this version: {@code swagger} or {@code openapi}. */
    public String key() {
        return key;
    }

    /** The version string a newly created document of this version declares. */
    public String newDocumentVersion() {
        return newDocumentVersion;
    }

    /**
     * The version a document declares, or empty when it declares none this editor knows (a missing
     * key, {@code openapi: 3.2.0}, a value that is not a version at all).
     *
     * <p>Detection only reads. A version written as a bare YAML number ({@code swagger: 2.0}) is
     * recognized by its text, and left a number in the tree.
     */
    public static Optional<OasVersion> detect(ObjectNode root) {
        for (OasVersion version : values()) {
            JsonNode value = root.get(version.key);
            if (value != null && (value.isTextual() || value.isNumber())
                    && version.accepts.matcher(value.asText()).matches()) {
                return Optional.of(version);
            }
        }
        return Optional.empty();
    }
}
