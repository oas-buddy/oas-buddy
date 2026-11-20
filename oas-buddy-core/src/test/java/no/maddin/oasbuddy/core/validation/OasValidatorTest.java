package no.maddin.oasbuddy.core.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OasValidatorTest {

    private final OasValidator validator = new OasValidator();

    @Test
    void validFixtureHasNoErrors() throws IOException {
        ValidationResult result = validator.validate(readFixture("petstore.yaml"));
        assertTrue(result.isValid(), () -> "Unexpected messages: " + result.messages());
    }

    /** Array items, property references and additionalProperties, as the schema editor writes them. */
    @Test
    void schemaStructureFixtureHasNoErrors() throws IOException {
        ValidationResult result = validator.validate(readFixture("structured.yaml"));
        assertTrue(result.isValid(), () -> "Unexpected messages: " + result.messages());
    }

    /** Component responses, and operations referring to them instead of defining their own. */
    @Test
    void reusableComponentsFixtureHasNoErrors() throws IOException {
        ValidationResult result = validator.validate(readFixture("reusable.yaml"));
        assertTrue(result.isValid(), () -> "Unexpected messages: " + result.messages());
    }

    @Test
    void invalidFixtureHasErrors() throws IOException {
        ValidationResult result = validator.validate(readFixture("invalid.yaml"));
        assertFalse(result.isValid());
        assertFalse(result.messages().isEmpty());
    }

    @Test
    void contentThatCannotBeParsedIsReportedWithoutThrowing() {
        ValidationResult result = validator.validate("{ a: [unclosed");

        assertFalse(result.isValid());
    }

    /** The checks run in a fixed order, so the panel reads the same every time. */
    @Test
    void messagesFromEachCheckAppearInOrder() {
        ValidationResult result = validator.validate("""
                openapi: 3.0.3
                info: {title: T, version: '1'}
                paths:
                  /a:
                    get:
                      tags: [undeclared]
                      parameters:
                        - $ref: '#/components/parameters/Missing'
                      responses:
                        '200': {description: ok}
                components:
                  schemas:
                    S: {type: string, minLength: 5, maxLength: 1}
                """);

        List<String> text = result.messages().stream().map(ValidationMessage::message).toList();
        int constraint = indexOfContaining(text, "minLength");
        int tag = indexOfContaining(text, "undeclared");
        int reference = indexOfContaining(text, "Missing");
        assertTrue(constraint >= 0 && constraint < tag && tag < reference, text::toString);
    }

    @ParameterizedTest
    @ValueSource(strings = {"petstore-2.0.yaml", "petstore-2.0.json", "petstore-3.1.yaml", "petstore-3.1.json"})
    void validSwagger20AndOas31FixturesHaveNoErrors(String fixture) throws IOException {
        ValidationResult result = validator.validate(readFixture(fixture));
        assertTrue(result.isValid(), () -> "Unexpected messages: " + result.messages());
        assertTrue(result.messages().isEmpty(), () -> "Unexpected messages: " + result.messages());
    }

    /** Validated as what it is, not as a 3.0 document that forgot its openapi key. */
    @Test
    void aSwagger20DocumentIsNotReportedAsMissingTheOpenapiKey() throws IOException {
        List<String> text = messages(validator.validate(readFixture("petstore-2.0.yaml")));

        assertTrue(text.stream().noneMatch(message -> message.contains("openapi")), text::toString);
    }

    @Test
    void swaggerParserStillReportsWhatItFindsInASwagger20Document() {
        List<String> text = messages(validator.validate("""
                swagger: '2.0'
                info: {title: T, version: '1'}
                paths:
                  /a:
                    get: {}
                """));

        assertEquals(1, text.size(), text::toString);
        assertTrue(text.getFirst().contains("responses is missing"), text::toString);
    }

    /**
     * swagger-parser reports no dangling reference in a 2.0 document at all (verified) — not even to
     * a definition — so every one of them comes from ReferenceCheck, exactly once.
     */
    @Test
    void danglingSwagger20ReferencesAreEachReportedOnce() {
        List<String> text = messages(validator.validate("""
                swagger: '2.0'
                info: {title: T, version: '1'}
                paths:
                  /a:
                    get:
                      parameters:
                        - $ref: '#/parameters/Missing'
                      responses:
                        '200':
                          description: ok
                          schema: {$ref: '#/definitions/Gone'}
                """));

        assertEquals(2, text.size(), text::toString);
        assertTrue(text.stream().anyMatch(message -> message.contains("#/parameters/Missing")), text::toString);
        assertTrue(text.stream().anyMatch(message -> message.contains("#/definitions/Gone")), text::toString);
    }

    /** In 2.0 a response's examples map media types to raw data: a $ref in there is not a reference. */
    @Test
    void swagger20ResponseExamplesAreData() {
        ValidationResult result = validator.validate("""
                swagger: '2.0'
                info: {title: T, version: '1'}
                paths:
                  /a:
                    get:
                      responses:
                        '200':
                          description: ok
                          examples:
                            application/json: {$ref: '#/nowhere'}
                """);

        assertTrue(result.messages().isEmpty(), () -> "Unexpected messages: " + result.messages());
    }

    /** 2.0 keeps its reusable schemas under definitions, so constraints are checked there. */
    @Test
    void constraintsInSwagger20DefinitionsAreChecked() {
        List<String> text = messages(validator.validate("""
                swagger: '2.0'
                info: {title: T, version: '1'}
                paths: {}
                definitions:
                  Name:
                    type: object
                    properties:
                      schema: {type: string, minLength: 5, maxLength: 1}
                """));

        assertEquals(List.of("definitions.Name.properties.schema: minLength (5) is greater than maxLength (1),"
                + " so no value can satisfy it"), text);
    }

    @Test
    void undefinedTagsInASwagger20DocumentAreWarnings() {
        ValidationResult result = validator.validate("""
                swagger: '2.0'
                info: {title: T, version: '1'}
                paths:
                  /a:
                    get:
                      tags: [undeclared]
                      responses: {'200': {description: ok}}
                """);

        assertTrue(result.isValid());
        assertEquals(List.of(ValidationSeverity.WARNING),
                result.messages().stream().map(ValidationMessage::severity).toList());
    }

    @Test
    void swaggerParserReportsWhatItFindsInAnOas31Document() {
        List<String> text = messages(validator.validate("""
                openapi: 3.1.0
                info: {title: T}
                paths:
                  /a:
                    get:
                      parameters: [{name: x, in: bogus}]
                      responses:
                        '200': {$ref: '#/components/responses/Missing'}
                """));

        assertTrue(text.stream().anyMatch(message -> message.contains("info.version is missing")), text::toString);
        assertTrue(text.stream().anyMatch(message -> message.contains(".in is not of type")), text::toString);
        assertTrue(text.stream().anyMatch(message -> message.contains("#/components/responses/Missing")),
                text::toString);
    }

    private static List<String> messages(ValidationResult result) {
        return result.messages().stream().map(ValidationMessage::message).toList();
    }

    private static int indexOfContaining(List<String> messages, String fragment) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }

    private static String readFixture(String name) throws IOException {
        try (InputStream is = OasValidatorTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
