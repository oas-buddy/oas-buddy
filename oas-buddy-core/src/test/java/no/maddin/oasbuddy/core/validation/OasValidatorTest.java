package no.maddin.oasbuddy.core.validation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
