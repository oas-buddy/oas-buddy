package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Node;
import no.maddin.oasbuddy.core.document.OasDocument;

import java.util.List;
import java.util.function.Consumer;

/** One link under {@code components.links}: the same fields an inline response link has. */
public final class LinkPane {

    private LinkPane() {
    }

    /**
     * @param onRemoveLink asked to remove this link; the pane only reports the request, it
     *                       never removes anything itself
     */
    public static Node build(OasDocument document, String linkName, Consumer<String> onRemoveLink) {
        return ComponentItemPane.build(ComponentKinds.LINKS, document, linkName, onRemoveLink,
                link -> List.of(LinkForm.build(link, ComponentCatalog.of(document))));
    }
}
