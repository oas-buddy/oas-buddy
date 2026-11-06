package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import no.maddin.oasbuddy.core.model.Callback;
import no.maddin.oasbuddy.core.model.Callbacks;

import java.util.List;

/** The callbacks of an operation, each a card holding its expressions and the operations under them. */
final class CallbacksEditor extends ReferableList<Callback> {

    private final ComponentCatalog catalog;
    private final OperationEditor operationEditor;

    private CallbacksEditor(Callbacks callbacks, ComponentCatalog catalog, RemovalConfirmation confirmation,
                            OperationEditor operationEditor) {
        super("callback", callbacks, catalog::callbackNames, catalog::callback,
                new Accessors<>(Callback::isReference, Callback::getReferencedCallbackName, Callback::getRef,
                        Callback::isEmpty),
                "Its expressions and the operations under them", confirmation);
        this.catalog = catalog;
        this.operationEditor = operationEditor;
    }

    static VBox build(Callbacks callbacks, ComponentCatalog catalog, RemovalConfirmation confirmation,
                      OperationEditor operationEditor) {
        return new CallbacksEditor(callbacks, catalog, confirmation, operationEditor).build(null);
    }

    @Override
    List<Node> inline(Callback callback) {
        return List.of();
    }

    @Override
    Node below(Callback callback) {
        return CallbackExpressions.build(callback, catalog, confirmation, operationEditor);
    }

    /** An inline callback may hold whole operations; a reference or an empty one loses nothing. */
    @Override
    boolean mayRemove(String name, Callback callback) {
        return callback == null || callback.isReference() || callback.isEmpty()
                || confirmation.confirm("Remove callback \"" + name + "\"?",
                        "Its expressions and the operations under them are defined only here.");
    }
}
