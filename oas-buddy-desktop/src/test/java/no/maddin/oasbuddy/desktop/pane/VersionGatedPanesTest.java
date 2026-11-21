package no.maddin.oasbuddy.desktop.pane;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.model.Constraint;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.service.query.NodeQuery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the Info and schema panes offer, and how they write it, follows the document's version. */
class VersionGatedPanesTest extends ApplicationTest {

    private StackPane holder;

    @Override
    public void start(Stage stage) {
        holder = new StackPane();
        stage.setScene(new Scene(holder, 1100, 1000));
        stage.show();
    }

    @Test
    void a30DocumentIsNotOfferedThe31InfoFields() {
        OasDocument document = OasDocument.newDocument(DocumentFormat.YAML, OasVersion.V3_0);

        showInfo(document);

        List<String> labels = labels();
        assertFalse(labels.contains("Summary"), labels::toString);
        assertFalse(labels.contains("SPDX identifier"), labels::toString);
    }

    @Test
    void a31DocumentEditsSummaryAndLicenseIdentifier() {
        OasDocument document = OasDocument.newDocument(DocumentFormat.YAML, OasVersion.V3_1);
        showInfo(document);

        interact(() -> fieldAfter("Summary").setText("Pets, briefly"));
        interact(() -> fieldAfter("SPDX identifier").setText("Apache-2.0"));

        assertEquals("Pets, briefly", document.getInfo().getSummary());
        assertEquals("Apache-2.0", document.getInfo().getLicense().getIdentifier());
    }

    /** The form never hides what is in the file: a 3.0 document carrying a summary shows it. */
    @Test
    void a30DocumentThatAlreadyCarriesASummaryShowsIt() {
        OasDocument document = OasDocument.newDocument(DocumentFormat.YAML, OasVersion.V3_0);
        document.getInfo().setSummary("Invalid in 3.0");

        showInfo(document);

        assertEquals("Invalid in 3.0", fieldAfter("Summary").getText());
    }

    @Test
    void nullableIsWrittenAsTheFlagIn30() {
        OasDocument document = withStringSchema(OasVersion.V3_0);
        showSchema(document);

        interact(() -> flag("Nullable").setSelected(true));

        JsonNode code = raw(document);
        assertTrue(code.get("nullable").asBoolean());
        assertEquals("string", code.get("type").asText());
    }

    @Test
    void nullableIsWrittenIntoTheTypeIn31() {
        OasDocument document = withStringSchema(OasVersion.V3_1);
        showSchema(document);

        interact(() -> flag("Nullable").setSelected(true));

        JsonNode code = raw(document);
        assertFalse(code.has("nullable"));
        assertEquals("[\"string\",\"null\"]", code.get("type").toString());
    }

    @Test
    void anExclusiveBoundIsAFlagIn30() {
        OasDocument document = withNumberSchema(OasVersion.V3_0);
        showSchema(document);

        interact(() -> constraint(Constraint.EXCLUSIVE_MINIMUM).queryAs(CheckBox.class).setSelected(true));

        assertTrue(raw(document).get("exclusiveMinimum").isBoolean());
    }

    @Test
    void anExclusiveBoundIsANumberIn31() {
        OasDocument document = withNumberSchema(OasVersion.V3_1);
        showSchema(document);

        interact(() -> constraint(Constraint.EXCLUSIVE_MINIMUM).queryAs(TextField.class).setText("0"));

        JsonNode bound = raw(document).get("exclusiveMinimum");
        assertTrue(bound.isNumber(), bound::toString);
        assertEquals(0, bound.intValue());
    }

    @Test
    void a30SchemaIsNotOfferedConstOrExamples() {
        showSchema(withNumberSchema(OasVersion.V3_0));

        List<String> labels = valueLabels();
        assertFalse(labels.contains("Const"), labels::toString);
        assertFalse(labels.contains("Examples"), labels::toString);
    }

    @Test
    void a31SchemaEditsConstAsATypedValue() {
        OasDocument document = withNumberSchema(OasVersion.V3_1);
        showSchema(document);
        TextField constant = values().lookup("." + SchemaDetails.CONST).queryAs(TextField.class);

        interact(() -> constant.setText("42"));
        assertEquals(42, raw(document).get("const").intValue());

        interact(() -> constant.setText(""));
        assertFalse(raw(document).has("const"), "clearing the field removes the key");
    }

    @Test
    void a31SchemaEditsExamplesAsAnOrderedList() {
        OasDocument document = withNumberSchema(OasVersion.V3_1);
        showSchema(document);

        addExample("1.5");
        addExample("2");
        interact(() -> {
            values().lookup("." + SchemaDetails.EXAMPLES).queryListView().getSelectionModel().select(1);
            values().lookup("." + SchemaDetails.EXAMPLES + "-up").queryButton().fire();
        });

        assertEquals("[2,1.5]", raw(document).get("examples").toString());

        interact(() -> values().lookup("." + SchemaDetails.EXAMPLES + "-clear").queryButton().fire());
        assertFalse(raw(document).has("examples"), "an empty list removes the key");
    }

    /** The form never hides what is in the file: a 3.0 schema carrying const or examples shows them. */
    @Test
    void a30SchemaThatAlreadyCarriesConstAndExamplesShowsThem() {
        OasDocument document = withNumberSchema(OasVersion.V3_0);
        var code = document.getComponents().getSchemas().getSchema("Code");
        code.setConst(IntNode.valueOf(7));
        code.setExamples(List.of(IntNode.valueOf(1)));

        showSchema(document);

        assertEquals("7", values().lookup("." + SchemaDetails.CONST).queryAs(TextField.class).getText());
        assertEquals(1, values().lookup("." + SchemaDetails.EXAMPLES).queryListView().getItems().size());
    }

    private void addExample(String text) {
        interact(() -> {
            values().lookup("." + SchemaDetails.EXAMPLES_INPUT).queryAs(TextField.class).setText(text);
            values().lookup("." + SchemaDetails.EXAMPLES + "-add").queryButton().fire();
        });
    }

    private NodeQuery values() {
        return from(lookup("#schema-values").<Node>query());
    }

    private List<String> valueLabels() {
        return values().lookup(".label").queryAll().stream().map(node -> ((Label) node).getText()).toList();
    }

    private static OasDocument withStringSchema(OasVersion version) {
        OasDocument document = OasDocument.newDocument(DocumentFormat.YAML, version);
        document.getComponents().getSchemas().addSchema("Code").changeTypeTo("string");
        return document;
    }

    private static OasDocument withNumberSchema(OasVersion version) {
        OasDocument document = OasDocument.newDocument(DocumentFormat.YAML, version);
        document.getComponents().getSchemas().addSchema("Code").changeTypeTo("number");
        return document;
    }

    private void showInfo(OasDocument document) {
        Node pane = InfoPane.build(document.getInfo(), document.getExternalDocs(),
                document.getVersion().orElseThrow());
        interact(() -> holder.getChildren().setAll(pane));
    }

    private void showSchema(OasDocument document) {
        Node pane = SchemaPane.build(document, "Code", (from, to) -> true, name -> { });
        interact(() -> holder.getChildren().setAll(pane));
    }

    private List<String> labels() {
        return lookup(".label").queryAll().stream().map(node -> ((Label) node).getText()).toList();
    }

    private TextField fieldAfter(String labelText) {
        Label label = lookup(".label").queryAll().stream()
                .map(Label.class::cast)
                .filter(candidate -> labelText.equals(candidate.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + labelText + " row"));
        return (TextField) GridPanes.cellInRowOf((GridPane) label.getParent(), labelText, 1);
    }

    private CheckBox flag(String text) {
        return from(lookup("#schema-values").<Node>query()).lookup(".check-box").queryAll().stream()
                .map(CheckBox.class::cast)
                .filter(box -> text.equals(box.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + text + " flag"));
    }

    private NodeQuery constraint(Constraint constraint) {
        return from(lookup("#schema-constraints").<Node>query()).lookup("." + SchemaConstraints.styleClass(constraint));
    }

    private static JsonNode raw(OasDocument document) {
        return document.getRoot().at("/components/schemas/Code");
    }
}
