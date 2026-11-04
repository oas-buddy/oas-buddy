package no.maddin.oasbuddy.core.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import no.maddin.oasbuddy.core.document.OasDocument;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.util.ArrayList;
import java.util.List;

public final class OasValidator {

    /** Reads YAML and, YAML being a superset of it, JSON. */
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /** What the editor checks beyond swagger-parser, in the order its messages are listed. */
    private static final List<Check> CHECKS = List.of(
            ConstraintCheck::check, TagCheck::check, ReferenceCheck::check, LinkCheck::check);

    public ValidationResult validate(OasDocument document) {
        return validate(document.getRoot().toString());
    }

    public ValidationResult validate(String content) {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(content, null, options);
        List<ValidationMessage> messages = new ArrayList<>();
        result.getMessages().forEach(message -> messages.add(new ValidationMessage(ValidationSeverity.ERROR, message)));
        JsonNode tree = parse(content);
        if (tree != null) {
            CHECKS.forEach(check -> messages.addAll(check.check(tree)));
        }
        return new ValidationResult(messages);
    }

    /** {@code null} for content swagger-parser could not read either: it has already reported that. */
    private static JsonNode parse(String content) {
        try {
            return YAML.readTree(content);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
