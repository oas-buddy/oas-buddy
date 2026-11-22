package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How {@link FormatOptions} change what {@link DocumentWriter} writes — and that the defaults change nothing. */
class FormatOptionsWriterTest {

    private static final String SOURCE = """
            openapi: 3.0.3
            info:
              title: Pets
              version: 1.0.0
            tags:
            - name: pets
            - name: owners
            paths:
              /pets:
                get:
                  tags:
                  - pets
                  responses:
                    '200':
                      description: ok
            """;

    /** The defaults are today's output, byte for byte, for every fixture in both formats. */
    @ParameterizedTest
    @CsvSource({
            "petstore.yaml, YAML", "petstore.json, JSON", "reusable.yaml, YAML", "reusable.json, JSON",
            "secured.yaml, YAML", "secured.json, JSON", "structured.yaml, YAML", "structured.json, JSON",
            "petstore-3.1.yaml, YAML", "petstore-3.1.json, JSON",
    })
    void theDefaultsChangeNothing(String fixture, DocumentFormat format) throws IOException {
        OasDocument document = fixture(fixture, format);

        assertEquals(DocumentWriter.write(document), DocumentWriter.write(document, FormatOptions.DEFAULTS));
    }

    @Test
    void jsonIndentAndArrayLayoutFollowTheOptions() {
        String written = DocumentWriter.write(json(), new FormatOptions(4, null, true, null, LineEnding.LF, null));

        assertTrue(written.contains("\n    \"info\": {\n        \"title\": \"Pets\""), written);
        assertTrue(written.contains("\"tags\": [\n        {\n            \"name\": \"pets\""), written);
        assertFalse(written.endsWith("\n"), "JSON keeps having no final newline unless asked");
    }

    @Test
    void jsonArraysStayInlineByDefault() {
        String written = DocumentWriter.write(json(), new FormatOptions(null, null, null, null, LineEnding.LF, null));

        assertTrue(written.contains("\"tags\": [ \"pets\" ]"), written);
    }

    @Test
    void jsonCanBeIndentedWithTabsAndEndWithCrlfAndANewline() {
        String written = DocumentWriter.write(json(),
                new FormatOptions(null, true, null, null, LineEnding.CRLF, true));

        assertTrue(written.startsWith("{\r\n\t\"openapi\": \"3.0.3\",\r\n"), written);
        assertTrue(written.endsWith("}\r\n"), written);
        assertFalse(written.replace("\r\n", "").contains("\n"), "every line ends with CRLF");
    }

    @Test
    void yamlIndentAndSequenceLayoutFollowTheOptions() {
        String written = DocumentWriter.write(yaml(), new FormatOptions(4, null, null, true, null, null));

        assertTrue(written.contains("info:\n    title: Pets\n"), written);
        assertTrue(written.contains("tags:\n  - name: pets\n  - name: owners\n"), written);
        assertTrue(written.contains("            tags:\n              - pets\n"), written);
    }

    /** At an indent of 2 the dash sits two in and the item's keys two further, as usually hand-written. */
    @Test
    void indentedSequencesAtTwoLookHandWritten() {
        OasDocument document = yaml();
        document.getPaths().getPathItem("/pets").getOperations().values().iterator().next()
                .setSummary("List pets");
        document.getRoot().withArray("tags").addObject().put("name", "vets").put("description", "Animal doctors");

        String written = DocumentWriter.write(document, new FormatOptions(null, null, null, true, null, null));

        assertTrue(written.contains("tags:\n  - name: pets\n  - name: owners\n  - name: vets\n    description: Animal doctors\n"),
                written);
    }

    /**
     * Flush sequences above an indent of 2: SnakeYAML pads the dash to the indent width. Valid YAML;
     * pinned here so a change in that behaviour is noticed.
     */
    @Test
    void flushSequencesAboveTwoHaveTheirDashPadded() {
        String written = DocumentWriter.write(yaml(), new FormatOptions(4, null, null, null, null, null));

        assertTrue(written.contains("tags:\n-   name: pets\n"), written);
    }

    /** YAML forbids tabs for indentation; the option is for JSON only. */
    @Test
    void yamlIgnoresTabs() {
        assertEquals(DocumentWriter.write(yaml()),
                DocumentWriter.write(yaml(), new FormatOptions(null, true, null, null, null, null)));
    }

    @Test
    void yamlCanEndWithCrlfAndWithoutAFinalNewline() {
        String written = DocumentWriter.write(yaml(), new FormatOptions(null, null, null, null, LineEnding.CRLF, false));

        assertTrue(written.startsWith("openapi: 3.0.3\r\ninfo:\r\n"), written);
        assertFalse(written.endsWith("\n"), written);
        assertFalse(written.replace("\r\n", "").contains("\n"), "every line ends with CRLF");
    }

    /** The YAML-specific features survive the custom layout path: no doc start, quoting, no line splitting. */
    @Test
    void theCustomYamlPathKeepsTheDiffFriendlySettings() {
        OasDocument document = yaml();
        document.getInfo().setVersion("3.0");
        document.getInfo().setDescription("word ".repeat(40).strip());

        String written = DocumentWriter.write(document, new FormatOptions(4, null, null, true, LineEnding.LF, null));

        assertFalse(written.startsWith("---"), written);
        assertTrue(written.contains("version: \"3.0\""), written);
        assertTrue(written.contains("description: " + "word ".repeat(40).strip() + "\n"), written);
    }

    /**
     * Whatever the layout, the document reads back the same, key order included. An indent of 10 is in
     * the set because SnakeYAML writes unparseable YAML there; the writer caps it.
     */
    @ParameterizedTest
    @MethodSource("layouts")
    void everyLayoutReadsBackTheSame(DocumentFormat format, FormatOptions options) {
        OasDocument document = format == DocumentFormat.JSON ? json() : yaml();

        OasDocument reloaded = DocumentReader.read(DocumentWriter.write(document, options), format);

        assertEquals(document.getRoot(), reloaded.getRoot());
        assertEquals(fieldOrder(document.getRoot()), fieldOrder(reloaded.getRoot()));
    }

    static Stream<Arguments> layouts() {
        List<Arguments> layouts = new ArrayList<>();
        for (DocumentFormat format : DocumentFormat.values()) {
            for (Integer indent : new Integer[]{null, 1, 4, 10}) {
                for (Boolean flag : new Boolean[]{null, true, false}) {
                    for (LineEnding lineEnding : new LineEnding[]{null, LineEnding.LF, LineEnding.CRLF}) {
                        layouts.add(Arguments.of(format, new FormatOptions(indent, flag, flag, flag, lineEnding, flag)));
                    }
                }
            }
        }
        return layouts.stream();
    }

    private static List<String> fieldOrder(JsonNode node) {
        List<String> order = new ArrayList<>();
        collect(node, "", order);
        return order;
    }

    private static void collect(JsonNode node, String path, List<String> order) {
        node.fieldNames().forEachRemaining(name -> {
            order.add(path + "/" + name);
            collect(node.get(name), path + "/" + name, order);
        });
        for (int i = 0; node.isArray() && i < node.size(); i++) {
            collect(node.get(i), path + "/" + i, order);
        }
    }

    private static OasDocument yaml() {
        return DocumentReader.read(SOURCE, DocumentFormat.YAML);
    }

    private static OasDocument json() {
        OasDocument document = yaml();
        document.setFormat(DocumentFormat.JSON);
        return document;
    }

    private static OasDocument fixture(String name, DocumentFormat format) throws IOException {
        try (InputStream is = FormatOptionsWriterTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return DocumentReader.read(new String(is.readAllBytes(), StandardCharsets.UTF_8), format);
        }
    }
}
