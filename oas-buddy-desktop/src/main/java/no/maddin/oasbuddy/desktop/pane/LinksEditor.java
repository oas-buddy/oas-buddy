package no.maddin.oasbuddy.desktop.pane;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import no.maddin.oasbuddy.core.model.Link;
import no.maddin.oasbuddy.core.model.ResponseLinks;

import java.util.List;

/** The links of a response, used by an operation's response cards and by the component response editor. */
final class LinksEditor extends ReferableList<Link> {

    private final ComponentCatalog catalog;

    private LinksEditor(ResponseLinks links, ComponentCatalog catalog, RemovalConfirmation confirmation) {
        super("link", links, catalog::linkNames, catalog::link,
                new Accessors<>(Link::isReference, Link::getReferencedLinkName, Link::getRef, Link::isEmpty),
                "Its target, parameters and description", confirmation);
        this.catalog = catalog;
    }

    static VBox build(ResponseLinks links, ComponentCatalog catalog, RemovalConfirmation confirmation) {
        return new LinksEditor(links, catalog, confirmation).build(null);
    }

    @Override
    List<Node> inline(Link link) {
        VBox form = LinkForm.build(link, catalog);
        HBox.setHgrow(form, Priority.ALWAYS);
        return List.of(form);
    }

    /** The form is tall, so the name and picker sit at its top. */
    @Override
    Pos align() {
        return Pos.TOP_LEFT;
    }
}
