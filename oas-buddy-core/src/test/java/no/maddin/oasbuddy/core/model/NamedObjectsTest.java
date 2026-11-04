package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The behaviour every named map under a parent shares, tested once on the generic classes. */
class NamedObjectsTest {

    private final ObjectNode parent = JsonNodeFactory.instance.objectNode();
    private final ReferableObjects<Header> headers =
            new ReferableObjects<>(parent, "headers", ComponentHeaders.SECTION, Header::new, Header::node);

    @Test
    void readingNeverAddsTheKey() {
        assertTrue(headers.names().isEmpty());
        assertNull(headers.get("a"));
        assertFalse(headers.rename("a", "b"));
        headers.remove("a");
        assertFalse(parent.has("headers"));
    }

    @Test
    void aNonObjectEntryIsListedButNotReturned() {
        parent.putObject("headers").put("odd", "~").putObject("fine");

        assertEquals(List.of("odd", "fine"), headers.names());
        assertNull(headers.get("odd"));
        assertTrue(headers.get("fine") != null);
    }

    @Test
    void aMapThatIsNotAnObjectReadsAsEmptyAndIsLeftAlone() {
        parent.put("headers", "~");

        assertTrue(headers.names().isEmpty());
        assertNull(headers.get("a"));
        headers.remove("a");
        assertEquals("~", parent.get("headers").asText());
    }

    @Test
    void addReturnsTheExistingEntryAndTheKeyGoesWithTheLastOne() {
        ObjectNode first = headers.add("a").node();

        assertSame(first, headers.add("a").node());
        headers.add("b");
        headers.remove("a");
        assertTrue(parent.has("headers"));
        headers.remove("b");
        assertFalse(parent.has("headers"));
    }

    @Test
    void renameKeepsPositionAndRefusesACollision() {
        headers.add("a");
        headers.add("b");
        headers.add("c");

        assertTrue(headers.rename("b", "z"));
        assertEquals(List.of("a", "z", "c"), headers.names());
        assertFalse(headers.rename("a", "c"));
        assertEquals(List.of("a", "z", "c"), headers.names());
    }

    @Test
    void referToAndDefineInlineReplaceInPlaceWithoutReordering() {
        headers.add("a").setDescription("kept");
        headers.add("b");
        headers.add("c");

        headers.referTo("b", "Shared");
        assertEquals(List.of("a", "b", "c"), headers.names());
        assertEquals("#/components/headers/Shared", parent.get("headers").get("b").get("$ref").asText());

        Header template = new Header(JsonNodeFactory.instance.objectNode());
        template.setDescription("from component");
        Header inline = headers.defineInline("b", template);
        assertEquals(List.of("a", "b", "c"), headers.names());
        assertEquals("from component", inline.getDescription());
        assertFalse(parent.get("headers").get("b").has("$ref"));
        assertNotSame(template.node(), inline.node());

        assertTrue(headers.defineInline("c", null).isEmpty());
    }
}
