package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.JsonNodes;
import no.maddin.oasbuddy.core.document.OasVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a schema says it admits {@code null}: {@code nullable: true} in OAS 3.0, a {@code "null"} in a
 * {@code type} array in OAS 3.1.
 */
class SchemaNullabilityTest {

    // --- OAS 3.0: the nullable flag, unchanged ---

    @Test
    void in30NullableIsAFlagBesideTheType() {
        ObjectNode node = schema("{type: string}");

        new Schema(node).setNullable(true, OasVersion.V3_0);

        assertEquals(schema("{type: string, nullable: true}"), node);
        assertTrue(new Schema(node).isNullable());
    }

    @Test
    void in30ClearingNullableRemovesTheFlag() {
        ObjectNode node = schema("{type: string, nullable: true}");

        new Schema(node).setNullable(false, OasVersion.V3_0);

        assertEquals(schema("{type: string}"), node);
    }

    // --- OAS 3.1: "null" in the type array ---

    @Test
    void aTypeArrayWithNullReadsAsItsOtherTypeAndAsNullable() {
        Schema schema = new Schema(schema("{type: [string, 'null']}"));

        assertEquals("string", schema.getType());
        assertEquals(List.of("string", "null"), schema.getTypes());
        assertTrue(schema.isNullable());
    }

    @Test
    void nullMayComeFirstInTheArray() {
        Schema schema = new Schema(schema("{type: ['null', integer]}"));

        assertEquals("integer", schema.getType());
        assertTrue(schema.isNullable());
    }

    /** No single type to show: the form must not pretend there is one. */
    @Test
    void aTypeArrayOfSeveralRealTypesHasNoSingleType() {
        Schema schema = new Schema(schema("{type: [string, integer, 'null']}"));

        assertNull(schema.getType());
        assertEquals(List.of("string", "integer", "null"), schema.getTypes());
        assertTrue(schema.isNullable());
    }

    @Test
    void aPlainTypeIsOneTypeAndNoTypeIsNone() {
        assertEquals(List.of("string"), new Schema(schema("{type: string}")).getTypes());
        assertEquals(List.of(), new Schema(schema("{description: anything}")).getTypes());
    }

    @Test
    void in31NullableAddsNullToTheType() {
        ObjectNode node = schema("{type: string, description: d}");

        new Schema(node).setNullable(true, OasVersion.V3_1);

        assertEquals(schema("{type: [string, 'null'], description: d}"), node);
        assertEquals(List.of("type", "description"), fieldNames(node));
    }

    @Test
    void in31NullableAddsNullToAnExistingTypeArray() {
        ObjectNode node = schema("{type: [string, integer]}");

        new Schema(node).setNullable(true, OasVersion.V3_1);

        assertEquals(schema("{type: [string, integer, 'null']}"), node);
    }

    @Test
    void in31NullableTwiceAddsNullOnce() {
        ObjectNode node = schema("{type: [string, 'null']}");

        new Schema(node).setNullable(true, OasVersion.V3_1);

        assertEquals(schema("{type: [string, 'null']}"), node);
    }

    /**
     * A schema with no type admits every value, null included, already. Writing {@code type: [null]}
     * would instead admit nothing but null, so there is nothing to write.
     */
    @Test
    void in31ASchemaWithNoTypeIsLeftAlone() {
        ObjectNode node = schema("{description: anything}");

        new Schema(node).setNullable(true, OasVersion.V3_1);

        assertEquals(schema("{description: anything}"), node);
    }

    @Test
    void clearingNullableCollapsesAPairBackToAPlainType() {
        ObjectNode node = schema("{type: [string, 'null'], description: d}");

        new Schema(node).setNullable(false, OasVersion.V3_1);

        assertEquals(schema("{type: string, description: d}"), node);
        assertEquals(List.of("type", "description"), fieldNames(node));
        assertFalse(new Schema(node).isNullable());
    }

    @Test
    void clearingNullableKeepsTheRestOfALongerArray() {
        ObjectNode node = schema("{type: [string, 'null', integer]}");

        new Schema(node).setNullable(false, OasVersion.V3_1);

        assertEquals(schema("{type: [string, integer]}"), node);
    }

    /** Clearing removes either form, whichever the file has, so the checkbox can always be unticked. */
    @ParameterizedTest
    @CsvSource({"V3_0", "V3_1"})
    void clearingNullableRemovesBothForms(OasVersion version) {
        ObjectNode node = schema("{type: [string, 'null'], nullable: true}");

        new Schema(node).setNullable(false, version);

        assertEquals(schema("{type: string}"), node);
    }

    // --- changing the type keeps "null" ---

    @Test
    void changingTheTypeOfANullableSchemaKeepsItNullable() {
        ObjectNode node = schema("{type: [string, 'null'], minLength: 1}");

        new Schema(node).changeTypeTo("integer");

        assertEquals(schema("{type: [integer, 'null'], minLength: 1}"), node);
    }

    @Test
    void nullKeepsItsPlaceInTheArray() {
        ObjectNode node = schema("{type: ['null', string]}");

        new Schema(node).changeTypeTo("array");

        assertEquals(schema("{type: ['null', array]}"), node);
    }

    @Test
    void changingTheTypeOfASeveralTypeArrayKeepsOnlyNullBesideTheNewType() {
        ObjectNode node = schema("{type: [string, integer, 'null']}");

        new Schema(node).changeTypeTo("boolean");

        assertEquals(schema("{type: [boolean, 'null']}"), node);
    }

    @Test
    void settingTheTypeOfANonNullableSchemaWritesAPlainType() {
        ObjectNode node = schema("{type: [string, integer]}");

        new Schema(node).setType("boolean");

        assertEquals(schema("{type: boolean}"), node);
    }

    /** Removing the type entirely leaves a schema that admits anything, null included. */
    @Test
    void clearingTheTypeRemovesTheKey() {
        ObjectNode node = schema("{type: [string, 'null']}");

        new Schema(node).setType(null);

        assertEquals(schema("{}"), node);
    }

    @Test
    void readingNullabilityNeverChangesTheSchema() {
        ObjectNode node = schema("{type: [string, 'null']}");
        ObjectNode before = node.deepCopy();

        Schema schema = new Schema(node);
        schema.isNullable();
        schema.getType();
        schema.getTypes();

        assertEquals(before, node);
    }

    private static ObjectNode schema(String yaml) {
        return DocumentReader.read("s: " + yaml, DocumentFormat.YAML).getRoot().get("s").deepCopy();
    }

    private static List<String> fieldNames(ObjectNode node) {
        return JsonNodes.fieldNames(node);
    }
}
