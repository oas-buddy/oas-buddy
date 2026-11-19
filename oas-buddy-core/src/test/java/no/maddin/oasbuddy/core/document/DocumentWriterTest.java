package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentWriterTest {

    private static final String JSON_SOURCE =
            "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"New API\",\"version\":\"1\"},\"paths\":{}}";

    @TempDir
    Path tempDir;

    @Test
    void jsonKeysAreFollowedByASpaceButNotPrecededByOne() throws IOException {
        OasDocument document = DocumentReader.read(
                "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"T\",\"version\":\"1\"},\"paths\":{}}",
                DocumentFormat.JSON);

        String written = DocumentWriter.write(document);

        assertTrue(written.contains("\"openapi\": \"3.0.3\""), written);
        assertTrue(written.contains("\"paths\": {"), written);
        assertFalse(written.contains("\" :"), written);
    }

    @Test
    void savingToAYamlPathConvertsAJsonDocument() throws IOException {
        OasDocument document = DocumentReader.read(JSON_SOURCE, DocumentFormat.JSON);

        Path file = tempDir.resolve("api.yaml");
        DocumentWriter.write(document, file);

        String written = Files.readString(file);
        // an empty "paths" is still emitted as {}, so the telltale is JSON's quoted keys
        assertFalse(written.contains("\"openapi\""), written);
        assertTrue(written.contains("openapi: 3.0.3"), written);
        assertEquals(DocumentFormat.YAML, document.getFormat(),
                "the document should remember the format it was saved in");
        assertEquals(document.getRoot(),
                DocumentReader.read(written, DocumentFormat.YAML).getRoot());
    }

    @Test
    void savingToAJsonPathConvertsAYamlDocument() throws IOException {
        OasDocument document = DocumentReader.read(
                "openapi: 3.0.3\ninfo:\n  title: New API\n  version: '1'\npaths: {}\n",
                DocumentFormat.YAML);

        Path file = tempDir.resolve("api.json");
        DocumentWriter.write(document, file);

        String written = Files.readString(file);
        assertTrue(written.contains("\"openapi\": \"3.0.3\""), written);
        assertEquals(DocumentFormat.JSON, document.getFormat());
        assertEquals(document.getRoot(),
                DocumentReader.read(written, DocumentFormat.JSON).getRoot());
    }

    /** A file name that names no format is not an error; the document stays as it is. */
    @Test
    void savingToAPathWithNoKnownExtensionKeepsTheDocumentFormat() throws IOException {
        OasDocument document = DocumentReader.read(JSON_SOURCE, DocumentFormat.JSON);

        Path file = tempDir.resolve("api.txt");
        DocumentWriter.write(document, file);

        assertEquals(DocumentFormat.JSON, document.getFormat());
        assertTrue(Files.readString(file).contains("\"openapi\": \"3.0.3\""));
    }

    /**
     * Jackson's SPLIT_LINES would break this across several lines with continuation escapes, so a
     * one-word edit would rewrite more of the diff than it changed.
     */
    @Test
    void longDescriptionsAreNotWrappedAcrossLines() {
        String description = "A description long enough that Jackson would wrap it at about eighty "
                + "columns, which makes the file unreadable and the diff noisy for no good reason "
                + "at all whatsoever.";
        OasDocument document = OasDocument.newDocument(DocumentFormat.YAML);
        document.getInfo().setDescription(description);

        String written = DocumentWriter.write(document);

        assertTrue(Arrays.stream(written.split("\n")).anyMatch(line -> line.contains(description)),
                written);
    }

    @Test
    void plainScalarsAreNotQuoted() {
        OasDocument document = OasDocument.newDocument(DocumentFormat.YAML);

        String written = DocumentWriter.write(document);

        assertTrue(written.contains("title: New API"), written);
        assertTrue(written.contains("openapi: 3.0.3"), written);
    }

    /**
     * The guard on MINIMIZE_QUOTES: a string whose text reads as a number must keep its quotes, or
     * it comes back as a number and the document silently changes shape on a round trip.
     */
    @Test
    void aStringThatLooksLikeANumberStaysQuoted() {
        OasDocument document = OasDocument.newDocument(DocumentFormat.YAML);
        document.getInfo().setVersion("3.0");

        String written = DocumentWriter.write(document);

        JsonNode version = DocumentReader.read(written, DocumentFormat.YAML)
                .getRoot().get("info").get("version");
        assertTrue(version.isTextual(), "version came back as " + version.getNodeType() + ": " + written);
        assertEquals("3.0", version.asText());
    }
}
