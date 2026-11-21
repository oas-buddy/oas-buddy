package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.OasDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentReferencesTest {

    @Test
    void rewriteAllMapsEveryReferenceAndLeavesTheOthersAlone() {
        OasDocument document = DocumentReader.read("""
                swagger: '2.0'
                paths:
                  /a:
                    get:
                      responses:
                        '200':
                          schema:
                            items: {$ref: '#/definitions/Pet'}
                        default: {$ref: '#/responses/Error'}
                definitions:
                  Pet:
                    properties:
                      owner: {$ref: 'other.yaml#/Owner'}
                """, DocumentFormat.YAML);

        int rewritten = ComponentReferences.rewriteAll(document.getRoot(),
                ref -> ref.startsWith("#/definitions/") ? ref.replace("#/definitions/", "#/components/schemas/") : null);

        JsonNode root = document.getRoot();
        assertEquals(1, rewritten);
        assertEquals("#/components/schemas/Pet", root.at("/paths/~1a/get/responses/200/schema/items/$ref").asText());
        assertEquals("#/responses/Error", root.at("/paths/~1a/get/responses/default/$ref").asText());
        assertEquals("other.yaml#/Owner", root.at("/definitions/Pet/properties/owner/$ref").asText());
    }
}
