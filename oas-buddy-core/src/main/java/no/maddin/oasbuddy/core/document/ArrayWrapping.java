package no.maddin.oasbuddy.core.document;

/**
 * IntelliJ IDEA's four ways of wrapping a JSON array, named after its code style settings and read
 * from its {@code ij_json_array_wrapping} key. What each does was established by running IntelliJ's
 * own formatter, not taken from its documentation; {@link JsonLayout} has the details.
 */
public enum ArrayWrapping {
    /** "Do not wrap" ({@code off}): an array stays on one line, however long. */
    DO_NOT_WRAP,
    /**
     * "Wrap if long" ({@code normal}): an array that does not fit goes onto the following lines, as
     * many elements to a line as fit.
     */
    WRAP_IF_LONG,
    /** "Chop down if long" ({@code on_every_item}): an array that does not fit gets one element per line. */
    CHOP_DOWN_IF_LONG,
    /** "Wrap always" ({@code split_into_lines}): one element per line, whatever the length. */
    WRAP_ALWAYS
}
