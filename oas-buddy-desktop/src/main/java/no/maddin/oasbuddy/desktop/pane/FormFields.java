package no.maddin.oasbuddy.desktop.pane;

import atlantafx.base.theme.Styles;
import javafx.beans.property.Property;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

final class FormFields {

    /** The label every editor gives an OAS {@code description} field. */
    static final String DESCRIPTION = "Description";

    private FormFields() {
    }

    /**
     * Applies each change the user makes to {@code property}; when {@code apply} (given the old and
     * the new value) returns {@code false} — a declined confirmation — the old value is put back
     * without the change being applied again. Reverting would otherwise re-enter the listener.
     */
    static <T> void guarded(Property<T> property, BiPredicate<T, T> apply) {
        boolean[] reverting = {false};
        property.addListener((obs, was, now) -> {
            if (reverting[0] || now == null || now.equals(was)) {
                return;
            }
            if (!apply.test(was, now)) {
                reverting[0] = true;
                property.setValue(was);
                reverting[0] = false;
            }
        });
    }

    /** A top-level pane body: consistent outer spacing/padding for every editor pane. */
    static VBox root(Node... sections) {
        VBox box = new VBox(18, sections);
        box.setPadding(new Insets(16));
        return box;
    }

    /** A form grid with the standard column/row gaps used across every editor pane. */
    static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        return grid;
    }

    /** A grid column with a fixed horizontal alignment and grow behaviour. */
    static ColumnConstraints column(HPos alignment, Priority hgrow) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setHalignment(alignment);
        constraints.setHgrow(hgrow);
        return constraints;
    }

    /** A muted column title for the list grids. */
    static Label columnHeading(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        return label;
    }

    /** A pane title with the control that deletes what the pane is editing. */
    static Node headerWithDelete(String title, String buttonId, String buttonText, Runnable onDelete) {
        return headerWithRenameAndDelete(title, null, buttonId, buttonText, onDelete);
    }

    /** As {@link #headerWithDelete}, with a rename control between the title and the delete button. */
    static Node headerWithRenameAndDelete(String title, Node renameField, String buttonId, String buttonText,
                                          Runnable onDelete) {
        Button deleteButton = new Button(buttonText);
        deleteButton.setId(buttonId);
        deleteButton.getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED);
        deleteButton.setOnAction(e -> onDelete.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = renameField == null
                ? new HBox(8, heading(title), spacer, deleteButton)
                : new HBox(8, heading(title), renameField, spacer, deleteButton);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /**
     * What a component editor shows instead of a form when the entry under {@code name} is not an
     * object at all — {@code NotFound: ~} or a string in a hand-edited file. The outline lists every
     * key, so such an entry is reachable, and it must neither crash the pane nor be rewritten into
     * something editable: it is left exactly as loaded, the same bargain a security scheme of an
     * unsupported type gets. The header above it still offers Delete.
     */
    static Label notEditable(String name, String kind) {
        Label label = new Label("\"" + name + "\" is not " + kind + " object, so it cannot be edited here. "
                + "It is left exactly as it was loaded, and is saved unchanged.");
        label.setId("not-editable");
        label.getStyleClass().add(Styles.TEXT_MUTED);
        label.setWrapText(true);
        return label;
    }

    /** The second-column text a list pane shows for an entry that is not an object; see {@link #notEditable}. */
    static Label notAnObject() {
        Label label = new Label("(not an object)");
        label.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.WARNING);
        return label;
    }

    static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(Styles.TITLE_4);
        return label;
    }

    static TextField textRow(GridPane grid, int row, String label, Supplier<String> getter, Consumer<String> setter) {
        TextField field = boundTextField(getter.get(), null, null, setter);
        grid.addRow(row, new Label(label), field);
        return field;
    }

    static TextArea textAreaRow(GridPane grid, int row, String label, Supplier<String> getter, Consumer<String> setter) {
        TextArea area = new TextArea(nullToEmpty(getter.get()));
        area.setPrefRowCount(3);
        area.setWrapText(true);
        area.textProperty().addListener((obs, oldVal, newVal) ->
                setter.accept(newVal == null || newVal.isBlank() ? null : newVal));
        grid.addRow(row, new Label(label), area);
        return area;
    }

    static CheckBox checkBoxRow(GridPane grid, int row, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        CheckBox box = new CheckBox();
        box.setSelected(getter.getAsBoolean());
        box.selectedProperty().addListener((obs, oldVal, newVal) -> setter.accept(newVal));
        grid.addRow(row, new Label(label), box);
        return box;
    }

    /**
     * A text field for renaming something, committing on Enter or focus loss rather than per
     * keystroke — the same reason {@code OAuthFlow.renameScope} commits a scope rename as a whole
     * rather than rebuilding the underlying map on every character, which would fight the caret.
     *
     * <p>{@code attempt} performs the rename and returns {@code null} on success. Any other value
     * reverts the field to {@code initialName}; a non-blank value is also shown as an error, the
     * same red-field-plus-tooltip treatment {@code SchemaConstraints} uses for an invalid value.
     * A blank value reverts quietly — for a rename declined through its own confirmation dialog,
     * which already explained itself.
     */
    static TextField renameField(String initialName, UnaryOperator<String> attempt) {
        TextField field = new TextField(initialName);
        Runnable commit = () -> {
            String candidate = field.getText() == null ? "" : field.getText().strip();
            if (candidate.equals(initialName)) {
                field.setText(initialName);
                return;
            }
            String error = candidate.isEmpty() ? "Name cannot be blank." : attempt.apply(candidate);
            if (error == null) {
                field.pseudoClassStateChanged(Styles.STATE_DANGER, false);
                field.setTooltip(null);
            } else {
                field.setText(initialName);
                field.pseudoClassStateChanged(Styles.STATE_DANGER, !error.isBlank());
                field.setTooltip(error.isBlank() ? null : new Tooltip(error));
            }
        };
        field.setOnAction(e -> commit.run());
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commit.run();
            }
        });
        return field;
    }

    /**
     * The usual {@link #renameField(String, UnaryOperator)}: a name in {@code taken} is refused with
     * {@code collisionFormat} (one {@code %s} for the candidate) before {@code rename} runs at all,
     * and a rename that {@code rename} declines reverts quietly. {@code taken} is read at commit
     * time, so it sees renames made since the field was built.
     */
    static TextField renameField(String initialName, Supplier<? extends Collection<String>> taken,
                                 String collisionFormat, Predicate<String> rename) {
        return renameField(initialName, candidate -> {
            if (taken.get().contains(candidate)) {
                return collisionFormat.formatted(candidate);
            }
            return rename.test(candidate) ? null : "";
        });
    }

    /**
     * A comma-separated list as typed by the user: each entry stripped, empty entries dropped.
     * Splits on the bare comma and strips afterwards rather than splitting on a pattern with
     * {@code \\s*} on both sides, whose two quantifiers backtrack against each other on a long
     * run of spaces.
     */
    static List<String> commaSeparated(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(",")).map(String::strip).filter(v -> !v.isEmpty()).toList();
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * A text field showing {@code initial}, whose every edit goes to {@code setter} — blank
     * becoming {@code null}, so clearing a field removes the key instead of writing {@code ""}.
     *
     * @param prompt     the prompt text, or {@code null} for none
     * @param styleClass a class for tests to find it by, or {@code null} for none
     */
    static TextField boundTextField(String initial, String prompt, String styleClass, Consumer<String> setter) {
        TextField field = new TextField(nullToEmpty(initial));
        if (prompt != null) {
            field.setPromptText(prompt);
        }
        if (styleClass != null) {
            field.getStyleClass().add(styleClass);
        }
        field.textProperty().addListener((obs, was, now) -> setter.accept(now == null || now.isBlank() ? null : now));
        return field;
    }
}
