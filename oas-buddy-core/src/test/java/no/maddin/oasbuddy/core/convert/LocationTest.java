package no.maddin.oasbuddy.core.convert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationTest {

    @Test
    void anOperationIsNamedByMethodAndPath() {
        assertEquals("GET /pets", Location.render(List.of("paths", "/pets", "get")));
    }

    @Test
    void belowAnOperationTheArrowPathContinues() {
        assertEquals("POST /pets → requestBody → content",
                Location.render(List.of("paths", "/pets", "post", "requestBody", "content")));
    }

    @Test
    void aPathItemFieldThatIsNoMethodKeepsThePathFirst() {
        assertEquals("/pets", Location.render(List.of("paths", "/pets")));
        assertEquals("/pets → parameters → [0]", Location.render(List.of("paths", "/pets", "parameters", "[0]")));
    }

    @Test
    void outsidePathsItIsThePlainArrowPath() {
        assertEquals("components → schemas → Pet", Location.render(List.of("components", "schemas", "Pet")));
        assertEquals("paths", Location.render(List.of("paths")));
        assertEquals("webhooks → newPet → post", Location.render(List.of("webhooks", "newPet", "post")));
    }

    @Test
    void theRootHasAName() {
        assertEquals("document root", Location.render(List.of()));
    }

    @Test
    void conflictAtRendersItsLocation() {
        assertEquals(new Conflict("GET /pets → summary", "p", "r"),
                Conflict.at(List.of("paths", "/pets", "get", "summary"), "p", "r"));
    }
}
