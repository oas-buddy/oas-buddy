package no.maddin.oasbuddy.core.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Reading formatting from {@code .editorconfig}. Every test directory declares {@code root = true}, so
 * a real {@code .editorconfig} further up (in a home directory) cannot leak in.
 */
class EditorConfigFormatTest {

    @TempDir
    Path dir;

    @Test
    void withoutAnEditorConfigNothingIsSet() {
        assertEquals(FormatOptions.DEFAULTS, EditorConfigFormat.forFile(dir.resolve("api.yaml")));
    }

    @Test
    void theStandardKeysAreRead() throws IOException {
        config("""
                root = true

                [*]
                indent_style = space
                indent_size = 4
                end_of_line = crlf
                insert_final_newline = true
                """);

        assertEquals(new FormatOptions(4, false, null, null, LineEnding.CRLF, true),
                EditorConfigFormat.forFile(dir.resolve("api.json")));
    }

    @Test
    void sectionsMatchByFileNameAndTheLaterOneWins() throws IOException {
        config("""
                root = true

                [*]
                indent_size = 2

                [*.{yaml,yml}]
                indent_size = 4
                ij_yaml_indent_sequence_value = true

                [*.json]
                indent_style = tab
                ij_json_array_wrapping = split_into_lines
                """);

        assertEquals(new FormatOptions(4, null, null, true, null, null),
                EditorConfigFormat.forFile(dir.resolve("api.yml")));
        assertEquals(new FormatOptions(2, true, JsonLayout.wrapping(ArrayWrapping.WRAP_ALWAYS), null, null, null),
                EditorConfigFormat.forFile(dir.resolve("api.json")));
    }

    @Test
    void aNestedEditorConfigOverridesItsParent() throws IOException {
        config("""
                root = true

                [*]
                indent_size = 2
                end_of_line = lf
                """);
        Path nested = Files.createDirectory(dir.resolve("specs"));
        Files.writeString(nested.resolve(".editorconfig"), """
                [*]
                indent_size = 4
                """);

        assertEquals(new FormatOptions(4, null, null, null, LineEnding.LF, null),
                EditorConfigFormat.forFile(nested.resolve("api.yaml")));
    }

    @Test
    void anIndentSizeOfTabMeansTheTabWidth() throws IOException {
        config("""
                root = true

                [*]
                indent_size = tab
                tab_width = 8
                """);

        assertEquals(8, EditorConfigFormat.forFile(dir.resolve("api.json")).indentSize());
    }

    /** The names IntelliJ's own export writes, one per setting in its code style dialog. */
    @Test
    void intellijsWrappingValuesMapToItsFourSettings() {
        Map<String, ArrayWrapping> expected = Map.of(
                "off", ArrayWrapping.DO_NOT_WRAP,
                "normal", ArrayWrapping.WRAP_IF_LONG,
                "on_every_item", ArrayWrapping.CHOP_DOWN_IF_LONG,
                "split_into_lines", ArrayWrapping.WRAP_ALWAYS);
        expected.forEach((value, wrapping) -> assertEquals(wrapping,
                EditorConfigFormat.from(Map.of("ij_json_array_wrapping", value)).json().arrayWrapping(), value));
    }

    @Test
    void lineLengthAndBracketSpacingAreRead() {
        JsonLayout json = EditorConfigFormat.from(Map.of(
                "max_line_length", "100",
                "ij_json_spaces_within_brackets", "true",
                "ij_json_spaces_within_braces", "false")).json();

        assertEquals(new JsonLayout(null, 100, true, false), json);
    }

    /** {@code off} is the standard's "no limit"; anything else that is not a positive number is skipped. */
    @Test
    void aLineLengthOfOffMeansNoLimit() {
        assertEquals(JsonLayout.NO_LIMIT, lineLength("off"));
        assertNull(lineLength("0"));
        assertNull(lineLength("long"));
    }

    private static Integer lineLength(String value) {
        return EditorConfigFormat.from(Map.of("max_line_length", value)).json().maxLineLength();
    }

    /**
     * IntelliJ exports {@code insert_final_newline = false} whenever its "ensure a final line break"
     * option is off, meaning "don't add one", not "strip it" — read that way, a file keeps the
     * format's own default rather than losing its newline on every save.
     */
    @Test
    void aFalseFinalNewlineMeansDoNotAddOneNotStripIt() {
        assertNull(EditorConfigFormat.from(Map.of("insert_final_newline", "false")).finalNewline());
        assertEquals(true, EditorConfigFormat.from(Map.of("insert_final_newline", "true")).finalNewline());
    }

    @Test
    void valuesThisEditorDoesNotUnderstandAreSkipped() throws IOException {
        config("""
                root = true

                [*]
                indent_style = sideways
                indent_size = -3
                end_of_line = cr
                insert_final_newline = maybe
                ij_json_array_wrapping = sometimes
                ij_json_spaces_within_brackets = some
                max_line_length = long
                ij_yaml_indent_sequence_value = perhaps
                """);

        assertEquals(FormatOptions.DEFAULTS, EditorConfigFormat.forFile(dir.resolve("api.yaml")));
    }

    @Test
    void anIndentSizeThatIsNoNumberIsSkipped() {
        assertEquals(FormatOptions.DEFAULTS, EditorConfigFormat.from(Map.of("indent_size", "wide")));
        assertEquals(FormatOptions.DEFAULTS,
                EditorConfigFormat.from(Map.of("indent_size", "tab", "tab_width", "wide")));
    }

    /** A broken config must not stop a save: an unreadable one counts as absent. */
    @Test
    void anEditorConfigThatCannotBeReadCountsAsAbsent() throws IOException {
        Files.createDirectory(dir.resolve(".editorconfig"));

        assertEquals(FormatOptions.DEFAULTS, EditorConfigFormat.forFile(dir.resolve("api.yaml")));
    }

    @Test
    void keysAndValuesAreCaseInsensitive() throws IOException {
        config("""
                root = true

                [*]
                Indent_Style = TAB
                END_OF_LINE = LF
                """);

        assertEquals(new FormatOptions(null, true, null, null, LineEnding.LF, null),
                EditorConfigFormat.forFile(dir.resolve("api.json")));
    }

    @Test
    void preferencesFillWhatTheEditorConfigLeavesOpen() throws IOException {
        config("""
                root = true

                [*]
                indent_size = 4
                """);
        FormatOptions preferences = new FormatOptions(2, null, JsonLayout.wrapping(ArrayWrapping.WRAP_ALWAYS), true, LineEnding.CRLF, false);

        assertEquals(new FormatOptions(4, null, JsonLayout.wrapping(ArrayWrapping.WRAP_ALWAYS), true, LineEnding.CRLF, false),
                EditorConfigFormat.forFile(dir.resolve("api.json")).orElse(preferences));
    }

    private void config(String content) throws IOException {
        Files.writeString(dir.resolve(".editorconfig"), content);
    }
}
