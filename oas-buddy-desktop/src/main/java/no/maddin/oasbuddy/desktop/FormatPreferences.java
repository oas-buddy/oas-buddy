package no.maddin.oasbuddy.desktop;

import no.maddin.oasbuddy.core.document.FormatOptions;
import no.maddin.oasbuddy.core.document.LineEnding;

import java.util.Map;
import java.util.prefs.Preferences;

/**
 * The user's own formatting choices, the fallback for whatever a repository's {@code .editorconfig}
 * leaves open. Kept in {@link Preferences} beside the theme, one key per option; an absent key is an
 * option left at the format's default, so a user who never opens Settings writes exactly as before.
 */
final class FormatPreferences {

    static final String INDENT_SIZE = "format.indentSize";
    static final String INDENT_WITH_TABS = "format.indentWithTabs";
    static final String JSON_ARRAYS_ONE_PER_LINE = "format.jsonArraysOnePerLine";
    static final String YAML_INDENT_SEQUENCES = "format.yamlIndentSequences";
    static final String LINE_ENDING = "format.lineEnding";
    static final String FINAL_NEWLINE = "format.finalNewline";

    private static final Map<String, Boolean> BOOLEANS = Map.of("true", true, "false", false);
    private static final Map<String, LineEnding> LINE_ENDINGS = Map.of(
            LineEnding.LF.name(), LineEnding.LF, LineEnding.CRLF.name(), LineEnding.CRLF);

    private FormatPreferences() {
    }

    static FormatOptions load(Preferences prefs) {
        return new FormatOptions(
                integer(prefs.get(INDENT_SIZE, null)),
                bool(prefs.get(INDENT_WITH_TABS, null)),
                bool(prefs.get(JSON_ARRAYS_ONE_PER_LINE, null)),
                bool(prefs.get(YAML_INDENT_SEQUENCES, null)),
                lineEnding(prefs.get(LINE_ENDING, null)),
                bool(prefs.get(FINAL_NEWLINE, null)));
    }

    static void save(Preferences prefs, FormatOptions options) {
        put(prefs, INDENT_SIZE, options.indentSize());
        put(prefs, INDENT_WITH_TABS, options.indentWithTabs());
        put(prefs, JSON_ARRAYS_ONE_PER_LINE, options.jsonArraysOnePerLine());
        put(prefs, YAML_INDENT_SEQUENCES, options.yamlIndentSequences());
        put(prefs, LINE_ENDING, options.lineEnding());
        put(prefs, FINAL_NEWLINE, options.finalNewline());
    }

    /** {@code null} removes the key, so an option set back to "Default" leaves no trace. */
    private static void put(Preferences prefs, String key, Object value) {
        if (value == null) {
            prefs.remove(key);
        } else {
            prefs.put(key, value.toString());
        }
    }

    /** Anything stored that is not a positive number reads as "not set" rather than failing the start. */
    private static Integer integer(String value) {
        if (value == null) {
            return null;
        }
        try {
            int size = Integer.parseInt(value);
            return size > 0 ? size : null;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static Boolean bool(String value) {
        return lookup(BOOLEANS, value);
    }

    private static LineEnding lineEnding(String value) {
        return lookup(LINE_ENDINGS, value);
    }

    /** What {@code table} maps {@code key} to; {@code null} — not set — for no key or one it does not know. */
    private static <T> T lookup(Map<String, T> table, String key) {
        return key == null ? null : table.get(key);
    }
}
