package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import no.maddin.oasbuddy.core.document.ArrayWrapping;
import no.maddin.oasbuddy.core.document.FormatOptions;
import no.maddin.oasbuddy.core.document.JsonLayout;
import no.maddin.oasbuddy.core.document.LineEnding;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Settings dialog's form: current values shown, "Default" meaning unset, choices mapped to options. */
class SettingsFormTest extends ApplicationTest {

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.show();
    }

    @Test
    void withNothingSetEveryChoiceIsDefaultAndTheFormGivesBackTheDefaults() {
        SettingsForm form = show(FormatOptions.DEFAULTS);

        for (String id : List.of(SettingsForm.INDENT_SIZE_ID, SettingsForm.JSON_INDENT_ID, SettingsForm.JSON_ARRAYS_ID,
                SettingsForm.LINE_LENGTH_ID, SettingsForm.JSON_BRACKETS_ID, SettingsForm.JSON_BRACES_ID,
                SettingsForm.YAML_SEQUENCES_ID, SettingsForm.LINE_ENDING_ID, SettingsForm.FINAL_NEWLINE_ID)) {
            assertTrue(String.valueOf(box(id).getValue()).startsWith("Default"), id);
        }
        assertEquals(FormatOptions.DEFAULTS, form.options());
    }

    @Test
    void theCurrentValuesArePreselected() {
        FormatOptions current = new FormatOptions(4, true,
                new JsonLayout(ArrayWrapping.CHOP_DOWN_IF_LONG, 100, true, false), true, LineEnding.CRLF, false);

        SettingsForm form = show(current);

        assertEquals("4", String.valueOf(box(SettingsForm.INDENT_SIZE_ID).getValue()));
        assertEquals("Tabs", String.valueOf(box(SettingsForm.JSON_INDENT_ID).getValue()));
        assertEquals("CRLF", String.valueOf(box(SettingsForm.LINE_ENDING_ID).getValue()));
        assertEquals("Chop down if long", String.valueOf(box(SettingsForm.JSON_ARRAYS_ID).getValue()));
        assertEquals("100", String.valueOf(box(SettingsForm.LINE_LENGTH_ID).getValue()));
        assertEquals(current, form.options());
    }

    /** An indent size the list does not offer (from a hand-edited store) is kept, not replaced. */
    @Test
    void anUnusualIndentSizeIsOfferedRatherThanLost() {
        SettingsForm form = show(new FormatOptions(3, null, null, null, null, null));

        assertEquals("3", String.valueOf(box(SettingsForm.INDENT_SIZE_ID).getValue()));
        assertEquals(3, form.options().indentSize());
    }

    @Test
    void choosingValuesChangesTheOptions() {
        SettingsForm form = show(FormatOptions.DEFAULTS);

        interact(() -> {
            select(SettingsForm.INDENT_SIZE_ID, "8");
            select(SettingsForm.JSON_ARRAYS_ID, "Wrap if long");
            select(SettingsForm.LINE_LENGTH_ID, "No limit");
            select(SettingsForm.JSON_BRACKETS_ID, "Yes");
            select(SettingsForm.JSON_BRACES_ID, "No");
            select(SettingsForm.YAML_SEQUENCES_ID, "Indented under the key");
            select(SettingsForm.LINE_ENDING_ID, "LF");
            select(SettingsForm.FINAL_NEWLINE_ID, "Yes");
            select(SettingsForm.JSON_INDENT_ID, "Spaces");
        });

        assertEquals(new FormatOptions(8, false,
                new JsonLayout(ArrayWrapping.WRAP_IF_LONG, JsonLayout.NO_LIMIT, true, false),
                true, LineEnding.LF, true), form.options());
    }

    /** Line length and spacing shape IntelliJ's layouts only, so they wait for an array choice. */
    @Test
    void theIntellijOnlyChoicesAreEnabledByAnArrayWrapping() {
        show(FormatOptions.DEFAULTS);
        List<String> intellijOnly = List.of(
                SettingsForm.LINE_LENGTH_ID, SettingsForm.JSON_BRACKETS_ID, SettingsForm.JSON_BRACES_ID);

        intellijOnly.forEach(id -> assertTrue(box(id).isDisabled(), id));

        interact(() -> select(SettingsForm.JSON_ARRAYS_ID, "Do not wrap"));
        intellijOnly.forEach(id -> assertFalse(box(id).isDisabled(), id));

        interact(() -> select(SettingsForm.JSON_ARRAYS_ID, "Default (inline)"));
        intellijOnly.forEach(id -> assertTrue(box(id).isDisabled(), id));
    }

    @Test
    void anUnusualLineLengthIsOfferedRatherThanLost() {
        SettingsForm form = show(new FormatOptions(null, null,
                new JsonLayout(ArrayWrapping.WRAP_ALWAYS, 99, null, null), null, null, null));

        assertEquals("99", String.valueOf(box(SettingsForm.LINE_LENGTH_ID).getValue()));
        assertEquals(99, form.options().json().maxLineLength());
    }

    @Test
    void noLimitIsPreselectedAsSuch() {
        SettingsForm form = show(new FormatOptions(null, null,
                new JsonLayout(ArrayWrapping.WRAP_IF_LONG, JsonLayout.NO_LIMIT, null, null), null, null, null));

        assertEquals("No limit", String.valueOf(box(SettingsForm.LINE_LENGTH_ID).getValue()));
        assertEquals(JsonLayout.NO_LIMIT, form.options().json().maxLineLength());
    }

    private SettingsForm show(FormatOptions current) {
        AtomicReference<SettingsForm> form = new AtomicReference<>();
        interact(() -> {
            form.set(new SettingsForm(current));
            stage.setScene(new Scene(new StackPane(form.get().node()), 700, 500));
        });
        return form.get();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<Object> box(String id) {
        return lookup("#" + id).queryAs(ComboBox.class);
    }

    private void select(String id, String label) {
        ComboBox<Object> box = box(id);
        box.setValue(box.getItems().stream()
                .filter(item -> label.equals(String.valueOf(item)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + label + " in " + id)));
    }
}
