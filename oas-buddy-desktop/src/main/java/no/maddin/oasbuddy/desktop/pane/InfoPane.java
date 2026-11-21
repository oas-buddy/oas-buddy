package no.maddin.oasbuddy.desktop.pane;

import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.model.ExternalDocs;
import no.maddin.oasbuddy.core.model.Info;
import javafx.scene.Node;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * The document's {@code info}. The fields OAS 3.1 added — {@code summary}, and a license's SPDX
 * {@code identifier} — are offered for a 3.1 document only, and also shown for any other document
 * that already carries them, so the form never hides what is in the file (validation reports them).
 */
public final class InfoPane {

    private InfoPane() {
    }

    public static Node build(Info info, ExternalDocs externalDocs, OasVersion version) {
        boolean v31 = version == OasVersion.V3_1;
        GridPane general = FormFields.grid();
        int row = 0;
        FormFields.textRow(general, row++, "Title", info::getTitle, info::setTitle);
        if (v31 || info.getSummary() != null) {
            FormFields.textRow(general, row++, "Summary", info::getSummary, info::setSummary);
        }
        FormFields.textRow(general, row++, "Version", info::getVersion, info::setVersion);
        FormFields.textAreaRow(general, row++, FormFields.DESCRIPTION, info::getDescription, info::setDescription);
        FormFields.textRow(general, row, "Terms of service", info::getTermsOfService, info::setTermsOfService);

        GridPane contact = FormFields.grid();
        FormFields.textRow(contact, 0, "Name", () -> info.getContact().getName(), v -> info.getContact().setName(v));
        FormFields.textRow(contact, 1, "Url", () -> info.getContact().getUrl(), v -> info.getContact().setUrl(v));
        FormFields.textRow(contact, 2, "Email", () -> info.getContact().getEmail(), v -> info.getContact().setEmail(v));

        GridPane license = FormFields.grid();
        FormFields.textRow(license, 0, "Name", () -> info.getLicense().getName(), v -> info.getLicense().setName(v));
        FormFields.textRow(license, 1, "Url", () -> info.getLicense().getUrl(), v -> info.getLicense().setUrl(v));
        if (v31 || info.getLicense().getIdentifier() != null) {
            FormFields.textRow(license, 2, "SPDX identifier", () -> info.getLicense().getIdentifier(),
                    v -> info.getLicense().setIdentifier(v));
        }

        return FormFields.root(
                new VBox(10, FormFields.heading("General"), general),
                new TitledPane("Contact", contact),
                new TitledPane("License", license),
                ExternalDocsSection.build(externalDocs));
    }
}
