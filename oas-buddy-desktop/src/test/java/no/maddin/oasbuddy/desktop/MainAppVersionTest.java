package no.maddin.oasbuddy.desktop;

import no.maddin.oasbuddy.core.document.OasVersion;
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
