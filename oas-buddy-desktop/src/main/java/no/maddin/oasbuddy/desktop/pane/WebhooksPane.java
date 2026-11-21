package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.model.PathItem;
import no.maddin.oasbuddy.core.model.Webhooks;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * OAS 3.1's {@code webhooks}: the list, and one webhook's editor. A webhook is a path item under a
 * name of the author's choosing, so its editor is {@link PathItemPane#embedded} — the same fields
 * as a path, its operations edited in place, as a callback's are.
 */
public final class WebhooksPane {

    private static final NameList LIST = new NameList(
            "Webhooks", "webhooks-list", "Webhook", "No webhooks yet.", "newPet", "Add webhook");

    private WebhooksPane() {
    }

    /**
     * @param onStructureChanged run after a webhook is added, so the outline picks it up
     * @param onRemove           asked to remove the named webhook; the pane never removes it itself
     */
    public static Node list(OasDocument document, Runnable onStructureChanged, Consumer<String> onRemove) {
        Webhooks webhooks = document.getWebhooks();
        return LIST.build(webhooks.names(), name -> {
            webhooks.add(name);
            onStructureChanged.run();
        }, onRemove);
    }

    /**
     * @param onRename   asked to rename this webhook to a new, not-yet-used name; returns whether it went ahead
     * @param onRemove   asked to remove this webhook
     * @param onChanged  run after an operation is added or removed, so the caller rebuilds this view
     */
    public static Node item(OasDocument document, String name, BiPredicate<String, String> onRename,
                            RemovalConfirmation confirmation, Runnable onRemove, Runnable onChanged) {
        Webhooks webhooks = document.getWebhooks();
        TextField nameField = FormFields.renameField(name, webhooks::names,
                "A webhook named \"%s\" already exists.", candidate -> onRename.test(name, candidate));
        nameField.setId("webhook-name");
        Node header = FormFields.headerWithRenameAndDelete("Webhook", nameField, "delete-webhook",
                "Delete webhook", onRemove);
        PathItem pathItem = webhooks.get(name);
        if (pathItem == null) {
            return FormFields.root(header, FormFields.notEditable(name, "a path item"));
        }
        ComponentCatalog components = ComponentCatalog.of(document);
        OperationEditor operations = OperationPane.editor(() -> document.getComponents().getSchemas().names(),
                TagCatalog.of(document), SecuritySchemeCatalog.of(document), components, confirmation);
        return FormFields.root(header,
                PathItemPane.embedded(pathItem, components, confirmation, operations, onChanged));
    }

    /**
     * Removes a webhook, asking first only when it has operations to take with it — an empty one
     * goes without a dialog about nothing.
     */
    public static void remove(OasDocument document, String name, RemovalConfirmation confirmation,
                              Runnable onRemoved) {
        Webhooks webhooks = document.getWebhooks();
        if (!webhooks.names().contains(name)) {
            return;
        }
        PathItem pathItem = webhooks.get(name);
        boolean hasOperations = pathItem != null && !pathItem.getOperations().isEmpty();
        if (hasOperations && !confirmation.confirm("Remove webhook \"" + name + "\"?",
                PathRemoval.describeOperations(pathItem))) {
            return;
        }
        webhooks.remove(name);
        onRemoved.run();
    }
}
