package no.maddin.oasbuddy.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.desktop.pane.ImportOnlyPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Document → Convert to: conflicts asked about first, Cancel keeps the document, the result is saved anew. */
class MainAppConversionTest extends ApplicationTest {

    private static final String WITH_WEBHOOK = """
            openapi: 3.1.0
            info:
              title: Pets
              version: 1.0.0
            paths: {}
            webhooks:
              newPet:
                post:
                  responses:
                    '200':
                      description: ok
            """;

    @TempDir
    Path tempDir;

    private MainApp app;
    private Stage stage;
    private final List<String> asked = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        app = new MainApp();
        app.start(stage);
    }

    @Test
    void convertToOffersEveryOtherVersionButNever20() throws IOException {
        assertEquals(List.of("OpenAPI 3.1"), convertTargets());

        interact(() -> app.newDocument(OasVersion.V3_1));
        assertEquals(List.of("OpenAPI 3.0"), convertTargets());

        openFile("legacy.yaml", "swagger: '2.0'\ninfo: {title: Legacy, version: '1'}\npaths: {}\n");
        assertEquals(List.of("OpenAPI 3.0", "OpenAPI 3.1"), convertTargets());
    }

    @Test
    void aLosslessConversionHappensWithoutAsking() {
        answer(false);

        interact(() -> convertMenuItem("OpenAPI 3.1").fire());

        assertEquals(List.of(), asked);
        assertEquals(Optional.of(OasVersion.V3_1), app.getDocument().getVersion());
        assertEquals("Untitled (OpenAPI 3.1) — OAS Buddy", stage.getTitle());
        assertEquals(List.of("OpenAPI 3.0"), convertTargets(), "the menu follows the new version");
    }

    @Test
    void conflictsAreListedAndCancelKeepsTheDocument() throws IOException {
        openFile("pets.yaml", WITH_WEBHOOK);
        OasDocument before = app.getDocument();
        JsonNode tree = before.getRoot().deepCopy();
        answer(false);

        interact(() -> app.convert(OasVersion.V3_0));

        assertEquals(1, asked.size());
        assertTrue(asked.getFirst().startsWith("Convert to OpenAPI 3.0? 1 thing cannot be carried over"),
                asked.getFirst());
        assertTrue(asked.getFirst().contains("webhooks: webhooks does not exist in OAS 3.0"), asked.getFirst());
        assertSame(before, app.getDocument());
        assertEquals(tree, app.getDocument().getRoot());
        assertEquals("pets.yaml (OpenAPI 3.1) — OAS Buddy", stage.getTitle());
    }

    @Test
    void confirmingConvertsRevalidatesAndLeavesTheFileForSaveAs() throws IOException {
        Path file = openFile("pets.yaml", WITH_WEBHOOK);
        OasDocument before = app.getDocument();
        answer(true);

        interact(() -> app.convert(OasVersion.V3_0));

        assertNotSame(before, app.getDocument());
        assertEquals(Optional.of(OasVersion.V3_0), app.getDocument().getVersion());
        assertTrue(app.getDocument().getRoot().has("x-oasbuddy-webhooks"));
        assertEquals("Untitled (OpenAPI 3.0) — OAS Buddy", stage.getTitle(),
                "a converted document belongs to no file, so Save asks where to put it");
        assertEquals(WITH_WEBHOOK, Files.readString(file), "the original file is untouched");
        assertFalse(lookup(".list-view").queryListView().getItems().isEmpty(), "validation ran");
    }

    @Test
    void aSwagger20FileConvertsFromItsPageAndBecomesSavable() throws IOException {
        openFile("legacy.yaml", """
                swagger: '2.0'
                info: {title: Legacy, version: '1'}
                host: api.example.com
                paths:
                  /pets:
                    get:
                      responses:
                        '200': {description: ok}
                """);
        answer(true);

        interact(() -> lookup("#" + ImportOnlyPane.ID + "-convert-to-V3_1").queryButton().fire());

        assertEquals(Optional.of(OasVersion.V3_1), app.getDocument().getVersion());
        assertFalse(fileItem("Save").isDisable());
        assertFalse(fileItem("Save As...").isDisable());
        TreeView<Object> outline = lookup(".tree-view").query();
        assertEquals("Info", String.valueOf(outline.getRoot().getChildren().getFirst().getValue()));
        assertEquals("Untitled (OpenAPI 3.1) — OAS Buddy", stage.getTitle());
    }

    @Test
    void saveAsProposesASiblingNamedForTheVersion() {
        assertEquals(Path.of("/work/legacy-3.0.yaml"),
                MainApp.suggestedFile(Path.of("/work/legacy.yaml"), OasVersion.V3_0));
        assertEquals(Path.of("api-3.1"), MainApp.suggestedFile(Path.of("api"), OasVersion.V3_1));
    }

    private void answer(boolean yes) {
        app.setConversionConfirmation((question, details) -> {
            asked.add(question + "\n" + details);
            return yes;
        });
    }

    private Path openFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        interact(() -> app.open(file));
        return file;
    }

    private List<String> convertTargets() {
        return convertMenu().getItems().stream().map(MenuItem::getText).toList();
    }

    private MenuItem convertMenuItem(String text) {
        return convertMenu().getItems().stream()
                .filter(item -> text.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no Convert to " + text));
    }

    private Menu convertMenu() {
        return (Menu) menu("Document").getItems().stream()
                .filter(item -> "Convert to".equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no Convert to submenu"));
    }

    private MenuItem fileItem(String text) {
        return menu("File").getItems().stream()
                .filter(item -> text.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " item"));
    }

    private Menu menu(String text) {
        MenuBar menuBar = lookup(".menu-bar").queryAs(MenuBar.class);
        return menuBar.getMenus().stream()
                .filter(menu -> text.equals(menu.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " menu"));
    }
}
