package no.maddin.oasbuddy.core.document;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The JSON writer against IntelliJ IDEA's own formatter, byte for byte. Each case folder under
 * {@code fixtures/intellij} holds an {@code .editorconfig} and IntelliJ's output for every compact
 * {@code *.source.json} under it; {@code tools/generate-intellij-json-fixtures.sh} regenerates them.
 *
 * <p>The options come from the case's {@code .editorconfig} through {@link EditorConfigFormat}, so
 * this also checks that IntelliJ's keys are read the way IntelliJ means them. The fixtures are read
 * from the source tree rather than the classpath: the case folders' {@code .editorconfig} is what is
 * being tested, and the walk up from a file has to find it.
 */
class IntellijJsonLayoutTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/intellij");
    private static final String SOURCE_SUFFIX = ".source.json";

    static Stream<Arguments> fixtures() throws IOException {
        List<String> sources;
        List<Path> cases;
        try (Stream<Path> files = Files.list(FIXTURES)) {
            sources = files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(SOURCE_SUFFIX))
                    .map(name -> name.substring(0, name.length() - SOURCE_SUFFIX.length()))
                    .sorted()
                    .toList();
        }
        try (Stream<Path> files = Files.list(FIXTURES)) {
            cases = files.filter(Files::isDirectory).sorted().toList();
        }
        return cases.stream().flatMap(dir -> sources.stream()
                .map(source -> Arguments.of(dir.getFileName().toString(), source)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("fixtures")
    void writesWhatIntellijWrites(String layout, String source) throws IOException {
        Path expected = FIXTURES.resolve(layout).resolve(source + ".json");
        OasDocument document = DocumentReader.read(FIXTURES.resolve(source + SOURCE_SUFFIX));
        FormatOptions options = EditorConfigFormat.forFile(expected);

        assertNotNull(options.json().arrayWrapping(), "the case's .editorconfig was not found");
        assertEquals(Files.readString(expected), DocumentWriter.write(document, options));
    }
}
