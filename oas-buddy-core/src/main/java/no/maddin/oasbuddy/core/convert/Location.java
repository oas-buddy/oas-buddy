package no.maddin.oasbuddy.core.convert;

import no.maddin.oasbuddy.core.model.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders a position in the document for a person reading a conflict list.
 *
 * <p>Inside an operation the location starts the way {@code TagUsages} names one, {@code GET /pets},
 * and continues as the arrow path {@code ComponentReferences} uses
 * ({@code GET /pets → responses → 200}). Anywhere else it is the arrow path alone
 * ({@code components → schemas → Pet}).
 */
public final class Location {

    static final String ROOT = "document root";
    private static final String SEPARATOR = " → ";
    private static final String PATHS = "paths";

    private Location() {
    }

    /**
     * @param segments the field names (and {@code [index]} array positions) from the root down;
     *                 empty means the root itself
     */
    public static String render(List<String> segments) {
        if (segments.isEmpty()) {
            return ROOT;
        }
        if (segments.size() < 2 || !PATHS.equals(segments.getFirst())) {
            return String.join(SEPARATOR, segments);
        }
        String path = segments.get(1);
        List<String> rest = segments.subList(2, segments.size());
        List<String> parts = new ArrayList<>();
        if (!rest.isEmpty() && isMethod(rest.getFirst())) {
            parts.add(rest.getFirst().toUpperCase(Locale.ROOT) + " " + path);
            parts.addAll(rest.subList(1, rest.size()));
        } else {
            parts.add(path);
            parts.addAll(rest);
        }
        return String.join(SEPARATOR, parts);
    }

    private static boolean isMethod(String field) {
        for (HttpMethod method : HttpMethod.values()) {
            if (method.fieldName().equals(field)) {
                return true;
            }
        }
        return false;
    }
}
