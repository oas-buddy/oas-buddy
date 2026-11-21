package no.maddin.oasbuddy.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.desktop.pane.ImportOnlyPane;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Swagger 2.0 file opens read-only: a note instead of the editor panes, no saving, validation as usual. */
class MainAppImportOnlyTest extends ApplicationTest {

    private static final String SWAGGER = """
            swagger: '2.0'
            info:
              title: Legacy pets
              version: 1.0.0
            host: api.example.com
            paths:
              /pets:
                get:
                  responses:
                    '200':
                      description: ok
            """;

    @TempDir
    Path tempDir;

    private MainApp app;
    private Stage stage;
    private Path file;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        app = new MainApp();
        app.start(stage);
    }

    @BeforeEach
    void openTheSwaggerFile() throws IOException {
        file = tempDir.resolve("legacy.yaml");
        Files.writeString(file, SWAGGER);
        interact(() -> app.open(file));
    }

    @Test
    void theTitleSaysItIsSwagger20() {
        assertEquals("legacy.yaml (Swagger 2.0) — OAS Buddy", stage.getTitle());
    }

    @Test
    void theOutlineHasOnlyTheReadOnlyNoteAndItIsShown() {
        TreeView<Object> outline = lookup(".tree-view").query();

        assertEquals(List.of("Swagger 2.0 (read-only)"),
                outline.getRoot().getChildren().stream().map(child -> String.valueOf(child.getValue())).toList());
        Label note = lookup("#" + ImportOnlyPane.ID).queryAs(Label.class);
        assertTrue(note.isVisible());
        assertTrue(note.getText().startsWith("Swagger 2.0 documents can be read and validated"), note.getText());
    }

    @Test
    void conversionIsOfferedButNotYetAvailable() {
        for (OasVersion target : List.of(OasVersion.V3_0, OasVersion.V3_1)) {
            Button convert = lookup("#" + ImportOnlyPane.ID + "-convert-to-" + target.name()).queryButton();
            assertEquals("Convert to " + target.label() + "…", convert.getText());
            assertTrue(convert.isDisabled());
        }
    }

    @Test
    void saveAndSaveAsAreDisabledUntilTheDocumentIsNoLonger20() {
        assertTrue(fileItem("Save").isDisable());
        assertTrue(fileItem("Save As...").isDisable());

        interact(() -> app.newDocument(OasVersion.V3_0));

        assertFalse(fileItem("Save").isDisable());
        assertFalse(fileItem("Save As...").isDisable());
        TreeView<Object> outline = lookup(".tree-view").query();
        assertEquals("Info", String.valueOf(outline.getRoot().getChildren().getFirst().getValue()));
    }

    @Test
    void validationStillRuns() {
        interact(() -> lookup(".button").queryAll().stream()
                .map(Button.class::cast)
                .filter(button -> "Validate".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .fire());

        ListView<String> messages = lookup(".list-view").queryAs(ListView.class);
        assertEquals(List.of("No validation errors."), messages.getItems());
    }

    @Test
    void openingItChangesNothing() throws IOException {
        JsonNode onDisk = DocumentReader.read(Files.readString(file), DocumentFormat.YAML).getRoot();

        assertEquals(onDisk, app.getDocument().getRoot());
        assertEquals(SWAGGER, Files.readString(file));
    }

    private MenuItem fileItem(String text) {
        MenuBar menuBar = lookup(".menu-bar").queryAs(MenuBar.class);
        Menu file = menuBar.getMenus().stream()
                .filter(menu -> "File".equals(menu.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no File menu"));
        return file.getItems().stream()
                .filter(item -> text.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " item"));
    }
}
