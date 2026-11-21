package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.DocumentWriter;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import no.maddin.oasbuddy.core.validation.OasValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Oas30To31Test {

    private final OasConverter converter = OasConverter.standard();

    @Test
    void theStandardConverterOffers31ForA30Document() {
        assertTrue(converter.targets(OasVersion.V3_0).contains(OasVersion.V3_1));
    }

    /** Every rule at once, compared key order included against the hand-written expected 3.1 file. */
    @Test
    void nullableAndExclusiveBoundsAreRespelledInPlace() throws IOException {
        ConversionResult result = converter.convert(fixture("nullable-bounds.yaml"), OasVersion.V3_1);

        assertEquals(inOrder(fixture("nullable-bounds-3.1.yaml")), inOrder(result.converted()));
    }

    @Test
    void onlyFlagsWithNoEffectIn30AreSetAsideAndReported() throws IOException {
        ConversionResult result = converter.convert(fixture("nullable-bounds.yaml"), OasVersion.V3_1);

        assertEquals(List.of(
                "components → schemas → Pet → properties → weight → exclusiveMaximum",
                "components → schemas → Pet → properties → owner → nullable"),
                result.conflicts().stream().map(Conflict::location).toList());
        assertTrue(result.conflicts().getFirst().resolution().startsWith("kept as x-oas-buddy-exclusiveMaximum"),
                result.conflicts().getFirst().resolution());
    }

    /** The named fixtures use neither rule, so the version is all that changes — and nothing is lost. */
    @ParameterizedTest
    @CsvSource({
            "petstore.yaml,   YAML",
            "petstore.json,   JSON",
            "reusable.yaml,   YAML",
            "reusable.json,   JSON",
            "secured.yaml,    YAML",
            "secured.json,    JSON",
            "structured.yaml, YAML",
            "structured.json, JSON",
    })
    void theFixturesConvertLosslesslyAndStayValid(String name, DocumentFormat format) throws IOException {
        OasDocument original = fixture(name, format);
        JsonNode before = original.getRoot().deepCopy();

        ConversionResult result = converter.convert(original, OasVersion.V3_1);

        assertTrue(result.isLossless(), () -> result.conflicts().toString());
        assertEquals(before, original.getRoot(), "the original must be untouched");
        assertEquals(format, result.converted().getFormat());
        assertEquals("3.1.0", result.converted().getOpenApiVersion());

        ObjectNode expected = before.deepCopy();
        expected.put("openapi", "3.1.0");
        assertEquals(inOrder(OasDocument.wrap(expected, format)), inOrder(result.converted()));

        var validation = new OasValidator().validate(result.converted());
        assertTrue(validation.isValid(), () -> validation.messages().toString());
    }

    @Test
    void aTypeListAndAnAlreadyNumericBoundAreLeftAsTheyAre() {
        OasDocument document = DocumentReader.read("""
                openapi: 3.0.3
                info: {title: t, version: '1'}
                paths: {}
                components:
                  schemas:
                    Listed:
                      type: [string, integer]
                      nullable: true
                    AlreadyNullable:
                      type: [string, 'null']
                      nullable: true
                    Numeric:
                      type: number
                      exclusiveMinimum: 5
                """, DocumentFormat.YAML);

        ConversionResult result = converter.convert(document, OasVersion.V3_1);

        JsonNode schemas = result.converted().getRoot().at("/components/schemas");
        assertEquals("[\"string\",\"integer\",\"null\"]", schemas.at("/Listed/type").toString());
        assertEquals("[\"string\",\"null\"]", schemas.at("/AlreadyNullable/type").toString());
        assertEquals("{\"type\":\"number\",\"exclusiveMinimum\":5}", schemas.get("Numeric").toString());
        assertTrue(result.isLossless());
    }

    /** JSON text keeps key order, which tree equality ignores. */
    private static String inOrder(OasDocument document) {
        return DocumentWriter.write(OasDocument.wrap(document.getRoot(), DocumentFormat.JSON));
    }

    private static OasDocument fixture(String name) throws IOException {
        return fixture(name, DocumentFormat.YAML);
    }

    private static OasDocument fixture(String name, DocumentFormat format) throws IOException {
        try (InputStream is = Oas30To31Test.class.getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return DocumentReader.read(new String(is.readAllBytes(), StandardCharsets.UTF_8), format);
        }
    }
}
