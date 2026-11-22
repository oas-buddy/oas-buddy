package no.maddin.oasbuddy.core.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonLayoutTest {

    private static final JsonLayout ALL_SET = new JsonLayout(ArrayWrapping.WRAP_IF_LONG, 80, true, false);
    private static final JsonLayout OTHERS = new JsonLayout(ArrayWrapping.WRAP_ALWAYS, 120, false, true);

    @Test
    void whatIsSetWinsOverTheFallback() {
        assertEquals(ALL_SET, ALL_SET.orElse(OTHERS));
    }

    @Test
    void whatIsUnsetComesFromTheFallback() {
        assertEquals(OTHERS, JsonLayout.DEFAULTS.orElse(OTHERS));
        assertEquals(new JsonLayout(ArrayWrapping.CHOP_DOWN_IF_LONG, 120, false, true),
                JsonLayout.wrapping(ArrayWrapping.CHOP_DOWN_IF_LONG).orElse(OTHERS));
    }

    /** A {@code null} layout is "nothing set", so options never carry a null to trip over. */
    @Test
    void optionsWithoutALayoutHaveTheDefaultOne() {
        assertEquals(JsonLayout.DEFAULTS, new FormatOptions(null, null, null, null, null, null).json());
        assertEquals(FormatOptions.DEFAULTS, new FormatOptions(null, null, null, null, null, null));
    }

    @Test
    void layoutsStackThroughTheOptions() {
        FormatOptions editorConfig = new FormatOptions(null, null, JsonLayout.wrapping(ArrayWrapping.DO_NOT_WRAP),
                null, null, null);
        FormatOptions preferences = new FormatOptions(4, null, OTHERS, null, null, null);

        assertEquals(new FormatOptions(4, null, new JsonLayout(ArrayWrapping.DO_NOT_WRAP, 120, false, true),
                null, null, null), editorConfig.orElse(preferences));
    }
}
