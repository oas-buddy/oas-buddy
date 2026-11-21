package no.maddin.oasbuddy.desktop.pane;

import atlantafx.base.theme.Styles;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.model.Schema;
import no.maddin.oasbuddy.core.model.SchemaValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The value-level and descriptive fields of a schema — enum, default, example and the four
 * boolean flags — shared by the schema pane and each property's details row. OAS 3.1's
 * {@code const} and {@code examples} are offered for a 3.1 document, and shown for any other
 * document that already carries them, so the form never hides what is in the file.
 *
 * <p>The controls are marked with style classes rather than ids: a pane can show several of these
 * editors at once, one per expanded property, and a property name must never become part of a node
 * id (see the scope-name gotcha in CLAUDE.md).
 */
final class SchemaDetails {

    static final String DEFAULT = "schema-default";
    static final String EXAMPLE = "schema-example";
    static final String CONST = "schema-const";
    static final String ENUM = "schema-enum";
    static final String ENUM_INPUT = ENUM + "-input";
    static final String EXAMPLES = "schema-examples";
    static final String EXAMPLES_INPUT = EXAMPLES + "-input";

    private static final double VALUE_LIST_HEIGHT = 120;

    private SchemaDetails() {
    }

    /**
     * @param withTitleAndDescription whether to include those two — the schema pane already shows
     *                                them in its main form, a property's details row does not
     * @param version                 decides how "Nullable" is written: the {@code nullable} flag in 3.0,
     *                                {@code "null"} in the type in 3.1
     */
    static Node build(Schema schema, boolean withTitleAndDescription, OasVersion version) {
        GridPane grid = FormFields.grid();
        int row = 0;
        if (withTitleAndDescription) {
            FormFields.textRow(grid, row++, "Title", schema::getTitle, schema::setTitle);
            FormFields.textAreaRow(grid, row++, FormFields.DESCRIPTION, schema::getDescription, schema::setDescription);
        }
        valueRow(grid, row++, "Default", DEFAULT, schema, schema::getDefault, schema::setDefault);
        valueRow(grid, row++, "Example", EXAMPLE, schema, schema::getExample, schema::setExample);
        boolean v31 = version == OasVersion.V3_1;
        if (v31 || !schema.getExamples().isEmpty()) {
            grid.addRow(row++, new Label("Examples"),
                    valueList(EXAMPLES, schema, schema::getExamples, schema::setExamples));
        }
        grid.addRow(row++, new Label("Enum"), valueList(ENUM, schema, schema::getEnum, schema::setEnum));
        if (v31 || schema.getConst() != null) {
            valueRow(grid, row++, "Const", CONST, schema, schema::getConst, schema::setConst);
        }
        grid.addRow(row, new Label("Flags"), flags(schema, version));
        return grid;
    }

    /** A value typed by the schema's type at the moment it is entered; see {@link SchemaValues}. */
    private static void valueRow(GridPane grid, int row, String label, String styleClass, Schema schema,
                                 Supplier<JsonNode> getter, Consumer<JsonNode> setter) {
        TextField field = new TextField(SchemaValues.display(getter.get()));
        field.getStyleClass().add(styleClass);
        field.setPromptText("typed as the schema's type");
        field.textProperty().addListener((obs, oldVal, newVal) ->
                setter.accept(SchemaValues.parse(newVal, schema.getType())));
        grid.addRow(row, new Label(label), field);
    }

    /**
     * An ordered list of typed values — {@code enum}, {@code examples} — with add, reorder and remove.
     * Its controls carry style classes derived from {@code styleClass}: the list itself, the input
     * ({@code -input}) and each button ({@code -add}, {@code -up}, …).
     */
    private static Node valueList(String styleClass, Schema schema, Supplier<List<JsonNode>> getter,
                                  Consumer<List<JsonNode>> setter) {
        ListView<JsonNode> values = new ListView<>();
        values.getStyleClass().add(styleClass);
        values.setPrefHeight(VALUE_LIST_HEIGHT);
        values.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(JsonNode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : SchemaValues.display(item));
            }
        });
        values.getItems().setAll(getter.get());
        Runnable write = () -> setter.accept(new ArrayList<>(values.getItems()));

        TextField input = new TextField();
        input.getStyleClass().add(styleClass + "-input");
        input.setPromptText("value");
        Runnable add = () -> {
            JsonNode value = SchemaValues.parse(input.getText(), schema.getType());
            if (value != null) {
                values.getItems().add(value);
                input.clear();
                write.run();
            }
        };
        input.setOnAction(e -> add.run());

        Button addButton = button(styleClass, "Add", add);
        Button upButton = button(styleClass, "Up", () -> move(values, -1, write));
        Button downButton = button(styleClass, "Down", () -> move(values, 1, write));
        Button removeButton = button(styleClass, "Remove", () -> {
            int index = values.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                values.getItems().remove(index);
                write.run();
            }
        });
        Button clearButton = button(styleClass, "Clear", () -> {
            values.getItems().clear();
            write.run();
        });
        removeButton.getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED);
        clearButton.getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED);

        return new VBox(8, values,
                new HBox(8, input, addButton, upButton, downButton, removeButton, clearButton));
    }

    private static void move(ListView<JsonNode> values, int offset, Runnable write) {
        int index = values.getSelectionModel().getSelectedIndex();
        int target = index + offset;
        if (index < 0 || target < 0 || target >= values.getItems().size()) {
            return;
        }
        List<JsonNode> items = values.getItems();
        items.add(target, items.remove(index));
        values.getSelectionModel().select(target);
        write.run();
    }

    private static Node flags(Schema schema, OasVersion version) {
        return new HBox(16,
                flag("Nullable", schema::isNullable, nullable -> schema.setNullable(nullable, version)),
                flag("Read only", schema::isReadOnly, schema::setReadOnly),
                flag("Write only", schema::isWriteOnly, schema::setWriteOnly),
                flag("Deprecated", schema::isDeprecated, schema::setDeprecated));
    }

    private static CheckBox flag(String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        CheckBox box = new CheckBox(label);
        box.setSelected(getter.getAsBoolean());
        box.selectedProperty().addListener((obs, oldVal, newVal) -> setter.accept(newVal));
        return box;
    }

    private static Button button(String styleClass, String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass + "-" + text.toLowerCase(Locale.ROOT));
        button.setOnAction(e -> action.run());
        return button;
    }
}
