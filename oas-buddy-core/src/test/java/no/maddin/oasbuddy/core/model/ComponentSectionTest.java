package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.LazyObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The behaviour every {@code components.X} map shares, tested once on the generic class. */
class ComponentSectionTest {

    private final ObjectNode root = JsonNodeFactory.instance.objectNode();
    private final ComponentSection<Header> headers =
            new ComponentSection<>(LazyObjectNode.of(root, "components", "headers"), Header::new);

    @Test
    void readingNeverAddsComponentsOrTheSection() {
        assertTrue(headers.names().isEmpty());
        assertNull(headers.get("a"));
        headers.remove("a");
        assertFalse(root.has("components"));
    }

    @Test
    void addingCreatesTheAncestorsAndReturnsTheExistingEntryNextTime() {
        ObjectNode first = headers.add("a").node();

        assertTrue(root.get("components").has("headers"));
        assertSame(first, headers.add("a").node());
    }

    @Test
    void aNonObjectEntryIsListedButNotReturned() {
        root.putObject("components").putObject("headers").put("odd", "~").putObject("fine");

        assertEquals(List.of("odd", "fine"), headers.names());
        assertNull(headers.get("odd"));
        assertNotNull(headers.get("fine"));
    }

    @Test
    void removingTheLastEntryLeavesTheSectionInPlace() {
        headers.add("a");

        headers.remove("a");

        assertTrue(headers.names().isEmpty());
        assertTrue(root.get("components").get("headers").isEmpty());
    }
}
