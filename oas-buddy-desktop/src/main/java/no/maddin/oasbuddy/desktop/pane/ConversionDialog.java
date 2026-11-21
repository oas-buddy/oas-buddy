package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import no.maddin.oasbuddy.core.convert.Conflict;
import no.maddin.oasbuddy.core.document.OasVersion;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Asks before a lossy conversion, listing every conflict. It goes through the
 * {@link RemovalConfirmation} seam — "ask, show what is at stake, go ahead only on an explicit
 * yes" is the same here — so tests answer for the user. Each conflict is one line of the seam's
 * {@code details} text ({@link #describe}), and the dialog shows those lines in a scrollable list
 * ({@link #content}) under a "Convert anyway" button.
 *
 * <p>Split the way {@link AboutDialog} is: the content is built separately from showing it, so it
 * can be inspected without driving a modal window.
 */
public final class ConversionDialog {

    /** The affirmative button. */
    public static final String ACTION = "Convert anyway";
    /** The id of the conflict list. */
    public static final String LIST_ID = "conversion-conflicts";

    private static final double LIST_HEIGHT = 260;

    private ConversionDialog() {
    }

    /** The confirmation the running app uses. */
    public static RemovalConfirmation dialog() {
        return (question, details) -> {
            Alert alert = alert(question, details);
            ButtonType action = alert.getButtonTypes().getFirst();
            return alert.showAndWait().filter(action::equals).isPresent();
        };
    }

    /** The dialog itself, built separately from showing it so it can be inspected. */
    public static Alert alert(String question, String details) {
        Alert alert = RemovalConfirmation.alert(ACTION, question, "");
        alert.getDialogPane().setContent(content(details));
        return alert;
    }

    /** One wrapped, scrollable row per line of {@code details}, i.e. per conflict. */
    public static Node content(String details) {
        ListView<String> list = new ListView<>();
        list.setId(LIST_ID);
        list.getItems().setAll(details.lines().toList());
        list.setPrefHeight(LIST_HEIGHT);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setWrapText(true);
                prefWidthProperty().bind(view.widthProperty().subtract(24));
            }
        });
        return list;
    }

    /** The headline: where the document is going and how much does not come along as it is. */
    public static String question(OasVersion target, int conflicts) {
        return "Convert to " + target.label() + "? " + conflicts
                + (conflicts == 1 ? " thing" : " things") + " cannot be carried over as they are.";
    }

    /** The conflicts as the seam's details text: one line each, location first. */
    public static String describe(List<Conflict> conflicts) {
        return conflicts.stream()
                .map(conflict -> conflict.location() + ": " + conflict.problem() + " — " + conflict.resolution())
                .collect(Collectors.joining("\n"));
    }
}
