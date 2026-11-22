package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.DumperOptions;

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

    /** Both formats indent by two spaces unless told otherwise. */
    private static final int DEFAULT_INDENT = 2;
    /**
     * SnakeYAML accepts an indentation of 1 to 10, but at 10 an indented sequence comes out as YAML
     * that does not parse (an empty {@code -} line, then the item outdented). 9 is the largest that
     * round-trips in every layout — verified for 1 to 10, flush and indented; larger values are capped.
     */
    private static final int MAX_YAML_INDENT = 9;

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
        write(document, file, FormatOptions.DEFAULTS);
    }

    /** As {@link #write(OasDocument, Path)}, laid out as {@code options} say. */
    public static void write(OasDocument document, Path file, FormatOptions options) throws IOException {
        refuseImportOnly(document);
        DocumentFormat.detect(file.getFileName().toString()).ifPresent(document::setFormat);
        Files.writeString(file, write(document, options));
    }

    /**
     * @throws UnsupportedOperationException for a Swagger 2.0 document: 2.0 is import-only — it is
     *                                       converted to 3.0 to be edited, and never written, so a
     *                                       2.0 file is never overwritten with what this editor made
     *                                       of it
     */
    public static String write(OasDocument document) {
        return write(document, FormatOptions.DEFAULTS);
    }

    /**
     * As {@link #write(OasDocument)}, laid out as {@code options} say. {@link FormatOptions#DEFAULTS}
     * gives exactly the output of {@link #write(OasDocument)}.
     */
    public static String write(OasDocument document, FormatOptions options) {
        refuseImportOnly(document);
        try {
            return document.getFormat() == DocumentFormat.JSON
                    ? writeJson(document.getRoot(), options)
                    : writeYaml(document.getRoot(), options);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not serialize document", e);
        }
    }

    private static String writeJson(JsonNode root, FormatOptions options) throws IOException {
        String lineEnding = options.lineEnding() == null ? DefaultIndenter.SYS_LF : options.lineEnding().text();
        String indent = Boolean.TRUE.equals(options.indentWithTabs())
                ? "\t" : " ".repeat(Math.max(1, indentSize(options)));
        String text = options.json().arrayWrapping() == null
                ? DocumentReader.mapperFor(DocumentFormat.JSON)
                        .writer(JSON_PRINTER.withObjectIndenter(new DefaultIndenter(indent, lineEnding)))
                        .writeValueAsString(root)
                : IntellijJsonWriter.write(root, options.json(), indent, indentSize(options), lineEnding);
        // JSON has always been written without a final newline
        return Boolean.TRUE.equals(options.finalNewline()) ? text + lineEnding : text;
    }

    private static String writeYaml(JsonNode root, FormatOptions options) throws IOException {
        String text = yamlMapper(options).writeValueAsString(root);
        // YAML has always been written with a final newline
        return Boolean.FALSE.equals(options.finalNewline()) ? text.stripTrailing() : text;
    }

    /**
     * The mapper YAML is written with. Without a layout option it is the very mapper used all along, so
     * the default output cannot drift; otherwise the same generator features plus SnakeYAML dumper
     * options. Handing Jackson dumper options replaces the ones its features would build, so the two
     * that matter here — block style and no line splitting — are set again explicitly.
     */
    static ObjectMapper yamlMapper(FormatOptions options) {
        if (options.indentSize() == null && options.yamlIndentSequences() == null && options.lineEnding() == null) {
            return DocumentReader.mapperFor(DocumentFormat.YAML);
        }
        int indent = Math.clamp(indentSize(options), 1, MAX_YAML_INDENT);
        DumperOptions dumper = new DumperOptions();
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setSplitLines(false);
        dumper.setIndent(indent);
        if (Boolean.TRUE.equals(options.yamlIndentSequences())) {
            indentSequences(dumper, indent);
        }
        if (options.lineEnding() != null) {
            dumper.setLineBreak(options.lineEnding() == LineEnding.CRLF
                    ? DumperOptions.LineBreak.WIN : DumperOptions.LineBreak.UNIX);
        }
        return new ObjectMapper(DocumentReader.yamlFactory().dumperOptions(dumper).build());
    }

    /**
     * Indents a sequence the way people write it: the dash inside its key's block, the item's own keys
     * on the indentation grid — {@code tags:} / {@code   - name: pets} / {@code     in: query} at an
     * indent of 4 (found by trying SnakeYAML's settings, not guessed). Above an indent of 2 that is the
     * dash two columns short of the indent, not counted as indentation; at 2 or less, the dash counted
     * as indentation, as Jackson's own {@code INDENT_ARRAYS_WITH_INDICATOR} does.
     *
     * <p>Flush sequences (the default) have no such setting: above an indent of 2 SnakeYAML pads the
     * dash to the indent width ({@code -   name: pets}). Valid YAML, just not hand-written-looking.
     */
    private static void indentSequences(DumperOptions dumper, int indent) {
        if (indent > DEFAULT_INDENT) {
            dumper.setIndicatorIndent(indent - DEFAULT_INDENT);
        } else {
            dumper.setIndicatorIndent(indent);
            dumper.setIndentWithIndicator(true);
        }
    }

    private static int indentSize(FormatOptions options) {
        return options.indentSize() == null ? DEFAULT_INDENT : options.indentSize();
    }

    private static void refuseImportOnly(OasDocument document) {
        if (document.getVersion().orElse(null) == OasVersion.V2_0) {
            throw new UnsupportedOperationException(
                    "Swagger 2.0 documents are import-only: convert to OpenAPI 3.0 before saving");
        }
    }
}
