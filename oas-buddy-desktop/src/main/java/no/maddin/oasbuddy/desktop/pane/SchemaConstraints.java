package no.maddin.oasbuddy.desktop.pane;

import atlantafx.base.theme.Styles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.model.Constraint;
import no.maddin.oasbuddy.core.model.Schema;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The validation constraints of a schema, showing only the group that belongs to its type — a
 * {@code string} never offers {@code minItems}. Which ones those are is {@link Constraint}'s to say.
 *
 * <p>Changing the type hides constraints rather than deleting them: an {@code integer} that
 * briefly becomes a {@code number} keeps its bounds. So nothing is kept invisibly, a note names
 * any constraint in the file that the current type does not use.
 *
 * <p>Like {@link SchemaDetails}, controls carry style classes ({@code constraint-<keyword>}), not
 * ids, because one pane can show several of these editors.
 */
final class SchemaConstraints {

    /** The panes edit OAS 3.0 until they are told the document's version. */
    private static final OasVersion VERSION = OasVersion.V3_0;

    static final String HIDDEN_NOTE = "schema-constraints-hidden";

    private final Schema schema;
    private final VBox box = new VBox(10);

    SchemaConstraints(Schema schema) {
        this.schema = schema;
        box.getStyleClass().add("schema-constraints");
        refresh();
    }

    Node node() {
        return box;
    }

    /** Rebuilds the fields for the schema's current type. */
    void refresh() {
        box.getChildren().clear();

        List<Constraint> constraints = Constraint.forType(schema.getType());
        if (constraints.isEmpty()) {
            box.getChildren().add(muted("No constraints for this type."));
        } else {
            GridPane grid = FormFields.grid();
            int row = 0;
            for (Constraint constraint : constraints) {
                grid.addRow(row++, new Label(constraint.label()), control(constraint));
            }
            box.getChildren().add(grid);
        }

        List<Constraint> hidden = schema.constraintsNotApplying();
        if (!hidden.isEmpty()) {
            Label note = muted("Kept in the file but not used by this type: "
                    + hidden.stream().map(Constraint::key).collect(Collectors.joining(", ")));
            note.getStyleClass().add(HIDDEN_NOTE);
            box.getChildren().add(note);
        }
    }

    private Node control(Constraint constraint) {
        JsonNode current = schema.getConstraint(constraint);
        if (constraint.kind(VERSION) == Constraint.Kind.BOOLEAN) {
            CheckBox checkBox = new CheckBox();
            checkBox.getStyleClass().add(styleClass(constraint));
            checkBox.setSelected(current != null && current.asBoolean());
            // false is the default, so it is never written; see Constraint.Kind.BOOLEAN.
            checkBox.selectedProperty().addListener((obs, oldVal, newVal) ->
                    schema.setConstraint(constraint, newVal ? BooleanNode.TRUE : null));
            return checkBox;
        }

        TextField field = new TextField(display(current));
        field.getStyleClass().add(styleClass(constraint));
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                schema.setConstraint(constraint, constraint.parse(newVal, VERSION));
                field.pseudoClassStateChanged(Styles.STATE_DANGER, false);
                field.setTooltip(null);
            } catch (IllegalArgumentException e) {
                // Not a number at all, so there is nothing to write; the last valid value stays.
                field.pseudoClassStateChanged(Styles.STATE_DANGER, true);
                field.setTooltip(new Tooltip(e.getMessage() + " — not saved"));
            }
        });
        return field;
    }

    static String styleClass(Constraint constraint) {
        return "constraint-" + constraint.key();
    }

    /** A constraint value as the user typed it: a text value without its JSON quotes. */
    private static String display(JsonNode value) {
        if (value == null) {
            return "";
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        label.setWrapText(true);
        return label;
    }
}
