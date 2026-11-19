package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DocumentReader {

    private DocumentReader() {
    }

    public static OasDocument read(Path file) throws IOException {
        DocumentFormat format = DocumentFormat.fromFileName(file.getFileName().toString());
        String content = Files.readString(file);
        return read(content, format);
    }

    public static OasDocument read(String content, DocumentFormat format) {
        ObjectMapper mapper = mapperFor(format);
        try {
            JsonNode tree = mapper.readTree(content);
            if (!(tree instanceof ObjectNode root)) {
                throw new DocumentParseException(
                        "Top level of an OpenAPI document must be an object", null);
            }
            return OasDocument.wrap(root, format);
        } catch (JsonProcessingException e) {
            JsonLocation location = e.getLocation();
            Integer line = location != null ? location.getLineNr() : null;
            Integer column = location != null ? location.getColumnNr() : null;
            throw new DocumentParseException(
                    "Could not parse " + format + " document: " + e.getOriginalMessage(), e, line, column);
        }
    }

    static ObjectMapper mapperFor(DocumentFormat format) {
        return switch (format) {
            case JSON -> new ObjectMapper();
            // These are generator features, so they shape what we write without affecting reads.
            //
            // SPLIT_LINES is on by default and hard-wraps a long description at about eighty
            // columns using YAML continuation escapes, so a one-word edit rewrites several lines of
            // the diff this editor exists to keep small.
            //
            // MINIMIZE_QUOTES drops the quotes wherever the scalar is unambiguous, which is how
            // OAS documents are written by hand. ALWAYS_QUOTE_NUMBERS_AS_STRINGS has to go with it:
            // on its own MINIMIZE_QUOTES only keeps the quotes around YAML's reserved words (true,
            // null, ...), so the string "3.0" would be written as a bare 3.0 and read back as a
            // number. DocumentWriterTest guards that case.
            case YAML -> new ObjectMapper(YAMLFactory.builder()
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .disable(YAMLGenerator.Feature.SPLIT_LINES)
                    .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                    .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                    .build());
        };
    }
}
