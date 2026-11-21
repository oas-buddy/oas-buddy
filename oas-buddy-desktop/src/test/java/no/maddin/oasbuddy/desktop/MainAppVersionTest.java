package no.maddin.oasbuddy.desktop;

import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.model.HttpMethod;
import javafx.event.ActionEvent;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The window title names the document's version; File → New lets the user pick one. */
class MainAppVersionTest extends ApplicationTest {

    private MainApp app;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        app = new MainApp();
        app.start(stage);
    }

    @Test
    void theStartDocumentIs30AndTheTitleSaysSo() {
        assertEquals(Optional.of(OasVersion.V3_0), app.getDocument().getVersion());
        assertEquals("Untitled (OpenAPI 3.0) — OAS Buddy", stage.getTitle());
    }

    @Test
    void fileNewOffers30FirstThen31() {
        assertEquals(List.of("OpenAPI 3.0", "OpenAPI 3.1"),
                newMenu().getItems().stream().map(MenuItem::getText).toList());
        assertEquals("Shortcut+N", newMenu().getItems().getFirst().getAccelerator().getName());
        assertNull(newMenu().getItems().get(1).getAccelerator(), "3.0 is the default; only it has the shortcut");
    }

    @Test
    void choosing31CreatesA31DocumentAndRetitlesTheWindow() {
        interact(() -> newMenu().getItems().get(1).fire());

        assertEquals(Optional.of(OasVersion.V3_1), app.getDocument().getVersion());
        assertEquals("3.1.0", app.getDocument().getOpenApiVersion());
        assertEquals("Untitled (OpenAPI 3.1) — OAS Buddy", stage.getTitle());

        interact(() -> newMenu().getItems().getFirst().fire());

        assertEquals(Optional.of(OasVersion.V3_0), app.getDocument().getVersion());
        assertEquals("Untitled (OpenAPI 3.0) — OAS Buddy", stage.getTitle());
    }

    @Test
    void theTitleNamesTheFileAndWhatItDeclares() {
        assertEquals("api.yaml (OpenAPI 3.1) — OAS Buddy",
                MainApp.windowTitle(Path.of("/tmp/api.yaml"), Optional.of(OasVersion.V3_1)));
        assertEquals("legacy.json (Swagger 2.0) — OAS Buddy",
                MainApp.windowTitle(Path.of("legacy.json"), Optional.of(OasVersion.V2_0)));
        assertEquals("odd.yaml (unknown OpenAPI version) — OAS Buddy",
                MainApp.windowTitle(Path.of("odd.yaml"), Optional.empty()));
    }

    @Test
    void aWebhooksNodeIsShownFor31Only() {
        assertFalse(outlineLabels().contains("Webhooks"), outlineLabels()::toString);

        interact(() -> app.newDocument(OasVersion.V3_1));

        assertEquals("Webhooks", outlineLabels().get(outlineLabels().indexOf("Paths") + 1));
        assertFalse(app.getDocument().getRoot().has("webhooks"), "showing the node adds nothing");
    }

    /** The outline never hides what is in the file: a 3.0 document carrying webhooks shows them. */
    @Test
    void a30DocumentCarryingWebhooksShowsThem() {
        interact(() -> {
            app.getDocument().getWebhooks().add("newPet");
            app.refreshOutline();
        });

        assertEquals(List.of("newPet"), childrenOf("Webhooks"));
    }

    @Test
    void addingRenamingAndRemovingAWebhookKeepsTheOutlineInStep() {
        interact(() -> app.newDocument(OasVersion.V3_1));
        app.setConfirmation((question, details) -> true);
        interact(() -> {
            app.getDocument().getWebhooks().add("first");
            app.getDocument().getWebhooks().add("second").addOperation(HttpMethod.POST);
            app.getDocument().getWebhooks().add("third");
            app.refreshOutline();
        });
        selectOutline("Webhooks", "second");

        interact(() -> {
            TextField name = lookup("#webhook-name").queryAs(TextField.class);
            name.setText("middle");
            name.fireEvent(new ActionEvent());
        });
        assertEquals(List.of("first", "middle", "third"), childrenOf("Webhooks"), "renamed in place");

        interact(() -> lookup("#delete-webhook").queryButton().fire());
        assertEquals(List.of("first", "third"), childrenOf("Webhooks"));
    }

    private List<String> outlineLabels() {
        TreeView<Object> outline = lookup(".tree-view").query();
        return outline.getRoot().getChildren().stream().map(child -> String.valueOf(child.getValue())).toList();
    }

    private List<String> childrenOf(String label) {
        return outlineItem(label).getChildren().stream().map(child -> String.valueOf(child.getValue())).toList();
    }

    private TreeItem<Object> outlineItem(String label) {
        TreeView<Object> outline = lookup(".tree-view").query();
        return outline.getRoot().getChildren().stream()
                .filter(child -> label.equals(String.valueOf(child.getValue())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outline node " + label));
    }

    private void selectOutline(String section, String entry) {
        TreeView<Object> outline = lookup(".tree-view").query();
        interact(() -> outline.getSelectionModel().select(outlineItem(section).getChildren().stream()
                .filter(child -> entry.equals(String.valueOf(child.getValue())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outline node " + entry))));
    }

    private Menu newMenu() {
        MenuBar menuBar = lookup(".menu-bar").queryAs(MenuBar.class);
        Menu file = menuBar.getMenus().stream()
                .filter(menu -> "File".equals(menu.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no File menu"));
        return file.getItems().stream()
                .filter(item -> item instanceof Menu && "New".equals(item.getText()))
                .map(Menu.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no New submenu"));
    }
}
