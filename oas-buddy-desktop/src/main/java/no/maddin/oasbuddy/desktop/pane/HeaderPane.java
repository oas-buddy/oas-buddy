package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import no.maddin.oasbuddy.core.document.OasDocument;

import java.util.List;
import java.util.function.Consumer;

/** One header under {@code components.headers}: the same fields an inline response header has. */
public final class HeaderPane {

    private HeaderPane() {
    }

    /**
     * @param onRemoveHeader asked to remove this header; the pane only reports the request, it
     *                       never removes anything itself
     */
    public static Node build(OasDocument document, String headerName, Consumer<String> onRemoveHeader) {
        return ComponentItemPane.build(ComponentKinds.HEADERS, document, headerName, onRemoveHeader, header -> {
            GridPane grid = FormFields.grid();
            TextField description = HeaderForm.descriptionField(header);
            description.setId("header-description");
            grid.addRow(0, new Label("Description"), description);
            TextField type = HeaderForm.typeField(header);
            type.setId("header-type");
            grid.addRow(1, new Label("Type"), type);
            grid.addRow(2, new Label(""), HeaderForm.requiredBox(header));
            return List.of(grid);
        });
    }
}
