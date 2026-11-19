package no.maddin.oasbuddy.core.document;

import java.util.Locale;
import java.util.Optional;

public enum DocumentFormat {
    YAML,
    JSON;

    /**
     * The format a file name names, or empty when its extension says nothing about the format.
     *
     * <p>Reading insists on an answer (see {@link #fromFileName}); writing does not — saving to a
     * path with no recognised extension keeps the document in whatever format it already is,
     * rather than failing over a file name.
     */
    public static Optional<DocumentFormat> detect(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) {
            return Optional.of(JSON);
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return Optional.of(YAML);
        }
        return Optional.empty();
    }

    public static DocumentFormat fromFileName(String fileName) {
        return detect(fileName).orElseThrow(() ->
                new IllegalArgumentException("Unrecognized OAS file extension: " + fileName));
    }
}
