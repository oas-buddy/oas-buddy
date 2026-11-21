package no.maddin.oasbuddy.desktop.pane;

import atlantafx.base.theme.Styles;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.model.Info;

import java.util.List;
import java.util.function.Consumer;

/**
 * What a Swagger 2.0 document shows instead of the editor panes. 2.0 is import-only: it is read and
 * validated, never edited or written, so the only way forward is converting it. Editing it in the 3.x
 * panes is not an option — they would offer 3.x constructs (a {@code cookie} parameter, a request
 * body) that a 2.0 file cannot hold.
 */
public final class ImportOnlyPane {

    /** The id of the note, and the id prefix of each convert button ({@code convert-to-V3_1}). */
    public static final String ID = "import-only";

    private ImportOnlyPane() {
    }

    /**
     * @param onConvert converts the document to the chosen version, or {@code null} while no
     *                  conversion is available — the buttons are then shown but disabled
     */
    public static Node build(OasDocument document, Consumer<OasVersion> onConvert) {
        String declared = document.getVersion().map(OasVersion::label).orElse("This");
        Label note = new Label(declared + " documents can be read and validated, but not edited or saved. "
                + "Convert this one to edit it; the file on disk stays as it is until the converted "
                + "document is saved.");
        note.setId(ID);
        note.setWrapText(true);

        Info info = document.getInfo();
        Label summary = new Label(describe(info.getTitle(), info.getVersion(), document.getPaths().pathNames().size()));
        summary.getStyleClass().add(Styles.TEXT_MUTED);

        HBox buttons = new HBox(8);
        for (OasVersion target : List.of(OasVersion.V3_0, OasVersion.V3_1)) {
            Button convert = new Button("Convert to " + target.label() + "…");
            convert.setId(ID + "-convert-to-" + target.name());
            convert.getStyleClass().add(Styles.ACCENT);
            convert.setDisable(onConvert == null);
            convert.setOnAction(e -> onConvert.accept(target));
            buttons.getChildren().add(convert);
        }

        return FormFields.root(FormFields.heading(declared + " (read-only)"), note, summary, buttons);
    }

    private static String describe(String title, String version, int paths) {
        String name = title == null || title.isBlank() ? "Untitled API" : title;
        String release = version == null || version.isBlank() ? "" : " " + version;
        return name + release + " — " + paths + (paths == 1 ? " path" : " paths");
    }
}
