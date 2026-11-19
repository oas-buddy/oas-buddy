package no.maddin.oasbuddy.core.document;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentFormatTest {

    @Test
    void jsonAndBothYamlExtensionsAreRecognized() {
        assertEquals(Optional.of(DocumentFormat.JSON), DocumentFormat.detect("petstore.json"));
        assertEquals(Optional.of(DocumentFormat.YAML), DocumentFormat.detect("petstore.yaml"));
        assertEquals(Optional.of(DocumentFormat.YAML), DocumentFormat.detect("petstore.yml"));
    }

    @Test
    void theExtensionIsMatchedWithoutRegardToCase() {
        assertEquals(Optional.of(DocumentFormat.YAML), DocumentFormat.detect("PETSTORE.YAML"));
        assertEquals(Optional.of(DocumentFormat.JSON), DocumentFormat.detect("Petstore.Json"));
    }

    /** A name whose extension says nothing about the format is not an answer, and not an error. */
    @Test
    void anUnknownOrMissingExtensionIsDetectedAsNothing() {
        assertEquals(Optional.empty(), DocumentFormat.detect("petstore"));
        assertEquals(Optional.empty(), DocumentFormat.detect("petstore.txt"));
        assertEquals(Optional.empty(), DocumentFormat.detect("yaml"));
    }

    /** Reading insists on an answer: opening a file we cannot name the format of is an error. */
    @Test
    void fromFileNameRejectsWhatDetectCannotAnswer() {
        assertEquals(DocumentFormat.YAML, DocumentFormat.fromFileName("petstore.yml"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DocumentFormat.fromFileName("petstore.txt"));
        assertEquals("Unrecognized OAS file extension: petstore.txt", e.getMessage());
    }
}
