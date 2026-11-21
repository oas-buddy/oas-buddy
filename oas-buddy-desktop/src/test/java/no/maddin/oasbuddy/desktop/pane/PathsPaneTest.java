package no.maddin.oasbuddy.desktop.pane;

import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.OasDocument;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathsPaneTest extends ApplicationTest {

    private final List<String> removalRequests = new ArrayList<>();
    private final List<String> structureChanges = new ArrayList<>();
    private OasDocument document;

    @Override
    public void start(Stage stage) {
        document = OasDocument.newDocument(DocumentFormat.YAML);
        document.getPaths().addPath("/pets");
        document.getPaths().addPath("/pets/{petId}");

        Node pane = PathsPane.build(document, () -> structureChanges.add("changed"), removalRequests::add);
        stage.setScene(new Scene(new StackPane(pane), 900, 600));
        stage.show();
    }

    @Test
    void listsOneRemovableRowPerPath() {
        assertEquals(List.of("/pets", "/pets/{petId}"), GridPanes.entries(list()));
    }

    @Test
    void askingToRemoveAPathReportsThatPath() {
        interact(() -> GridPanes.buttonInRowOf(list(), "/pets/{petId}").fire());

        assertEquals(List.of("/pets/{petId}"), removalRequests);
    }

    @Test
    void removalIsNotAppliedByThePaneItself() {
        interact(() -> GridPanes.buttonInRowOf(list(), "/pets").fire());

        assertTrue(GridPanes.entries(list()).contains("/pets"), "the pane must delegate, not delete");
    }

    @Test
    void addingAPathAddsItStrippedAndReportsTheChange() {
        interact(() -> {
            lookup(".text-field").queryAs(TextField.class).setText("  /owners ");
            addButton().fire();
        });

        assertEquals(List.of("/pets", "/pets/{petId}", "/owners"), document.getPaths().pathNames());
        assertEquals(List.of("changed"), structureChanges);
    }

    @Test
    void aBlankPathIsNotAdded() {
        interact(() -> addButton().fire());

        assertEquals(List.of("/pets", "/pets/{petId}"), document.getPaths().pathNames());
        assertEquals(List.of(), structureChanges);
    }

    private Button addButton() {
        return lookup(".button").queryAll().stream()
                .map(Button.class::cast)
                .filter(button -> "Add path".equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no Add path button"));
    }

    private GridPane list() {
        return lookup("#paths-list").queryAs(GridPane.class);
    }
}
