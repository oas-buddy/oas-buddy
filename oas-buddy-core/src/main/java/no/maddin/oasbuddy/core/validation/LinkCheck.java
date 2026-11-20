package no.maddin.oasbuddy.core.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.model.Callbacks;
import no.maddin.oasbuddy.core.model.ComponentCallbacks;
import no.maddin.oasbuddy.core.model.ComponentLinks;
import no.maddin.oasbuddy.core.model.ComponentResponses;
import no.maddin.oasbuddy.core.model.HttpMethod;
import no.maddin.oasbuddy.core.model.Operation;
import no.maddin.oasbuddy.core.model.ResponseLinks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports a link that cannot work: one whose {@code operationId} names no operation in the document,
 * or one that sets both {@code operationId} and {@code operationRef}. swagger-parser checks neither.
 *
 * <p>Links are found by where OAS puts them — a response's {@code links}, {@code components.responses}
 * and {@code components.links}, reaching into callbacks' path items too — and not by a field called
 * {@code links}, since a schema may well have a property of that name.
 */
final class LinkCheck {

    private static final String SEPARATOR = " → ";

    private LinkCheck() {
    }

    /** Swagger 2.0 has neither links nor callbacks, so there is nothing to check. */
    static List<ValidationMessage> check(JsonNode root, OasVersion version) {
        List<ValidationMessage> messages = new ArrayList<>();
        if (version == OasVersion.V2_0) {
            return messages;
        }
        Set<String> ids = new HashSet<>();
        JsonNode paths = root.path(OasDocument.PATHS);
        collectIds(paths, ids);
        Walker walker = new Walker(ids, messages);

        walker.pathItems(paths, List.of(OasDocument.PATHS));
        JsonNode components = root.path(OasDocument.COMPONENTS);
        walker.links(components.path(ComponentLinks.SECTION),
                List.of(OasDocument.COMPONENTS, ComponentLinks.SECTION));
        walker.responses(components.path(ComponentResponses.SECTION),
                List.of(OasDocument.COMPONENTS, ComponentResponses.SECTION));
        walker.callbacks(components.path(ComponentCallbacks.SECTION),
                List.of(OasDocument.COMPONENTS, ComponentCallbacks.SECTION));
        return messages;
    }

    private static void collectIds(JsonNode paths, Set<String> ids) {
        if (!(paths instanceof ObjectNode)) {
            return;
        }
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            collectItemIds(path.getValue(), ids);
        }
    }

    /** Operations inside callbacks count too: a link may target one by its id. */
    private static void collectItemIds(JsonNode pathItem, Set<String> ids) {
        for (HttpMethod method : HttpMethod.values()) {
            if (pathItem.path(method.fieldName()) instanceof ObjectNode operation) {
                String id = operation.path(Operation.OPERATION_ID).asText(null);
                if (id != null) {
                    ids.add(id);
                }
                collectCallbackIds(operation.path(Callbacks.FIELD), ids);
            }
        }
    }

    private static void collectCallbackIds(JsonNode callbacks, Set<String> ids) {
        if (!(callbacks instanceof ObjectNode)) {
            return;
        }
        for (JsonNode callback : callbacks) {
            if (callback instanceof ObjectNode expressions) {
                for (JsonNode nested : expressions) {
                    collectItemIds(nested, ids);
                }
            }
        }
    }

    private record Walker(Set<String> ids, List<ValidationMessage> messages) {

        void pathItems(JsonNode pathItems, List<String> location) {
            if (pathItems instanceof ObjectNode) {
                for (Map.Entry<String, JsonNode> entry : pathItems.properties()) {
                    pathItem(entry.getValue(), with(location, entry.getKey()));
                }
            }
        }

        void pathItem(JsonNode pathItem, List<String> location) {
            for (HttpMethod method : HttpMethod.values()) {
                if (pathItem.path(method.fieldName()) instanceof ObjectNode operation) {
                    List<String> at = with(location, method.fieldName());
                    responses(operation.path(Operation.RESPONSES), with(at, Operation.RESPONSES));
                    callbacks(operation.path(Callbacks.FIELD), with(at, Callbacks.FIELD));
                }
            }
        }

        void callbacks(JsonNode callbacks, List<String> location) {
            if (callbacks instanceof ObjectNode) {
                for (Map.Entry<String, JsonNode> callback : callbacks.properties()) {
                    pathItems(callback.getValue(), with(location, callback.getKey()));
                }
            }
        }

        void responses(JsonNode responses, List<String> location) {
            if (responses instanceof ObjectNode) {
                for (Map.Entry<String, JsonNode> response : responses.properties()) {
                    links(response.getValue().path(ResponseLinks.FIELD),
                            with(with(location, response.getKey()), ResponseLinks.FIELD));
                }
            }
        }

        void links(JsonNode links, List<String> location) {
            if (!(links instanceof ObjectNode)) {
                return;
            }
            for (Map.Entry<String, JsonNode> entry : links.properties()) {
                if (entry.getValue() instanceof ObjectNode link && !link.has("$ref")) {
                    check(link, String.join(SEPARATOR, with(location, entry.getKey())));
                }
            }
        }

        private void check(ObjectNode link, String where) {
            String id = link.path(Operation.OPERATION_ID).asText(null);
            if (id != null && link.has("operationRef")) {
                error("Link sets both operationId and operationRef (at " + where + ")");
            }
            if (id != null && !ids.contains(id)) {
                error("Link targets operationId \"" + id + "\", which no operation declares (at " + where + ")");
            }
        }

        private void error(String message) {
            messages.add(new ValidationMessage(ValidationSeverity.ERROR, message));
        }

        private static List<String> with(List<String> location, String next) {
            List<String> copy = new ArrayList<>(location);
            copy.add(next);
            return copy;
        }
    }
}
