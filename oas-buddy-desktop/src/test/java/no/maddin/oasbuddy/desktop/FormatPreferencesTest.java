package no.maddin.oasbuddy.desktop;

import no.maddin.oasbuddy.core.document.FormatOptions;
import no.maddin.oasbuddy.core.document.LineEnding;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Formatting preferences round-trip through {@link Preferences}; an in-memory store keeps the user's own untouched. */
class FormatPreferencesTest {

    private final Preferences prefs = new InMemoryPreferences();

    @Test
    void nothingStoredIsTheDefaults() {
        assertEquals(FormatOptions.DEFAULTS, FormatPreferences.load(prefs));
    }

    @Test
    void everyOptionRoundTrips() {
        FormatOptions options = new FormatOptions(4, true, true, false, LineEnding.CRLF, true);

        FormatPreferences.save(prefs, options);

        assertEquals(options, FormatPreferences.load(prefs));
    }

    @Test
    void anOptionSetBackToDefaultLeavesNoKey() {
        FormatPreferences.save(prefs, new FormatOptions(4, true, true, true, LineEnding.LF, false));

        FormatPreferences.save(prefs, FormatOptions.DEFAULTS);

        assertEquals(FormatOptions.DEFAULTS, FormatPreferences.load(prefs));
        assertNull(prefs.get(FormatPreferences.LINE_ENDING, null), "the key is removed");
    }

    @Test
    void storedNonsenseReadsAsNotSet() {
        prefs.put(FormatPreferences.INDENT_SIZE, "wide");
        prefs.put(FormatPreferences.LINE_ENDING, "CR");

        assertEquals(FormatOptions.DEFAULTS, FormatPreferences.load(prefs));

        prefs.put(FormatPreferences.INDENT_SIZE, "-2");
        assertEquals(FormatOptions.DEFAULTS, FormatPreferences.load(prefs));
    }

    /** A {@link Preferences} node that lives in a map, so tests never write the user's real store. */
    private static final class InMemoryPreferences extends AbstractPreferences {

        private final Map<String, String> values = new HashMap<>();

        InMemoryPreferences() {
            super(null, "");
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
            values.clear();
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(String[]::new);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            throw new UnsupportedOperationException("no child nodes");
        }

        @Override
        protected void syncSpi() {
            // nothing to sync: the map is the store
        }

        @Override
        protected void flushSpi() {
            // nothing to flush: the map is the store
        }
    }
}
