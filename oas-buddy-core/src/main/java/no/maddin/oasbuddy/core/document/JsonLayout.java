package no.maddin.oasbuddy.core.document;

/**
 * How JSON arrays and empty brackets are laid out — IntelliJ IDEA's JSON code style, so that a
 * repository formatted by IntelliJ is written back the way IntelliJ writes it.
 *
 * <p>With no {@link #arrayWrapping} the layout is Jackson's, as it always was ({@code [ "a", "b" ]},
 * {@code [ ]}, {@code { }}), and the other fields are ignored. With one, every array, bracket and
 * empty object follows IntelliJ's formatter:
 * <ul>
 *   <li>a non-empty object always spans several lines;</li>
 *   <li>an array holding such an element (or an array that does) opens a line after {@code [} and
 *       closes one before {@code ]}; "do not wrap" and "wrap if long" keep the elements flowing on
 *       it ({@code 1, {} … {@code }, 2}), the other two put each on its own line;</li>
 *   <li>otherwise an array stays inline when it fits: its line, a trailing comma included, may be
 *       {@link #maxLineLength} characters long and no longer.</li>
 * </ul>
 * Verified against IntelliJ IDEA 2026.3's command-line formatter; the fixtures under
 * {@code fixtures/intellij} are its output.
 *
 * @param arrayWrapping        {@code null} for Jackson's layout
 * @param maxLineLength        the longest line, in columns (a tab counts as one indentation width);
 *                             {@code null} is IntelliJ's default, {@value #DEFAULT_MAX_LINE_LENGTH};
 *                             {@link #NO_LIMIT} for none
 * @param spacesWithinBrackets {@code [ "a" ]} and {@code [ ]} rather than {@code ["a"]} and {@code []};
 *                             {@code null} is IntelliJ's default, no
 * @param spacesWithinBraces   {@code { }} rather than {@code {}} for an empty object; {@code null} is
 *                             IntelliJ's default, no
 */
public record JsonLayout(ArrayWrapping arrayWrapping, Integer maxLineLength, Boolean spacesWithinBrackets,
                         Boolean spacesWithinBraces) {

    /** IntelliJ's own default line length. */
    public static final int DEFAULT_MAX_LINE_LENGTH = 120;
    /** {@code max_line_length = off}: no limit, so nothing wraps for being long. */
    public static final int NO_LIMIT = Integer.MAX_VALUE;

    /** Nothing set: Jackson's layout. */
    public static final JsonLayout DEFAULTS = new JsonLayout(null, null, null, null);

    /** Only the wrapping set; everything else at IntelliJ's defaults. */
    public static JsonLayout wrapping(ArrayWrapping arrayWrapping) {
        return new JsonLayout(arrayWrapping, null, null, null);
    }

    /** This layout, with every field it leaves unset taken from {@code fallback}. */
    public JsonLayout orElse(JsonLayout fallback) {
        return new JsonLayout(
                arrayWrapping != null ? arrayWrapping : fallback.arrayWrapping,
                maxLineLength != null ? maxLineLength : fallback.maxLineLength,
                spacesWithinBrackets != null ? spacesWithinBrackets : fallback.spacesWithinBrackets,
                spacesWithinBraces != null ? spacesWithinBraces : fallback.spacesWithinBraces);
    }
}
