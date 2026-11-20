package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OasVersionTest {

    @ParameterizedTest
    @CsvSource({
            "petstore.yaml,     YAML, V3_0",
            "petstore.json,     JSON, V3_0",
            "petstore-3.1.yaml, YAML, V3_1",
            "petstore-3.1.json, JSON, V3_1",
            "petstore-2.0.yaml, YAML, V2_0",
            "petstore-2.0.json, JSON, V2_0",
    })
    void eachFixtureIsDetectedAsItsVersion(String fixture, DocumentFormat format, OasVersion expected)
            throws IOException {
        assertEquals(Optional.of(expected), loadFixture(fixture, format).getVersion());
    }

    @ParameterizedTest
    @CsvSource({
            "openapi: 3.0.0,      V3_0",
            "openapi: 3.0.3,      V3_0",
            "openapi: 3.0.4,      V3_0",
            "openapi: 3.1.0,      V3_1",
            "openapi: 3.1.1,      V3_1",
            "swagger: '2.0',      V2_0",
    })
    void theVersionKeyDecides(String yaml, OasVersion expected) {
        assertEquals(Optional.of(expected), detect(yaml));
    }

    /** Hand-written YAML often leaves the version unquoted, so it is read as a number. */
    @ParameterizedTest
    @CsvSource({
            "openapi: 3.0, V3_0",
            "openapi: 3.1, V3_1",
            "swagger: 2.0, V2_0",
    })
    void aVersionWrittenAsANumberIsStillRecognized(String yaml, OasVersion expected) {
        assertEquals(Optional.of(expected), detect(yaml));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "info: {title: t}",
            "openapi: 3.2.0",
            "openapi: 2.0",
            "openapi: 3.10.0",
            "openapi: 3.0x",
            "swagger: '1.2'",
            "openapi: {a: b}",
            "openapi: ~",
    })
    void anUnknownOrMissingVersionIsDetectedAsNothing(String yaml) {
        assertEquals(Optional.empty(), detect(yaml));
    }

    @Test
    void newDocumentsDeclareTheVersionTheyWereCreatedFor() {
        assertEquals("3.0.3", OasDocument.newDocument(DocumentFormat.YAML).getOpenApiVersion());
        assertEquals("3.0.3",
                OasDocument.newDocument(DocumentFormat.YAML, OasVersion.V3_0).getOpenApiVersion());
        OasDocument v31 = OasDocument.newDocument(DocumentFormat.JSON, OasVersion.V3_1);
        assertEquals("3.1.0", v31.getOpenApiVersion());
        assertEquals(Optional.of(OasVersion.V3_1), v31.getVersion());
    }

    /** Swagger 2.0 is import-only: there is no such thing as a new 2.0 document. */
    @Test
    void aNewSwagger20DocumentIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> OasDocument.newDocument(DocumentFormat.YAML, OasVersion.V2_0));
    }

    /** Detecting reads the version key as it is; an unquoted number is not rewritten as text. */
    @ParameterizedTest
    @CsvSource({
            "petstore-2.0.yaml, YAML",
            "petstore-3.1.json, JSON",
    })
    void detectingTheVersionNeverChangesTheDocument(String fixture, DocumentFormat format) throws IOException {
        OasDocument document = loadFixture(fixture, format);
        ObjectNode before = document.getRoot().deepCopy();

        document.getVersion();

        assertEquals(before, document.getRoot());
        assertEquals(DocumentWriter.write(loadFixture(fixture, format)), DocumentWriter.write(document));
    }

    @Test
    void anUnquotedVersionNumberIsLeftAsANumber() {
        OasDocument document = DocumentReader.read("swagger: 2.0\ninfo: {title: t, version: '1'}\n",
                DocumentFormat.YAML);

        document.getVersion();

        assertEquals(true, document.getRoot().get("swagger").isNumber());
    }

    private static Optional<OasVersion> detect(String yaml) {
        return OasVersion.detect(DocumentReader.read(yaml, DocumentFormat.YAML).getRoot());
    }

    private static OasDocument loadFixture(String name, DocumentFormat format) throws IOException {
        try (InputStream is = OasVersionTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return DocumentReader.read(new String(is.readAllBytes(), StandardCharsets.UTF_8), format);
        }
    }
}
