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

import java.util.List;
import java.util.function.Consumer;

/**
 * A list pane of plain names, each with a Remove button, and a field to add one: the paths and the
 * webhooks, which have no summary column. The panes only report an add or a removal; they never
 * change the document themselves.
 *
 * @param heading       the pane's heading: {@code Paths}
 * @param listId        the id of the list grid: {@code paths-list}
 * @param columnHeading the name column's heading: {@code Path}
 * @param emptyText     shown instead of the list when there is nothing in it
 * @param prompt        what the new-name field suggests
 * @param addLabel      the add button's text
 */
record NameList(String heading, String listId, String columnHeading, String emptyText, String prompt,
                String addLabel) {

    /**
     * @param onAdd    asked to add the (stripped, non-blank) name typed into the field
     * @param onRemove asked to remove the named entry
     */
    Node build(List<String> names, Consumer<String> onAdd, Consumer<String> onRemove) {
        GridPane list = FormFields.grid();
        list.setId(listId);
        // the name column keeps a minimum width so Remove stays next to the names
        // instead of being pushed to the far edge of a wide window
        ColumnConstraints nameColumn = FormFields.column(HPos.LEFT, Priority.NEVER);
        nameColumn.setMinWidth(260);
        list.getColumnConstraints().addAll(nameColumn, FormFields.column(HPos.LEFT, Priority.NEVER));
        fill(list, names, onRemove);

        TextField nameField = new TextField();
        nameField.setPromptText(prompt);
        Button addButton = new Button(addLabel);
        addButton.getStyleClass().add(Styles.ACCENT);
        addButton.setOnAction(e -> {
            String name = nameField.getText();
            if (name != null && !name.isBlank()) {
                nameField.clear();
                onAdd.accept(name.strip());
            }
        });

        return FormFields.root(FormFields.heading(heading), list, new HBox(8, nameField, addButton));
    }

    private void fill(GridPane list, List<String> names, Consumer<String> onRemove) {
        if (names.isEmpty()) {
            Label empty = new Label(emptyText);
            empty.getStyleClass().add(Styles.TEXT_MUTED);
            list.add(empty, 0, 0, 2, 1);
            return;
        }
        list.addRow(0, FormFields.columnHeading(columnHeading));
        int row = 1;
        for (String name : names) {
            Button removeButton = new Button("Remove");
            removeButton.getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED);
            removeButton.setOnAction(e -> onRemove.accept(name));
            list.addRow(row++, new Label(name), removeButton);
        }
    }
}
