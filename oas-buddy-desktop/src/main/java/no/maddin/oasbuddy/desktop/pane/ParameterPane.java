package no.maddin.oasbuddy.desktop.pane;

import no.maddin.oasbuddy.core.document.OasDocument;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.function.Consumer;

/** One parameter under {@code components.parameters}: the same fields an inline parameter has. */
public final class ParameterPane {

    private ParameterPane() {
    }

    /**
     * @param confirmation      asked before an inline named example is replaced by a reference
     * @param onRemoveParameter asked to remove this parameter; the pane only reports the request, it
     *                          never removes anything itself
     */
    public static Node build(OasDocument document, String key, RemovalConfirmation confirmation,
                             Consumer<String> onRemoveParameter) {
        return ComponentItemPane.build(ComponentKinds.PARAMETERS, document, key, onRemoveParameter, parameter -> {
            GridPane grid = FormFields.grid();
            int row = 0;
            grid.addRow(row++, new Label("Name"), ParameterForm.nameField(parameter));
            ParameterSerialization serialization = new ParameterSerialization(parameter,
                    ComponentCatalog.of(document), confirmation);
            grid.addRow(row++, new Label("In"), ParameterForm.inBox(parameter, serialization::refresh));
            grid.addRow(row++, new Label(""), ParameterForm.requiredBox(parameter));
            grid.addRow(row++, new Label("Type"), ParameterForm.typeControl(parameter));
            FormFields.textAreaRow(grid, row, "Description", parameter::getDescription, parameter::setDescription);
            return List.of(grid, serialization.node());
        });
    }
}
