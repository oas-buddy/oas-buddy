package no.maddin.oasbuddy.desktop.pane;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.model.HttpMethod;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The webhook list and one webhook's editor: a path item under a name, edited in place. */
class WebhooksPaneTest extends ApplicationTest {

    private static final String DOCUMENT = """
            openapi: 3.1.0
            info: {title: Pets, version: 1.0.0}
            paths: {}
            webhooks:
              newPet:
                post:
                  operationId: petAdded
                  responses:
                    '200': {description: ok}
              quiet: {}
              odd: ~
            """;

    private StackPane holder;
    private OasDocument document;
    private final List<String> events = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        holder = new StackPane();
        stage.setScene(new Scene(holder, 1100, 900));
        stage.show();
    }

    @Test
    void theListShowsEveryWebhookAndAddsOne() {
        document = read(DOCUMENT);
        show(WebhooksPane.list(document, () -> events.add("changed"), name -> events.add("remove " + name)));

        assertEquals(List.of("newPet", "quiet", "odd"), GridPanes.entries(list()));

        interact(() -> {
            lookup(".text-field").queryAs(TextField.class).setText("  petSold ");
            button("Add webhook").fire();
        });

        assertEquals(List.of("newPet", "quiet", "odd", "petSold"), document.getWebhooks().names());
        assertEquals(List.of("changed"), events);
    }

    @Test
    void removingFromTheListOnlyReportsTheRequest() {
        document = read(DOCUMENT);
        show(WebhooksPane.list(document, () -> events.add("changed"), name -> events.add("remove " + name)));

        interact(() -> GridPanes.buttonInRowOf(list(), "quiet").fire());

        assertEquals(List.of("remove quiet"), events);
        assertTrue(document.getWebhooks().names().contains("quiet"));
    }

    @Test
    void anEmptyListSaysSo() {
        document = read("openapi: 3.1.0\ninfo: {title: t, version: '1'}\npaths: {}\n");
        show(WebhooksPane.list(document, () -> { }, name -> { }));

        assertTrue(labels().contains("No webhooks yet."), labels()::toString);
        assertFalse(document.getRoot().has("webhooks"), "showing the list adds nothing");
    }

    @Test
    void aWebhooksOperationsAreEditedInPlaceAndOneCanBeAdded() {
        document = read(DOCUMENT);
        show(item("newPet"));

        assertTrue(lookup("." + PathItemPane.OPERATION_CLASS).tryQuery().isPresent());
        interact(() -> lookup("." + PathItemPane.ADD_OPERATION_CLASS).queryAll().stream()
                .map(Button.class::cast)
                .filter(button -> "+ GET".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .fire());

        assertTrue(document.getWebhooks().get("newPet").getOperations().containsKey(HttpMethod.GET));
        assertEquals(List.of("changed"), events);
    }

    @Test
    void renamingAndDeletingAreReported() {
        document = read(DOCUMENT);
        show(item("quiet"));

        interact(() -> {
            TextField name = lookup("#webhook-name").queryAs(TextField.class);
            name.setText("silent");
            name.fireEvent(new ActionEvent());
        });
        interact(() -> lookup("#delete-webhook").queryButton().fire());

        assertEquals(List.of("rename quiet -> silent", "remove"), events);
    }

    @Test
    void aWebhookThatIsNotAnObjectIsShownNotEdited() {
        document = read(DOCUMENT);
        String before = document.getRoot().toString();
        show(item("odd"));

        assertTrue(lookup("#not-editable").tryQuery().isPresent());
        assertEquals(before, document.getRoot().toString());
    }

    @Test
    void removingAWebhookWithOperationsAsksFirst() {
        document = read(DOCUMENT);
        List<String> asked = new ArrayList<>();

        WebhooksPane.remove(document, "newPet", (question, details) -> {
            asked.add(question + " / " + details);
            return false;
        }, () -> events.add("removed"));

        assertEquals(List.of("Remove webhook \"newPet\"? / It also removes these operations:\n  POST — petAdded"),
                asked);
        assertTrue(document.getWebhooks().names().contains("newPet"), "declining keeps it");
        assertEquals(List.of(), events);
    }

    @Test
    void anEmptyWebhookGoesWithoutAskingAndTheKeyGoesWithTheLast() {
        document = read("""
                openapi: 3.1.0
                info: {title: t, version: '1'}
                paths: {}
                webhooks:
                  quiet: {}
                """);

        WebhooksPane.remove(document, "quiet", (question, details) -> {
            throw new AssertionError("asked about nothing: " + question);
        }, () -> events.add("removed"));
        WebhooksPane.remove(document, "missing", (question, details) -> true, () -> events.add("removed"));

        assertEquals(List.of("removed"), events);
        assertEquals(List.of("openapi", "info", "paths"), JsonNodes.fieldNames(document.getRoot()));
    }

    private Node item(String name) {
        return WebhooksPane.item(document, name, (from, to) -> {
                    events.add("rename " + from + " -> " + to);
                    return true;
                }, (question, details) -> true,
                () -> events.add("remove"), () -> events.add("changed"));
    }

    private void show(Node pane) {
        interact(() -> holder.getChildren().setAll(pane));
    }

    private GridPane list() {
        return lookup("#webhooks-list").queryAs(GridPane.class);
    }

    private Button button(String text) {
        return lookup(".button").queryAll().stream()
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " button"));
    }

    private List<String> labels() {
        return lookup(".label").queryAll().stream().map(node -> ((Label) node).getText()).toList();
    }

    private static OasDocument read(String yaml) {
        return DocumentReader.read(yaml, DocumentFormat.YAML);
    }
}
