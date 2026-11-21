package no.maddin.oasbuddy.core.document;

import org.ec4j.core.Cache;
import org.ec4j.core.EditorConfigLoader;
import org.ec4j.core.Resource;
import org.ec4j.core.ResourcePropertiesService;
import org.ec4j.core.model.Property;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The formatting a repository asks for through {@code .editorconfig}, for one file. Glob matching,
 * section precedence and the walk up to {@code root = true} are left to ec4j.
 *
 * <p>Read are the standard {@code indent_style}, {@code indent_size} (with {@code tab_width} for
 * {@code indent_size = tab}), {@code end_of_line} and {@code insert_final_newline}, and two of
 * IntelliJ's own keys for what the standard does not cover: {@code ij_json_array_wrapping} and
 * {@code ij_yaml_indent_sequence_value}. Reading IntelliJ's keys rather than inventing ours means a
 * repository already formatted by IntelliJ comes out the same here.
 *
 * <p>A value this class does not understand is skipped, never an error: the option falls through to
 * the user's preferences. An {@code .editorconfig} that cannot be read is treated as absent — a broken
 * config file must not stop a save.
 */
public final class EditorConfigFormat {

    private static final System.Logger LOG = System.getLogger(EditorConfigFormat.class.getName());

    /**
     * No cache: the app runs for hours, and an edited {@code .editorconfig} should apply to the next
     * save. Reading a few small files per save costs nothing worth caching.
     */
    private static final ResourcePropertiesService SERVICE = ResourcePropertiesService.builder()
            .cache(Cache.Caches.none())
            .loader(EditorConfigLoader.default_())
            .keepUnset(false)
            .build();

    private static final String TAB = "tab";

    private static final Map<String, Boolean> BOOLEAN = Map.of("true", true, "false", false);
    /** Whether to indent with tabs. */
    private static final Map<String, Boolean> INDENT_STYLE = Map.of(TAB, true, "space", false);
    /**
     * IntelliJ's wrapping values, as "one element per line": chop down and wrap always put every
     * element on its own line; do not wrap and wrap-if-long keep them on one (this writer has no
     * line-length budget to wrap against).
     */
    private static final Map<String, Boolean> ARRAY_WRAPPING = Map.of(
            "split_into_lines", true, "on_every_item", true, "off", false, "normal", false);
    /** {@code cr} is a valid EditorConfig value, but no OpenAPI tool expects it; it is not mapped. */
    private static final Map<String, LineEnding> END_OF_LINE = Map.of("lf", LineEnding.LF, "crlf", LineEnding.CRLF);

    private EditorConfigFormat() {
    }

    /**
     * The options the {@code .editorconfig} files above {@code file} set for it; {@link FormatOptions#DEFAULTS}
     * when there are none. The file itself need not exist yet (a Save As target).
     */
    public static FormatOptions forFile(Path file) {
        try {
            Resource resource = Resource.Resources.ofPath(file.toAbsolutePath(), StandardCharsets.UTF_8);
            return from(lowerCased(SERVICE.queryProperties(resource).getProperties()));
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "could not read .editorconfig for " + file, e);
            return FormatOptions.DEFAULTS;
        }
    }

    /** EditorConfig keys and values are case-insensitive. */
    private static Map<String, String> lowerCased(Map<String, Property> properties) {
        Map<String, String> values = new HashMap<>();
        properties.forEach((name, property) -> {
            if (property.getSourceValue() != null) {
                values.put(name.toLowerCase(Locale.ROOT), property.getSourceValue().strip().toLowerCase(Locale.ROOT));
            }
        });
        return values;
    }

    static FormatOptions from(Map<String, String> values) {
        return new FormatOptions(
                indentSize(values),
                lookup(INDENT_STYLE, values.get("indent_style")),
                lookup(ARRAY_WRAPPING, values.get("ij_json_array_wrapping")),
                lookup(BOOLEAN, values.get("ij_yaml_indent_sequence_value")),
                lookup(END_OF_LINE, values.get("end_of_line")),
                lookup(BOOLEAN, values.get("insert_final_newline")));
    }

    /** {@code indent_size = tab} means "as wide as a tab", i.e. {@code tab_width}. */
    private static Integer indentSize(Map<String, String> values) {
        String size = values.get("indent_size");
        return positiveInteger(TAB.equals(size) ? values.get("tab_width") : size);
    }

    private static Integer positiveInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            int number = Integer.parseInt(value);
            return number > 0 ? number : null;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /** The value a table maps {@code key} to; {@code null} — "not set" — for no key or one it does not know. */
    private static <T> T lookup(Map<String, T> table, String key) {
        return key == null ? null : table.get(key);
    }
}
