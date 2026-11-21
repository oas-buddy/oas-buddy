package no.maddin.oasbuddy.desktop;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import no.maddin.oasbuddy.core.document.FormatOptions;
import no.maddin.oasbuddy.core.document.LineEnding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saving applies the repository's .editorconfig first and the user's preferences for the rest. */
class MainAppFormattingTest extends ApplicationTest {

    private static final String PETS = """
            {
              "openapi" : "3.0.3",
              "info" : { "title" : "Pets", "version" : "1.0.0" },
              "tags" : [ { "name" : "pets" } ],
              "paths" : { }
            }""";

    @TempDir
    Path dir;

    private MainApp app;

    @Override
    public void start(Stage stage) {
        app = new MainApp();
        app.start(stage);
        // never the developer's own stored preferences
        app.setFormatPreferences(FormatOptions.DEFAULTS);
    }

    @Test
    void theEditorConfigBesideTheFileDecidesTheLayout() throws IOException {
        Files.writeString(dir.resolve(".editorconfig"), """
                root = true

                [*.json]
                indent_size = 4
                end_of_line = lf
                insert_final_newline = true
                ij_json_array_wrapping = split_into_lines
                """);
        Path file = save("pets.json");

        String written = Files.readString(file);
        assertTrue(written.contains("\n    \"info\": {\n        \"title\": \"Pets\""), written);
        assertTrue(written.contains("\"tags\": [\n        {\n            \"name\": \"pets\""), written);
        assertTrue(written.endsWith("}\n"), written);
    }

    @Test
    void preferencesFillWhatTheEditorConfigLeavesOpen() throws IOException {
        Files.writeString(dir.resolve(".editorconfig"), """
                root = true

                [*]
                indent_size = 4
                """);
        app.setFormatPreferences(new FormatOptions(2, null, null, null, LineEnding.CRLF, true));

        String written = Files.readString(save("pets.json"));

        assertTrue(written.startsWith("{\r\n    \"openapi\""), "indent from .editorconfig, CRLF from preferences");
        assertTrue(written.endsWith("}\r\n"), written);
    }

    @Test
    void fileSettingsOpensTheSettings() {
        List<String> opened = new ArrayList<>();
        app.setSettingsAction(() -> opened.add("settings"));

        interact(() -> fileItem("Settings...").fire());

        assertEquals(List.of("settings"), opened);
    }

    /** Opens the JSON in {@link #dir} under {@code name} and fires a plain Save, which writes it back in place. */
    private Path save(String name) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, PETS);
        interact(() -> app.open(file));
        interact(() -> fileItem("Save").fire());
        return file;
    }

    private MenuItem fileItem(String text) {
        MenuBar menuBar = lookup(".menu-bar").queryAs(MenuBar.class);
        Menu fileMenu = menuBar.getMenus().stream()
                .filter(menu -> "File".equals(menu.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no File menu"));
        return fileMenu.getItems().stream()
                .filter(item -> text.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " item"));
    }
}
