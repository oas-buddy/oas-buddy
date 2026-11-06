package no.maddin.oasbuddy.desktop.pane;

import no.maddin.oasbuddy.core.model.ApiResponse;
import no.maddin.oasbuddy.core.model.Callback;
import no.maddin.oasbuddy.core.model.ComponentCallbacks;
import no.maddin.oasbuddy.core.model.ComponentExamples;
import no.maddin.oasbuddy.core.model.ComponentHeaders;
import no.maddin.oasbuddy.core.model.ComponentLinks;
import no.maddin.oasbuddy.core.model.ComponentParameters;
import no.maddin.oasbuddy.core.model.ComponentResponses;
import no.maddin.oasbuddy.core.model.Example;
import no.maddin.oasbuddy.core.model.Header;
import no.maddin.oasbuddy.core.model.Link;
import no.maddin.oasbuddy.core.model.Parameter;

/** The reusable sections that share one list pane, item frame and removal flow. */
public final class ComponentKinds {

    public static final ComponentKind<ApiResponse> RESPONSES = new ComponentKind<>(
            "response", "a", "add-response", "NotFound", "Description", ComponentResponses.SECTION,
            c -> c.getResponses(), r -> orEmpty(r.getDescription()),
            // description is the one field a response must have
            (response, key) -> response.setDescription(""));

    public static final ComponentKind<Parameter> PARAMETERS = new ComponentKind<>(
            "parameter", "a", "add-component-parameter", "Limit", "Name and location",
            ComponentParameters.SECTION, c -> c.getParameters(), p -> p.getName() + " in " + p.getIn(),
            // the key names it for references; its wire name starts out the same and can then diverge
            (parameter, key) -> {
                parameter.setName(key);
                parameter.setIn("query");
            });

    public static final ComponentKind<Example> EXAMPLES = new ComponentKind<>(
            "example", "an", "add-component-example", "Cat", "Summary", ComponentExamples.SECTION,
            c -> c.getExamples(), e -> orEmpty(e.getSummary()), (example, key) -> { });

    public static final ComponentKind<Header> HEADERS = new ComponentKind<>(
            "header", "a", "add-component-header", "X-Rate-Limit", "Description", ComponentHeaders.SECTION,
            c -> c.getHeaders(), h -> orEmpty(h.getDescription()), (header, key) -> { });

    public static final ComponentKind<Link> LINKS = new ComponentKind<>(
            "link", "a", "add-component-link", "GetPet", "Description", ComponentLinks.SECTION,
            c -> c.getLinks(), l -> orEmpty(l.getDescription()), (link, key) -> { });

    public static final ComponentKind<Callback> CALLBACKS = new ComponentKind<>(
            "callback", "a", "add-component-callback", "onEvent", "Expressions", ComponentCallbacks.SECTION,
            c -> c.getCallbacks(), cb -> cb.isReference() ? "$ref: " + cb.getRef() : String.join(", ", cb.expressions()),
            (callback, key) -> { });

    private ComponentKinds() {
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
