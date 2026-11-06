package no.maddin.oasbuddy.desktop.pane;

import atlantafx.base.theme.Styles;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.model.ComponentSection;

import java.util.List;
import java.util.function.Consumer;

/**
 * The list of one reusable section under {@code components} — name, a one-line summary, Remove —
 * with an add row. Everything that varies is in the {@link ComponentKind}.
 */
public final class ComponentListPane {

    private ComponentListPane() {
    }

    /**
     * @param onStructureChanged run after an entry is added, so the outline picks it up
     * @param onRemove           asked to remove the named entry; the pane only reports the request,
     *                           it never removes anything itself
     */
    public static <T> Node build(ComponentKind<T> kind, OasDocument document, Runnable onStructureChanged,
                                 Consumer<String> onRemove) {
        ComponentSection<T> section = kind.access().apply(document.getComponents());

        GridPane list = FormFields.grid();
        list.setId(kind.noun() + "s-list");
        // the name column keeps a minimum width so the summary and Remove stay next to the names
        // instead of being pushed to the far edge of a wide window
        ColumnConstraints nameColumn = FormFields.column(HPos.LEFT, Priority.NEVER);
        nameColumn.setMinWidth(220);
        list.getColumnConstraints().addAll(nameColumn,
                FormFields.column(HPos.LEFT, Priority.NEVER),
                FormFields.column(HPos.LEFT, Priority.NEVER));
        fillList(kind, section, list, onRemove);

        TextField nameField = new TextField();
        nameField.setId("new-" + kind.noun() + "-name");
        nameField.setPromptText(kind.prompt());
        Button addButton = new Button("Add " + kind.noun());
        addButton.setId(kind.addButtonId());
        addButton.getStyleClass().add(Styles.ACCENT);
        addButton.setOnAction(e -> {
            String name = nameField.getText();
            if (name != null && !name.isBlank()) {
                kind.initialise().accept(section.add(name.strip()), name.strip());
                nameField.clear();
                onStructureChanged.run();
            }
        });

        return FormFields.root(FormFields.heading(kind.plural()), list, new HBox(8, nameField, addButton));
    }

    private static <T> void fillList(ComponentKind<T> kind, ComponentSection<T> section, GridPane list,
                                     Consumer<String> onRemove) {
        List<String> names = section.names();
        if (names.isEmpty()) {
            Label empty = new Label("No reusable " + kind.noun() + "s yet.");
            empty.getStyleClass().add(Styles.TEXT_MUTED);
            list.add(empty, 0, 0, 3, 1);
            return;
        }

        list.addRow(0, FormFields.columnHeading(kind.capitalised()), FormFields.columnHeading(kind.summaryHeading()));

        int row = 1;
        for (String name : names) {
            T entry = section.get(name);
            Label summary;
            if (entry == null) {
                summary = FormFields.notAnObject();
            } else {
                summary = new Label(kind.summary().apply(entry));
                summary.getStyleClass().add(Styles.TEXT_MUTED);
            }

            Button removeButton = new Button("Remove");
            removeButton.getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED);
            removeButton.setOnAction(e -> onRemove.accept(name));

            list.addRow(row++, new Label(name), summary, removeButton);
        }
    }
}
