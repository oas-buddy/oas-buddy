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
 * {@code indent_size = tab}), {@code end_of_line}, {@code insert_final_newline} and
 * {@code max_line_length}, and IntelliJ's own keys for what the standard does not cover:
 * {@code ij_json_array_wrapping}, {@code ij_json_spaces_within_brackets},
 * {@code ij_json_spaces_within_braces} and {@code ij_yaml_indent_sequence_value}. Reading IntelliJ's keys rather than inventing ours means a
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
    /**
     * Only {@code true} is applied. {@code false} is what IntelliJ exports whenever its "ensure a final
     * line break" option is off, and IntelliJ and VS Code read it as "don't add one", never as "strip
     * it". Taken literally, as the EditorConfig spec words it, every YAML file saved in such a
     * repository would lose its newline — so it counts as unset, and the format's default applies.
     */
    private static final Map<String, Boolean> FINAL_NEWLINE = Map.of("true", true);
    /** Whether to indent with tabs. */
    private static final Map<String, Boolean> INDENT_STYLE = Map.of(TAB, true, "space", false);
    /** IntelliJ's names for its four wrapping settings, as its own {@code .editorconfig} export writes them. */
    private static final Map<String, ArrayWrapping> ARRAY_WRAPPING = Map.of(
            "off", ArrayWrapping.DO_NOT_WRAP,
            "normal", ArrayWrapping.WRAP_IF_LONG,
            "on_every_item", ArrayWrapping.CHOP_DOWN_IF_LONG,
            "split_into_lines", ArrayWrapping.WRAP_ALWAYS);
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
                json(values),
                lookup(BOOLEAN, values.get("ij_yaml_indent_sequence_value")),
                lookup(END_OF_LINE, values.get("end_of_line")),
                lookup(FINAL_NEWLINE, values.get("insert_final_newline")));
    }

    private static JsonLayout json(Map<String, String> values) {
        return new JsonLayout(
                lookup(ARRAY_WRAPPING, values.get("ij_json_array_wrapping")),
                maxLineLength(values.get("max_line_length")),
                lookup(BOOLEAN, values.get("ij_json_spaces_within_brackets")),
                lookup(BOOLEAN, values.get("ij_json_spaces_within_braces")));
    }

    /** {@code off} is the standard's "no limit". */
    private static Integer maxLineLength(String value) {
        if ("off".equals(value)) {
            return JsonLayout.NO_LIMIT;
        }
        return positiveInteger(value);
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
