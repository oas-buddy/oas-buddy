package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OasConverterTest {

    private static final String DOC_3_0 = """
            openapi: 3.0.3
            info:
              title: Pets
              version: 1.0.0
            paths:
              /pets:
                get:
                  summary: List pets
                  responses:
                    '200':
                      description: ok
            components:
              schemas:
                Pet:
                  type: object
            """;

    private static final String DOC_2_0 = """
            swagger: '2.0'
            info:
              title: Pets
              version: 1.0.0
            paths: {}
            """;

    /** 3.0 → 3.1: renames {@code summary} to {@code title} in place and reports one conflict. */
    private static final ConversionStep RENAMING_3_0_TO_3_1 = new FakeStep(OasVersion.V3_0, OasVersion.V3_1) {
        @Override
        public List<Conflict> apply(ObjectNode root) {
            root.put("openapi", "3.1.0");
            if (!(root.at("/paths/~1pets/get") instanceof ObjectNode operation)) {
                return List.of();
            }
            JsonNodes.renameField(operation, "summary", "title");
            return List.of(Conflict.at(List.of("paths", "/pets", "get", "summary"), "renamed", "kept as title"));
        }
    };

    private static final ConversionStep PLAIN_3_1_TO_3_0 = new FakeStep(OasVersion.V3_1, OasVersion.V3_0) {
        @Override
        public List<Conflict> apply(ObjectNode root) {
            root.put("openapi", "3.0.3");
            return List.of();
        }
    };

    /** 2.0 → 3.0: swaps the version key in place and reports one conflict. */
    private static final ConversionStep IMPORTING_2_0_TO_3_0 = new FakeStep(OasVersion.V2_0, OasVersion.V3_0) {
        @Override
        public List<Conflict> apply(ObjectNode root) {
            JsonNodes.renameField(root, "swagger", "openapi");
            root.put("openapi", "3.0.3");
            return List.of(Conflict.at(List.of(), "imported", "nothing lost"));
        }
    };

    private final OasConverter converter =
            new OasConverter(List.of(RENAMING_3_0_TO_3_1, PLAIN_3_1_TO_3_0, IMPORTING_2_0_TO_3_0));

    @Test
    void theOriginalDocumentIsNeverTouched() {
        OasDocument original = read(DOC_3_0);
        JsonNode before = original.getRoot().deepCopy();

        ConversionResult result = converter.convert(original, OasVersion.V3_1);

        assertEquals(before, original.getRoot());
        assertNotSame(original.getRoot(), result.converted().getRoot());
        assertEquals(OasVersion.V3_1, result.converted().getVersion().orElseThrow());
    }

    @Test
    void keyOrderIsPreservedThroughAConversion() {
        OasDocument converted = converter.convert(read(DOC_3_0), OasVersion.V3_1).converted();

        assertEquals(List.of("openapi", "info", "paths", "components"), JsonNodes.fieldNames(converted.getRoot()));
        assertEquals(List.of("title", "responses"),
                JsonNodes.fieldNames(converted.getRoot().at("/paths/~1pets/get")));
    }

    @Test
    void theConvertedDocumentKeepsTheOriginalsFormat() {
        OasDocument json = DocumentReader.read("{\"openapi\": \"3.0.3\", \"info\": {}, \"paths\": {}}",
                DocumentFormat.JSON);

        assertEquals(DocumentFormat.JSON, converter.convert(json, OasVersion.V3_1).converted().getFormat());
    }

    @Test
    void stepsAreChainedToReachAVersionNoSingleStepGoesTo() {
        List<OasVersion> ran = new ArrayList<>();
        OasConverter recording = new OasConverter(List.of(
                recording(RENAMING_3_0_TO_3_1, ran), recording(IMPORTING_2_0_TO_3_0, ran)));

        ConversionResult result = recording.convert(read(DOC_2_0), OasVersion.V3_1);

        assertEquals(List.of(OasVersion.V2_0, OasVersion.V3_0), ran);
        assertEquals(OasVersion.V3_1, result.converted().getVersion().orElseThrow());
    }

    @Test
    void conflictsOfEveryStepAreCollectedInTheOrderTheStepsRan() {
        OasDocument withOperation = read(DOC_2_0.replace("paths: {}", """
                paths:
                  /pets:
                    get:
                      summary: List pets
                """));

        ConversionResult result = converter.convert(withOperation, OasVersion.V3_1);

        assertEquals(List.of(
                new Conflict("document root", "imported", "nothing lost"),
                new Conflict("GET /pets → summary", "renamed", "kept as title")), result.conflicts());
        assertFalse(result.isLossless());
    }

    @Test
    void aLosslessConversionReportsNoConflicts() {
        OasDocument v31 = converter.convert(read(DOC_3_0), OasVersion.V3_1).converted();

        ConversionResult back = converter.convert(v31, OasVersion.V3_0);

        assertTrue(back.isLossless());
    }

    @Test
    void convertingToTheSameVersionReturnsAnUnchangedCopy() {
        OasDocument original = read(DOC_3_0);

        ConversionResult result = converter.convert(original, OasVersion.V3_0);

        assertEquals(original.getRoot(), result.converted().getRoot());
        assertNotSame(original.getRoot(), result.converted().getRoot());
        assertTrue(result.isLossless());
    }

    @Test
    void thereIsNoConversionToSwagger2() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert(read(DOC_3_0), OasVersion.V2_0));
        assertThrows(IllegalArgumentException.class,
                () -> new OasConverter(List.of(new FakeStep(OasVersion.V3_0, OasVersion.V2_0))));
    }

    @Test
    void aDocumentWithoutAKnownVersionIsRefused() {
        OasDocument unknown = read("openapi: 3.2.0\ninfo: {}\npaths: {}\n");

        assertThrows(IllegalArgumentException.class, () -> converter.convert(unknown, OasVersion.V3_1));
    }

    @Test
    void aMissingStepMeansNoRoute() {
        OasConverter onlyImport = new OasConverter(List.of(IMPORTING_2_0_TO_3_0));

        assertThrows(IllegalArgumentException.class, () -> onlyImport.convert(read(DOC_3_0), OasVersion.V3_1));
    }

    @Test
    void aStepThatForgetsToChangeTheVersionIsABugAndTheOriginalSurvivesIt() {
        OasConverter broken = new OasConverter(List.of(new FakeStep(OasVersion.V3_0, OasVersion.V3_1) {
            @Override
            public List<Conflict> apply(ObjectNode root) {
                root.remove("components");
                return List.of();
            }
        }));
        OasDocument original = read(DOC_3_0);
        JsonNode before = original.getRoot().deepCopy();

        assertThrows(IllegalStateException.class, () -> broken.convert(original, OasVersion.V3_1));
        assertEquals(before, original.getRoot());
    }

    @Test
    void stepsMustChangeTheVersionAndMayNotOverlap() {
        assertThrows(IllegalArgumentException.class,
                () -> new OasConverter(List.of(new FakeStep(OasVersion.V3_0, OasVersion.V3_0))));
        assertThrows(IllegalArgumentException.class, () -> new OasConverter(List.of(
                RENAMING_3_0_TO_3_1, new FakeStep(OasVersion.V3_0, OasVersion.V3_1))));
    }

    @Test
    void targetsListsEveryReachableVersion() {
        assertEquals(Set.of(OasVersion.V3_0, OasVersion.V3_1), converter.targets(OasVersion.V2_0));
        assertEquals(Set.of(OasVersion.V3_1), converter.targets(OasVersion.V3_0));
        assertEquals(Set.of(OasVersion.V3_0), converter.targets(OasVersion.V3_1));
        assertEquals(Set.of(), new OasConverter(List.of()).targets(OasVersion.V3_0));
    }

    private static OasDocument read(String yaml) {
        return DocumentReader.read(yaml, DocumentFormat.YAML);
    }

    private static ConversionStep recording(ConversionStep step, List<OasVersion> ran) {
        return new FakeStep(step.from(), step.to()) {
            @Override
            public List<Conflict> apply(ObjectNode root) {
                ran.add(step.from());
                return step.apply(root);
            }
        };
    }

    private static class FakeStep implements ConversionStep {
        private final OasVersion from;
        private final OasVersion to;

        FakeStep(OasVersion from, OasVersion to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public OasVersion from() {
            return from;
        }

        @Override
        public OasVersion to() {
            return to;
        }

        @Override
        public List<Conflict> apply(ObjectNode root) {
            return List.of();
        }
    }
}
