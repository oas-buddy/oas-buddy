package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DocumentWriter {

    /**
     * Jackson's default puts a space on both sides of the colon ({@code "key" : value}); the
     * convention in hand-written JSON, and so in the diffs this editor exists to keep clean, is
     * a space after it only.
     */
    private static final DefaultPrettyPrinter JSON_PRINTER = new DefaultPrettyPrinter()
            .withSeparators(Separators.createDefaultInstance()
                    .withObjectFieldValueSpacing(Separators.Spacing.AFTER));

    private DocumentWriter() {
    }

    /**
     * Writes the document to a file, in the format the file name names.
     *
     * <p>Symmetric with {@link DocumentReader#read(Path)}, which also takes the format from the
     * path and stamps it onto the document: that is what makes "Save As {@code api.yaml}" convert a
     * document loaded from JSON, instead of writing JSON bytes into a {@code .yaml} file. A path
     * whose extension names no format leaves the document's own format alone.
     *
     * @throws UnsupportedOperationException for a Swagger 2.0 document, before the file is touched;
     *                                       see {@link #write(OasDocument)}
     */
    public static void write(OasDocument document, Path file) throws IOException {
        refuseImportOnly(document);
        DocumentFormat.detect(file.getFileName().toString()).ifPresent(document::setFormat);
        Files.writeString(file, write(document));
    }

    /**
     * @throws UnsupportedOperationException for a Swagger 2.0 document: 2.0 is import-only — it is
     *                                       converted to 3.0 to be edited, and never written, so a
     *                                       2.0 file is never overwritten with what this editor made
     *                                       of it
     */
    public static String write(OasDocument document) {
        refuseImportOnly(document);
        DocumentFormat format = document.getFormat();
        ObjectMapper mapper = DocumentReader.mapperFor(format);
        try {
            if (format == DocumentFormat.JSON) {
                return mapper.writer(JSON_PRINTER).writeValueAsString(document.getRoot());
            }
            return mapper.writeValueAsString(document.getRoot());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not serialize document", e);
        }
    }

    private static void refuseImportOnly(OasDocument document) {
        if (document.getVersion().orElse(null) == OasVersion.V2_0) {
            throw new UnsupportedOperationException(
                    "Swagger 2.0 documents are import-only: convert to OpenAPI 3.0 before saving");
        }
    }
}
