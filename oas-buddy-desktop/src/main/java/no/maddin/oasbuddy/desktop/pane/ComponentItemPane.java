package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Node;
import no.maddin.oasbuddy.core.document.OasDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The part every reusable component's own editor shares: a title with Delete, and — for an entry
 * that is not an object — the note instead of a form. The kind supplies the form.
 */
final class ComponentItemPane {

    private ComponentItemPane() {
    }

    /**
     * @param onRemove asked to remove this entry; the pane only reports the request
     * @param form     the nodes under the title, given the entry
     */
    static <T> Node build(ComponentKind<T> kind, OasDocument document, String name, Consumer<String> onRemove,
                          Function<T, List<Node>> form) {
        T entry = kind.access().apply(document.getComponents()).get(name);
        Node title = FormFields.headerWithDelete(kind.capitalised() + ": " + name, "delete-" + kind.noun(),
                "Delete " + kind.noun(), () -> onRemove.accept(name));
        List<Node> body = new ArrayList<>();
        body.add(title);
        if (entry == null) {
            body.add(FormFields.notEditable(name, kind.article() + " " + kind.noun()));
        } else {
            body.addAll(form.apply(entry));
        }
        return FormFields.root(body.toArray(Node[]::new));
    }
}
