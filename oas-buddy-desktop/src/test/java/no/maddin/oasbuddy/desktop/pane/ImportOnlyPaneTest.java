package no.maddin.oasbuddy.desktop.pane;

import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The note a Swagger 2.0 document shows: what it is, and the way forward. */
class ImportOnlyPaneTest extends ApplicationTest {

    private StackPane holder;

    @Override
    public void start(Stage stage) {
        holder = new StackPane();
        stage.setScene(new Scene(holder, 800, 400));
        stage.show();
    }

    @Test
    void eachConvertButtonHandsItsTargetVersionToTheAction() {
        List<OasVersion> chosen = new ArrayList<>();
        show(swagger("info: {title: Pets, version: 1.0.0}\npaths: {}\n"), chosen::add);

        Button to30 = convertButton(OasVersion.V3_0);
        Button to31 = convertButton(OasVersion.V3_1);
        assertFalse(to30.isDisabled());
        assertFalse(to31.isDisabled());

        interact(to31::fire);
        interact(to30::fire);

        assertEquals(List.of(OasVersion.V3_1, OasVersion.V3_0), chosen);
    }

    @Test
    void withoutAnActionTheButtonsAreDisabled() {
        show(swagger("info: {title: Pets, version: 1.0.0}\npaths: {}\n"), null);

        assertTrue(convertButton(OasVersion.V3_0).isDisabled());
        assertTrue(convertButton(OasVersion.V3_1).isDisabled());
    }

    @Test
    void theSummaryNamesTheApiItsVersionAndHowManyPaths() {
        show(swagger("""
                info: {title: Pets, version: 2.1.0}
                paths:
                  /pets: {}
                  /pets/{id}: {}
                """), null);

        assertTrue(labels().contains("Pets 2.1.0 — 2 paths"), labels()::toString);
    }

    @Test
    void anUntitledApiWithoutAVersionAndOnePathStillReadsWell() {
        show(swagger("""
                info: {title: ' ', version: ''}
                paths:
                  /pets: {}
                """), null);

        assertTrue(labels().contains("Untitled API — 1 path"), labels()::toString);
    }

    private static OasDocument swagger(String rest) {
        return DocumentReader.read("swagger: '2.0'\n" + rest, DocumentFormat.YAML);
    }

    private void show(OasDocument document, Consumer<OasVersion> onConvert) {
        Node pane = ImportOnlyPane.build(document, onConvert);
        interact(() -> holder.getChildren().setAll(pane));
    }

    private Button convertButton(OasVersion target) {
        return lookup("#" + ImportOnlyPane.ID + "-convert-to-" + target.name()).queryButton();
    }

    private List<String> labels() {
        return lookup(".label").queryAll().stream().map(node -> ((Label) node).getText()).toList();
    }
}
