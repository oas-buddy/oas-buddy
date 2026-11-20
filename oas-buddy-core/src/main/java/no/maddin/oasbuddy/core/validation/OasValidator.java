package no.maddin.oasbuddy.core.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.parser.OpenAPIParser;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a document as the version it declares. 3.0 and 3.1 go to swagger-parser's
 * {@link OpenAPIV3Parser}; Swagger 2.0 goes to {@link OpenAPIParser}, which hands it to the 1.x parser
 * and its 2.0-to-3.0 converter. The converted model is thrown away — only the messages are wanted.
 *
 * <p>What swagger-parser reports differs a lot by version (verified against 2.1.27): for 3.1 it
 * reports about as much as for 3.0 (a missing {@code info.version}, an unknown parameter location, a
 * dangling schema reference), but for 2.0 next to nothing — not a dangling {@code #/definitions/}
 * reference, not a missing {@code info.version}, not an unknown parameter location; only gross
 * structural gaps such as an operation with no {@code responses}. The editor's own {@link Check}s
 * cover what they can for every version.
 */
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
        JsonNode tree = parse(content);
        OasVersion version = tree instanceof ObjectNode root
                ? OasVersion.detect(root).orElse(OasVersion.V3_0)
                : OasVersion.V3_0;
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        SwaggerParseResult result = version == OasVersion.V2_0
                ? new OpenAPIParser().readContents(content, null, options)
                : new OpenAPIV3Parser().readContents(content, null, options);
        List<ValidationMessage> messages = new ArrayList<>();
        if (result.getMessages() != null) {
            result.getMessages().forEach(message ->
                    messages.add(new ValidationMessage(ValidationSeverity.ERROR, message)));
        }
        if (tree != null) {
            CHECKS.forEach(check -> messages.addAll(check.check(tree, version)));
        }
        return new ValidationResult(messages);
    }

    /** {@code null} for content swagger-parser could not read either: it has already reported that. */
    private static JsonNode parse(String content) {
        try {
            return YAML.readTree(content);
        } catch (JsonProcessingException _) {
            return null;
        }
    }
}
