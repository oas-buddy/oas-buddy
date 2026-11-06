package no.maddin.oasbuddy.desktop.pane;

import no.maddin.oasbuddy.core.document.OasDocument;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One response under {@code components.responses}: the same fields an inline response has. */
public final class ResponsePane {

    private ResponsePane() {
    }

    /**
     * @param confirmation     asked before an inline named example is replaced by a reference
     * @param onRemoveResponse asked to remove this response; the pane only reports the request, it
     *                         never removes anything itself
     */
    public static Node build(OasDocument document, String responseName, Supplier<List<String>> schemaNames,
                             RemovalConfirmation confirmation, Consumer<String> onRemoveResponse) {
        return ComponentItemPane.build(ComponentKinds.RESPONSES, document, responseName, onRemoveResponse, response -> {
            GridPane grid = FormFields.grid();
            TextField description = ResponseForm.descriptionField(response);
            description.setId("response-description");
            grid.addRow(0, new Label("Description"), description);

            ComponentCatalog catalog = ComponentCatalog.of(document);
            return List.of(grid,
                    FormFields.heading("Content"),
                    ContentEditor.build(response.getContent(), schemaNames, catalog, confirmation),
                    FormFields.heading("Headers"),
                    HeadersEditor.build(response.getHeaders(), catalog, confirmation),
                    FormFields.heading("Links"),
                    LinksEditor.build(response.getLinks(), catalog, confirmation));
        });
    }
}
