package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import no.maddin.oasbuddy.core.model.Header;
import no.maddin.oasbuddy.core.model.ResponseHeaders;

import java.util.List;

/** The headers of a response, used by an operation's response cards and by the component response editor. */
final class HeadersEditor extends ReferableList<Header> {

    private HeadersEditor(ResponseHeaders headers, ComponentCatalog catalog, RemovalConfirmation confirmation) {
        super("header", headers, catalog::headerNames, catalog::header,
                new Accessors<>(Header::isReference, Header::getReferencedHeaderName, Header::getRef, Header::isEmpty),
                "Its description and schema", confirmation);
    }

    static VBox build(ResponseHeaders headers, ComponentCatalog catalog, RemovalConfirmation confirmation) {
        return new HeadersEditor(headers, catalog, confirmation).build(null);
    }

    @Override
    List<Node> inline(Header header) {
        TextField description = HeaderForm.descriptionField(header);
        HBox.setHgrow(description, Priority.ALWAYS);
        TextField type = HeaderForm.typeField(header);
        type.setPrefColumnCount(8);
        return List.of(description, type, HeaderForm.requiredBox(header));
    }
}
