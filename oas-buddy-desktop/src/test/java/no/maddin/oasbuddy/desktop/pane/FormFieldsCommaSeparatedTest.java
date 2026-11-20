package no.maddin.oasbuddy.desktop.pane;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static java.time.Duration.ofSeconds;

class FormFieldsCommaSeparatedTest {

    @Test
    void stripsEachEntry() {
        assertEquals(List.of("id", "name", "tag"), FormFields.commaSeparated("  id ,name,   tag  "));
    }

    @Test
    void dropsEmptyEntries() {
        assertEquals(List.of("id", "name"), FormFields.commaSeparated("id, ,,name,"));
    }

    @Test
    void blankOrMissingTextIsAnEmptyList() {
        assertEquals(List.of(), FormFields.commaSeparated(null));
        assertEquals(List.of(), FormFields.commaSeparated("   "));
    }

    /** The pattern this replaced, {@code \s*,\s*}, backtracks super-linearly on a long run of spaces. */
    @Test
    void aLongRunOfSpacesWithoutACommaStaysFast() {
        String text = "a" + " ".repeat(100_000) + "b";

        List<String> values = assertTimeoutPreemptively(ofSeconds(1), () -> FormFields.commaSeparated(text));

        assertEquals(List.of(text), values);
    }
}
