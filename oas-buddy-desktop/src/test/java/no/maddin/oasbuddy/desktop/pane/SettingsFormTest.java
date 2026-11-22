package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import no.maddin.oasbuddy.core.document.FormatOptions;
import no.maddin.oasbuddy.core.document.LineEnding;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                SettingsForm.YAML_SEQUENCES_ID, SettingsForm.LINE_ENDING_ID, SettingsForm.FINAL_NEWLINE_ID)) {
            assertTrue(String.valueOf(box(id).getValue()).startsWith("Default"), id);
        }
        assertEquals(FormatOptions.DEFAULTS, form.options());
    }

    @Test
    void theCurrentValuesArePreselected() {
        FormatOptions current = new FormatOptions(4, true, true, true, LineEnding.CRLF, false);

        SettingsForm form = show(current);

        assertEquals("4", String.valueOf(box(SettingsForm.INDENT_SIZE_ID).getValue()));
        assertEquals("Tabs", String.valueOf(box(SettingsForm.JSON_INDENT_ID).getValue()));
        assertEquals("CRLF", String.valueOf(box(SettingsForm.LINE_ENDING_ID).getValue()));
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
            select(SettingsForm.JSON_ARRAYS_ID, "One element per line");
            select(SettingsForm.YAML_SEQUENCES_ID, "Indented under the key");
            select(SettingsForm.LINE_ENDING_ID, "LF");
            select(SettingsForm.FINAL_NEWLINE_ID, "Yes");
            select(SettingsForm.JSON_INDENT_ID, "Spaces");
        });

        assertEquals(new FormatOptions(8, false, true, true, LineEnding.LF, true), form.options());
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
