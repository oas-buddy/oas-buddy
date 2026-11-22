package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;
import no.maddin.oasbuddy.core.document.FormatOptions;

import java.util.Optional;

/**
 * File → Settings…: the user's formatting preferences. The body is a {@link SettingsForm}, built apart
 * from showing it (the way {@link AboutDialog} is split) so it can be tested without a modal window.
 */
public final class SettingsDialog {

    private SettingsDialog() {
    }

    /** Shows the dialog modally; the chosen options on OK, empty on Cancel. */
    public static Optional<FormatOptions> show(Window owner, FormatOptions current) {
        SettingsForm form = new SettingsForm(current);
        Dialog<FormatOptions> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.initOwner(owner);
        dialog.getDialogPane().setContent(form.node());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? form.options() : null);
        return dialog.showAndWait();
    }
}
