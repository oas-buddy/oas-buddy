package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.DocumentWriter;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.OasDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The facade additions that keep the form from disagreeing with a 3.1 file. */
class Oas31FacadeTest {

    private static final String DOC_3_1 = """
            openapi: 3.1.0
            info:
              title: Pets
              summary: Pets, in 3.1
              version: 1.0.0
              license:
                name: Apache 2.0
                identifier: Apache-2.0
            paths: {}
            webhooks:
              newPet:
                post:
                  summary: A pet was added
                  responses:
                    '200':
                      description: ok
              odd: ~
            components:
              schemas:
                Kind:
                  const: dog
                  examples:
                    - dog
                    - 42
            """;

    private static final String BARE = """
            openapi: 3.1.0
            info:
              title: Pets
              version: 1.0.0
            paths: {}
            components:
              schemas:
                Pet:
                  type: object
            """;

    @Test
    void theNewFieldsAreRead() {
        OasDocument document = read(DOC_3_1);
        Info info = document.getInfo();
        Schema kind = document.getComponents().getSchemas().getSchema("Kind");

        assertEquals("Pets, in 3.1", info.getSummary());
        assertEquals("Apache-2.0", info.getLicense().getIdentifier());
        assertEquals(new TextNode("dog"), kind.getConst());
        assertEquals(List.of(new TextNode("dog"), new IntNode(42)), kind.getExamples());
        assertEquals(List.of("newPet", "odd"), document.getWebhooks().names());
        assertEquals("A pet was added",
                document.getWebhooks().get("newPet").getOperations().get(HttpMethod.POST).getSummary());
        assertNull(document.getWebhooks().get("odd"), "a non-object entry is listed but not wrapped");
    }

    /** Asking for every section — what the outline and the Info pane do on open — changes nothing. */
    @Test
    void readingNeverChangesTheDocument() {
        for (String yaml : List.of(DOC_3_1, BARE)) {
            OasDocument document = read(yaml);
            String before = DocumentWriter.write(document);
            JsonNode tree = document.getRoot().deepCopy();

            Info info = document.getInfo();
            info.getSummary();
            info.getContact().getName();
            info.getContact().getUrl();
            info.getContact().getEmail();
            info.getLicense().getName();
            info.getLicense().getUrl();
            info.getLicense().getIdentifier();
            Webhooks webhooks = document.getWebhooks();
            webhooks.names().forEach(webhooks::get);
            webhooks.get("missing");
            Schemas schemas = document.getComponents().getSchemas();
            for (String name : schemas.names()) {
                schemas.getSchema(name).getConst();
                schemas.getSchema(name).getExamples();
            }

            assertEquals(tree, document.getRoot());
            assertEquals(before, DocumentWriter.write(document));
        }
    }

    @Test
    void writingContactOrLicenseAddsItAndClearingItTakesItOutAgain() {
        OasDocument document = read(BARE);
        License license = document.getInfo().getLicense();

        license.setIdentifier("MIT");
        license.setName("MIT");
        assertEquals("MIT", ((ObjectNode) document.getRoot().get("info")).get("license").get("identifier").asText());

        license.setIdentifier(null);
        license.setName("");
        assertFalse(document.getRoot().get("info").has("license"));

        Contact contact = document.getInfo().getContact();
        contact.setEmail("pets@example.com");
        contact.setEmail(null);
        assertFalse(document.getRoot().get("info").has("contact"));
    }

    @Test
    void aContactThatIsNotAnObjectIsNeitherReadNorRewritten() {
        OasDocument document = read(BARE.replace("version: 1.0.0", "version: 1.0.0\n  contact: support"));
        Contact contact = document.getInfo().getContact();

        assertFalse(contact.isEditable());
        assertNull(contact.getName());
        contact.setName("Support");
        assertEquals(new TextNode("support"), document.getRoot().get("info").get("contact"));
    }

    @Test
    void summaryIsWrittenAndRemoved() {
        Info info = read(BARE).getInfo();

        info.setSummary("Short");
        assertEquals("Short", info.getSummary());
        info.setSummary(null);
        assertNull(info.getSummary());
    }

    @Test
    void constAndExamplesAreWrittenAndRemoved() {
        OasDocument document = read(BARE);
        Schema pet = document.getComponents().getSchemas().getSchema("Pet");
        ObjectNode node = (ObjectNode) document.getRoot().at("/components/schemas/Pet");

        pet.setConst(NullNode.instance);
        assertEquals(NullNode.instance, pet.getConst(), "a JSON null is a legitimate constant");
        pet.setConst(null);
        assertFalse(node.has("const"));

        pet.setExamples(List.of(new IntNode(1), new TextNode("two")));
        assertEquals(List.of("type", "examples"), JsonNodes.fieldNames(node));
        pet.setExamples(List.of());
        assertFalse(node.has("examples"), "an empty list removes the key rather than leaving []");
    }

    @Test
    void webhooksAreAddedRenamedInPlaceAndTheKeyGoesWithTheLast() {
        OasDocument document = read(BARE);
        Webhooks webhooks = document.getWebhooks();

        webhooks.add("first").addOperation(HttpMethod.POST);
        webhooks.add("second");
        webhooks.add("third");
        assertTrue(webhooks.rename("second", "middle"));
        assertFalse(webhooks.rename("first", "third"), "a collision is refused");
        assertEquals(List.of("first", "middle", "third"), webhooks.names());
        assertEquals(List.of("openapi", "info", "paths", "components", "webhooks"),
                JsonNodes.fieldNames(document.getRoot()));

        webhooks.names().forEach(webhooks::remove);
        assertFalse(document.getRoot().has("webhooks"));
    }

    @ParameterizedTest
    @EnumSource(DocumentFormat.class)
    void theNewFieldsSurviveARoundTrip(DocumentFormat format) {
        OasDocument document = read(BARE);
        document.setFormat(format);
        document.getInfo().setSummary("Short");
        document.getInfo().getLicense().setIdentifier("MIT");
        document.getComponents().getSchemas().getSchema("Pet").setConst(new IntNode(3));

        OasDocument reloaded = DocumentReader.read(DocumentWriter.write(document), format);

        assertEquals(document.getRoot(), reloaded.getRoot());
    }

    private static OasDocument read(String yaml) {
        return DocumentReader.read(yaml, DocumentFormat.YAML);
    }
}
