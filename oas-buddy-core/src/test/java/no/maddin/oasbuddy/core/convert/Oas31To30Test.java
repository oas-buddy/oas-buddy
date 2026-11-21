package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.DocumentWriter;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Oas31To30Test {

    private final OasConverter converter = OasConverter.standard();

    @Test
    void theStandardConverterOffers30ForA31Document() {
        assertEquals(java.util.Set.of(OasVersion.V3_0), converter.targets(OasVersion.V3_1));
    }

    /** Every rule at once, compared key order included against the hand-written expected 3.0 file. */
    @Test
    void whatIsRespelledIsRespelledAndWhatIsNotIsKeptInPlace() throws IOException {
        ConversionResult result = converter.convert(fixture("everything-3.1.yaml"), OasVersion.V3_0);

        assertEquals(inOrder(fixture("everything-3.0.yaml")), inOrder(result.converted()));
    }

    @Test
    void everyLossIsReported() throws IOException {
        ConversionResult result = converter.convert(fixture("everything-3.1.yaml"), OasVersion.V3_0);

        String pet = "components → schemas → Pet → ";
        assertEquals(List.of(
                "webhooks",
                "jsonSchemaDialect",
                "info → summary",
                "info → license → identifier",
                "components → pathItems",
                "components → securitySchemes → Mutual",
                pet + "$schema",
                pet + "properties → kind → const",
                pet + "properties → kind → examples",
                pet + "properties → id → type",
                pet + "properties → nothing → type",
                pet + "properties → size → exclusiveMinimum"),
                result.conflicts().stream().map(Conflict::location).toList());
        assertEquals(new Conflict("components → securitySchemes → Mutual",
                "mutualTLS security schemes do not exist in OAS 3.0",
                "kept under components → x-oas-buddy-securitySchemes; security requirements naming it now name no scheme"),
                result.conflicts().get(5));
        assertEquals(new Conflict(pet + "properties → id → type",
                "a list of several types has no OAS 3.0 equivalent",
                "kept as x-oas-buddy-type; the schema now admits any type; use oneOf to restrict it"),
                result.conflicts().get(9));
    }

    /** The acceptance criterion: what 3.0 → 3.1 does, 3.1 → 3.0 undoes, key order included. */
    @ParameterizedTest
    @CsvSource({
            "round-trip.yaml, YAML",
            "petstore.yaml,   YAML",
            "petstore.json,   JSON",
            "reusable.yaml,   YAML",
            "reusable.json,   JSON",
            "secured.yaml,    YAML",
            "secured.json,    JSON",
            "structured.yaml, YAML",
            "structured.json, JSON",
    })
    void convertingTo31AndBackGivesTheOriginal(String name, DocumentFormat format) throws IOException {
        OasDocument original = fixture(name, format);

        OasDocument there = converter.convert(original, OasVersion.V3_1).converted();
        ConversionResult back = converter.convert(there, OasVersion.V3_0);

        assertEquals(inOrder(original), inOrder(back.converted()));
        assertEquals(format, back.converted().getFormat());
    }

    @Test
    void theRoundTripFixtureReallyExercisesEveryRule() throws IOException {
        ConversionResult there = converter.convert(fixture("round-trip.yaml"), OasVersion.V3_1);

        String text = inOrder(there.converted());
        assertTrue(text.contains("\"null\""), text);
        assertTrue(text.contains("\"exclusiveMinimum\": 0"), text);
        assertTrue(text.contains("\"x-oas-buddy-nullable\": true"), text);
        assertEquals(2, there.conflicts().size(), () -> there.conflicts().toString());
    }

    /** The other way round: only a one-element type list, which 3.0 spells as the plain type, differs. */
    @Test
    void convertingTo30AndBackRestoresEveryKeptKeyword() throws IOException {
        OasDocument original = fixture("everything-3.1.yaml");
        ObjectNode expected = original.getRoot().deepCopy();
        ((ObjectNode) expected.at("/components/schemas/Pet/properties/plain")).put("type", "string");

        OasDocument there = converter.convert(original, OasVersion.V3_0).converted();
        ConversionResult back = converter.convert(there, OasVersion.V3_1);

        assertEquals(inOrder(OasDocument.wrap(expected, DocumentFormat.YAML)), inOrder(back.converted()));
        assertTrue(back.isLossless(), () -> back.conflicts().toString());
    }

    @Test
    void aKeptNameAlreadyTakenIsNeverOverwritten() {
        OasDocument document = DocumentReader.read("""
                openapi: 3.1.0
                info: {title: t, version: '1'}
                paths: {}
                components:
                  schemas:
                    Taken:
                      const: 1
                      x-oas-buddy-const: mine
                """, DocumentFormat.YAML);

        ConversionResult result = converter.convert(document, OasVersion.V3_0);

        JsonNode taken = result.converted().getRoot().at("/components/schemas/Taken");
        assertEquals("{\"const\":1,\"x-oas-buddy-const\":\"mine\"}", taken.toString());
        assertEquals("left as it is, since x-oas-buddy-const is already taken",
                result.conflicts().getFirst().resolution());
    }

    /** JSON text keeps key order, which tree equality ignores. */
    private static String inOrder(OasDocument document) {
        return DocumentWriter.write(OasDocument.wrap(document.getRoot(), DocumentFormat.JSON));
    }

    private static OasDocument fixture(String name) throws IOException {
        return fixture(name, DocumentFormat.YAML);
    }

    private static OasDocument fixture(String name, DocumentFormat format) throws IOException {
        try (InputStream is = Oas31To30Test.class.getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return DocumentReader.read(new String(is.readAllBytes(), StandardCharsets.UTF_8), format);
        }
    }
}
