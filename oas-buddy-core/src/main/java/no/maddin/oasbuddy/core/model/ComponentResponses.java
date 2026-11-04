package no.maddin.oasbuddy.core.model;

import no.maddin.oasbuddy.core.document.LazyObjectNode;

/** The named map at {@code components.responses}: responses defined once and referenced from any number of operations. Named for where it lives, since {@link Responses} is already an operation's own status-code map. */
public final class ComponentResponses extends ComponentSection<ApiResponse> {

    /** The {@code components} field these live in, as {@link ComponentReferences} names it. */
    public static final String SECTION = "responses";

    public ComponentResponses(LazyObjectNode node) {
        super(node, ApiResponse::new);
    }

    public ApiResponse getResponse(String name) {
        return get(name);
    }

    public ApiResponse addResponse(String name) {
        return add(name);
    }

    public void removeResponse(String name) {
        remove(name);
    }
}
