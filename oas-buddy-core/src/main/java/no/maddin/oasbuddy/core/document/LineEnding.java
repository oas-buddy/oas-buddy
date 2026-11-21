package no.maddin.oasbuddy.core.document;

/** The line ending a document is written with; see {@link FormatOptions#lineEnding()}. */
public enum LineEnding {
    LF("\n"),
    CRLF("\r\n");

    private final String text;

    LineEnding(String text) {
        this.text = text;
    }

    /** The characters that end a line. */
    public String text() {
        return text;
    }
}
