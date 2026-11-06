package no.maddin.oasbuddy.desktop.pane;

import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.model.ComponentReferences;

import java.util.List;

/**
 * Removing a reusable component, shared by the list and the individual editor. The same bargain as
 * {@link SchemaRemoval}: the places referring to it are listed, and their references are left
 * dangling for the validation panel to report rather than rewritten.
 */
public final class ComponentRemoval {

    private ComponentRemoval() {
    }

    public static <T> void remove(ComponentKind<T> kind, OasDocument document, String name,
                                  RemovalConfirmation confirmation, Runnable onRemoved) {
        List<String> usages = ComponentReferences.find(document, kind.section(), name);
        if (!confirmation.confirm("Remove " + kind.noun() + " \"" + name + "\"?", SchemaRemoval.describeUsages(usages))) {
            return;
        }
        kind.access().apply(document.getComponents()).remove(name);
        onRemoved.run();
    }
}
