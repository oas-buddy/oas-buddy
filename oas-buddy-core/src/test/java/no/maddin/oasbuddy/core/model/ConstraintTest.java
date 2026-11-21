package no.maddin.oasbuddy.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import no.maddin.oasbuddy.core.document.DocumentFormat;
import no.maddin.oasbuddy.core.document.DocumentReader;
import no.maddin.oasbuddy.core.document.DocumentWriter;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which constraints belong to which type, and how their values are read. */
class ConstraintTest {

    @Test
    void eachTypeHasItsOwnGroup() {
        assertAll(
                () -> assertEquals(List.of(Constraint.MINIMUM, Constraint.EXCLUSIVE_MINIMUM, Constraint.MAXIMUM,
                        Constraint.EXCLUSIVE_MAXIMUM, Constraint.MULTIPLE_OF), Constraint.forType("integer")),
                () -> assertEquals(Constraint.forType("integer"), Constraint.forType("number")),
                () -> assertEquals(List.of(Constraint.MIN_LENGTH, Constraint.MAX_LENGTH, Constraint.PATTERN),
                        Constraint.forType("string")),
                () -> assertEquals(List.of(Constraint.MIN_ITEMS, Constraint.MAX_ITEMS, Constraint.UNIQUE_ITEMS),
                        Constraint.forType("array")),
                () -> assertEquals(List.of(Constraint.MIN_PROPERTIES, Constraint.MAX_PROPERTIES),
                        Constraint.forType("object")),
                () -> assertEquals(List.of(), Constraint.forType("boolean")),
                () -> assertEquals(List.of(), Constraint.forType(null)));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "MINIMUM, 1.5",
            "MINIMUM, -3",
            "MULTIPLE_OF, 0.01",
            "MIN_LENGTH, 0",
            "MAX_ITEMS, 10",
            "MIN_LENGTH, -1",
            "MIN_LENGTH, 1.5",
    })
    void numbersAreReadAsNumbersEvenOnesValidationWillReport(Constraint constraint, String text) {
        JsonNode value = constraint.parse(text, OasVersion.V3_0);

        assertAll(
                () -> assertTrue(value.isNumber(), () -> "read as " + value.getNodeType()),
                () -> assertEquals(text, value.toString()));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "MINIMUM, abc",
            "MINIMUM, 1 2",
            "MAX_ITEMS, '\"3\"'",
            "MAX_ITEMS, true",
    })
    void textThatIsNotOfTheKindIsRejectedRatherThanWrittenAsAString(Constraint constraint, String text) {
        assertThrows(IllegalArgumentException.class, () -> constraint.parse(text, OasVersion.V3_0));
    }

    /** Draft 4 in 2.0 and 3.0, JSON Schema 2020-12 in 3.1. */
    @ParameterizedTest
    @EnumSource(value = Constraint.class, names = {"EXCLUSIVE_MINIMUM", "EXCLUSIVE_MAXIMUM"})
    void theExclusiveBoundsAreBooleansUntil31AndNumbersFrom31(Constraint constraint) {
        assertAll(
                () -> assertEquals(Constraint.Kind.BOOLEAN, constraint.kind(OasVersion.V2_0)),
                () -> assertEquals(Constraint.Kind.BOOLEAN, constraint.kind(OasVersion.V3_0)),
                () -> assertEquals(Constraint.Kind.NUMBER, constraint.kind(OasVersion.V3_1)),
                () -> assertTrue(constraint.accepts(BooleanNode.TRUE, OasVersion.V3_0)),
                () -> assertFalse(constraint.accepts(IntNode.valueOf(5), OasVersion.V3_0)),
                () -> assertTrue(constraint.accepts(IntNode.valueOf(5), OasVersion.V3_1)),
                () -> assertFalse(constraint.accepts(BooleanNode.TRUE, OasVersion.V3_1)),
                () -> assertEquals(IntNode.valueOf(5), constraint.parse("5", OasVersion.V3_1)),
                () -> assertThrows(IllegalArgumentException.class, () -> constraint.parse("true", OasVersion.V3_1)));
    }

    @ParameterizedTest
    @EnumSource(value = Constraint.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"EXCLUSIVE_MINIMUM", "EXCLUSIVE_MAXIMUM"})
    void everyOtherConstraintIsTheSameKindInEveryVersion(Constraint constraint) {
        assertAll(
                () -> assertEquals(constraint.kind(OasVersion.V3_0), constraint.kind(OasVersion.V2_0)),
                () -> assertEquals(constraint.kind(OasVersion.V3_0), constraint.kind(OasVersion.V3_1)));
    }

    /** Only numeric exclusive bounds can be compared with each other or with the plain bounds. */
    @Test
    void theExclusiveBoundsFormRangesOnlyIn31() {
        List<Constraint> exclusive = List.of(Constraint.EXCLUSIVE_MINIMUM, Constraint.EXCLUSIVE_MAXIMUM);
        assertAll(
                () -> assertFalse(Constraint.ranges(OasVersion.V3_0).contains(exclusive)),
                () -> assertTrue(Constraint.ranges(OasVersion.V3_1).contains(exclusive)),
                () -> assertTrue(Constraint.ranges(OasVersion.V3_1)
                        .contains(List.of(Constraint.MINIMUM, Constraint.EXCLUSIVE_MAXIMUM))),
                () -> assertTrue(Constraint.ranges(OasVersion.V3_1)
                        .containsAll(Constraint.ranges(OasVersion.V3_0))));
    }

    @Test
    void blankTextIsNoValue() {
        assertNull(Constraint.MINIMUM.parse(" ", OasVersion.V3_0));
    }

    @Test
    void aPatternIsTakenLiterally() {
        assertEquals("^[a-z]+$", Constraint.PATTERN.parse("^[a-z]+$", OasVersion.V3_0).asText());
    }

    @Test
    void changingTheTypeKeepsConstraintsThatNoLongerApplyAndSaysWhich() {
        Schema schema = OasDocument.newDocument(DocumentFormat.YAML).getComponents().getSchemas().addSchema("Code");
        schema.changeTypeTo("string");
        schema.setConstraint(Constraint.MAX_LENGTH, Constraint.MAX_LENGTH.parse("8", OasVersion.V3_0));
        schema.setConstraint(Constraint.PATTERN, Constraint.PATTERN.parse("^[A-Z]+$", OasVersion.V3_0));

        schema.changeTypeTo("integer");

        assertAll(
                () -> assertEquals(8, schema.getConstraint(Constraint.MAX_LENGTH).asInt()),
                () -> assertEquals(List.of(Constraint.MAX_LENGTH, Constraint.PATTERN), schema.constraintsNotApplying()));
    }

    @ParameterizedTest
    @EnumSource(DocumentFormat.class)
    void constraintsRoundTripAsNumbers(DocumentFormat format) {
        OasDocument document = OasDocument.newDocument(format);
        Schema price = document.getComponents().getSchemas().addSchema("Price");
        price.changeTypeTo("number");
        price.setConstraint(Constraint.MINIMUM, Constraint.MINIMUM.parse("0", OasVersion.V3_0));
        price.setConstraint(Constraint.EXCLUSIVE_MINIMUM, BooleanNode.TRUE);
        price.setConstraint(Constraint.MULTIPLE_OF, Constraint.MULTIPLE_OF.parse("0.01", OasVersion.V3_0));

        OasDocument reloaded = DocumentReader.read(DocumentWriter.write(document), format);
        Schema reloadedPrice = reloaded.getComponents().getSchemas().getSchema("Price");

        assertAll(
                () -> assertEquals(document.getRoot(), reloaded.getRoot()),
                () -> assertTrue(reloadedPrice.getConstraint(Constraint.MINIMUM).isNumber()),
                () -> assertTrue(reloadedPrice.getConstraint(Constraint.MULTIPLE_OF).isNumber()),
                () -> assertTrue(reloadedPrice.getConstraint(Constraint.EXCLUSIVE_MINIMUM).asBoolean()));
    }
}
