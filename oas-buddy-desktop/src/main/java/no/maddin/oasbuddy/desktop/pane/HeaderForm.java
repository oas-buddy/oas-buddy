package no.maddin.oasbuddy.desktop.pane;

import static no.maddin.oasbuddy.desktop.pane.FormFields.nullToEmpty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import no.maddin.oasbuddy.core.model.Header;
import no.maddin.oasbuddy.core.model.Schema;

/**
 * The fields of an inline header, shared by a response's header rows and the component header
 * editor, the way {@link ParameterForm} is for parameters. None carries an id: several are on
 * screen at once.
 */
final class HeaderForm {

    private HeaderForm() {
    }

    static TextField descriptionField(Header header) {
        return FormFields.boundTextField(header.getDescription(), "description", null, header::setDescription);
    }

    static CheckBox requiredBox(Header header) {
        CheckBox box = new CheckBox("Required");
        box.setSelected(Boolean.TRUE.equals(header.isRequired()));
        box.selectedProperty().addListener((obs, oldVal, newVal) -> header.setRequired(newVal ? Boolean.TRUE : null));
        return box;
    }

    /** As {@link ParameterForm#typeField}: a plain setter per keystroke, and showing it adds no {@code schema}. */
    static TextField typeField(Header header) {
        Schema schema = header.findSchema();
        TextField field = new TextField(schema == null ? "" : nullToEmpty(schema.getType()));
        field.setPromptText("type");
        field.textProperty().addListener((obs, oldVal, newVal) -> header.getSchema().setType(newVal));
        return field;
    }
}
