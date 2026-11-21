package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.DocumentWriter;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.validation.OasValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Swagger20To30Test {

    private final OasConverter converter = OasConverter.standard();

    @Test
    void a20DocumentCanBeImportedAs30OrStraightTo31ButNothingConvertsTo20() {
        assertEquals(Set.of(OasVersion.V3_0, OasVersion.V3_1), converter.targets(OasVersion.V2_0));
        assertFalse(converter.targets(OasVersion.V3_0).contains(OasVersion.V2_0));
    }

    /** Every rule at once, compared key order included against the reviewed expected 3.0 file. */
    @Test
    void theFullFixtureImportsAsExpected() throws IOException {
        ConversionResult result = converter.convert(fixture("swagger-2.0-full.yaml"), OasVersion.V3_0);

        assertEquals(inOrder(fixture("swagger-2.0-full-3.0.yaml")), inOrder(result.converted()));
    }

    @Test
    void whatDoesNotCarryOverIsReported() throws IOException {
        ConversionResult result = converter.convert(fixture("swagger-2.0-full.yaml"), OasVersion.V3_0);

        assertEquals(List.of(
                new Conflict("definitions → Pet → properties → tag → x-nullable",
                        "x-nullable is a vendor extension, not part of Swagger 2.0", "read as nullable"),
                new Conflict("GET /pets → parameters → [3] → collectionFormat",
                        "collectionFormat tsv has no OAS 3.0 equivalent here",
                        "kept as x-oasbuddy-collectionFormat; the default serialization applies"),
                new Conflict("PUT /pets/{petId} → parameters → other",
                        "OAS 3.0 allows one request body; this operation has several body parameters",
                        "kept in x-oasbuddy-parameters"),
                new Conflict("parameters → Caption", "OAS 3.0 cannot reuse a single form field",
                        "inlined into the request body of every operation referring to it")),
                result.conflicts());
    }

    @ParameterizedTest
    @EnumSource(value = OasVersion.class, names = {"V3_0", "V3_1"})
    void theImportedDocumentValidates(OasVersion target) throws IOException {
        ConversionResult result = converter.convert(fixture("swagger-2.0-full.yaml"), target);

        assertEquals(target, result.converted().getVersion().orElseThrow());
        var validation = new OasValidator().validate(result.converted());
        assertTrue(validation.isValid(), () -> validation.messages().toString());
    }

    @ParameterizedTest
    @CsvSource({
            "petstore-2.0.yaml, YAML",
            "petstore-2.0.json, JSON",
    })
    void thePetstoreImportsLosslessly(String name, DocumentFormat format) throws IOException {
        ConversionResult result = converter.convert(fixture(name, format), OasVersion.V3_0);
        JsonNode root = result.converted().getRoot();

        assertTrue(result.isLossless(), () -> result.conflicts().toString());
        assertEquals(format, result.converted().getFormat());
        assertEquals("https://api.example.com/v1", root.at("/servers/0/url").asText());
        assertEquals("#/components/schemas/Pet",
                root.at("/paths/~1pets/post/requestBody/content/application~1json/schema/$ref").asText());
        assertEquals("#/components/schemas/Pet",
                root.at("/paths/~1pets/get/responses/200/content/application~1json/schema/items/$ref").asText());
        assertEquals(List.of("openapi", "info", "servers", "paths", "components"),
                JsonNodes.fieldNames(root));
        var validation = new OasValidator().validate(result.converted());
        assertTrue(validation.isValid(), () -> validation.messages().toString());
    }

    @Test
    void withoutConsumesOrProducesJsonIsAssumedAndSaid() {
        ConversionResult result = converter.convert(read("""
                swagger: '2.0'
                info: {title: t, version: '1'}
                paths:
                  /a:
                    post:
                      parameters:
                        - {name: b, in: body, schema: {type: string}}
                      responses:
                        '200': {description: ok, schema: {type: string}}
                """), OasVersion.V3_0);

        JsonNode post = result.converted().getRoot().at("/paths/~1a/post");
        assertEquals("string", post.at("/requestBody/content/application~1json/schema/type").asText());
        assertEquals("string", post.at("/responses/200/content/application~1json/schema/type").asText());
        assertFalse(result.converted().getRoot().has("servers"), "no host, no base path: no servers");
        assertEquals(List.of("consumes", "produces"), result.conflicts().stream().map(Conflict::location).toList());
    }

    @Test
    void aBasePathWithoutAHostIsARelativeServerAndItsSchemesAreKeptAside() {
        ConversionResult result = converter.convert(read("""
                swagger: '2.0'
                info: {title: t, version: '1'}
                basePath: /api
                schemes: [https]
                paths: {}
                """), OasVersion.V3_0);

        JsonNode root = result.converted().getRoot();
        assertEquals("/api", root.at("/servers/0/url").asText());
        assertEquals("https", root.at("/x-oasbuddy-schemes/0").asText());
        assertEquals(List.of("schemes"), result.conflicts().stream().map(Conflict::location).toList());
    }

    @Test
    void aHostWithoutSchemesAssumesHttps() {
        ConversionResult result = converter.convert(read("""
                swagger: '2.0'
                info: {title: t, version: '1'}
                host: example.com
                paths: {}
                """), OasVersion.V3_0);

        assertEquals("https://example.com", result.converted().getRoot().at("/servers/0/url").asText());
        assertEquals("the server url assumes https", result.conflicts().getFirst().resolution());
    }

    @Test
    void aPathLevelBodyAppliesToEveryOperationOfThePath() {
        ConversionResult result = converter.convert(read("""
                swagger: '2.0'
                info: {title: t, version: '1'}
                consumes: [application/json]
                paths:
                  /a:
                    parameters:
                      - {name: b, in: body, schema: {type: string}}
                    put:
                      responses: {'200': {description: ok}}
                    post:
                      parameters:
                        - {name: own, in: body, schema: {type: integer}}
                      responses: {'200': {description: ok}}
                """), OasVersion.V3_0);

        JsonNode path = result.converted().getRoot().at("/paths/~1a");
        assertFalse(path.has("parameters"));
        assertEquals("string", path.at("/put/requestBody/content/application~1json/schema/type").asText());
        assertEquals("integer", path.at("/post/requestBody/content/application~1json/schema/type").asText(),
                "an operation's own body overrides the path's");
        assertEquals(List.of("requestBody", "responses"),
                JsonNodes.fieldNames(path.get("put")));
    }

    /** JSON text keeps key order, which tree equality ignores. */
    private static String inOrder(OasDocument document) {
        return DocumentWriter.write(OasDocument.wrap(document.getRoot(), DocumentFormat.JSON));
    }

    private static OasDocument read(String yaml) {
        return DocumentReader.read(yaml, DocumentFormat.YAML);
    }

    private static OasDocument fixture(String name) throws IOException {
        return fixture(name, DocumentFormat.YAML);
    }

    private static OasDocument fixture(String name, DocumentFormat format) throws IOException {
        try (InputStream is = Swagger20To30Test.class.getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return DocumentReader.read(new String(is.readAllBytes(), StandardCharsets.UTF_8), format);
        }
    }
}
