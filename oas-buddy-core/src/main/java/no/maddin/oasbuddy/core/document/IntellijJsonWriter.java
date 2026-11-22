package no.maddin.oasbuddy.core.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Writes JSON the way IntelliJ IDEA's formatter lays it out, for a {@link JsonLayout} with an
 * {@link ArrayWrapping}; the rules are stated there. Jackson's pretty printer cannot do this: whether
 * an array fits depends on the column it starts at and on its whole inline text, and a pretty printer
 * sees neither — it is told about one token at a time.
 *
 * <p>Scalars and keys are still written by Jackson, so quoting, escaping and number formatting are
 * exactly those of the default layout.
 *
 * <p>One IntelliJ behaviour is deliberately not copied: in a top-level array of objects it indents
 * every object after the first as if it were at the top level. An OpenAPI document is an object at its
 * root, so that case only arises in a hand-made file, and the editor writes it indented correctly.
 */
final class IntellijJsonWriter {

    private static final ObjectMapper SCALARS = DocumentReader.mapperFor(DocumentFormat.JSON);

    private final ArrayWrapping wrapping;
    private final int maxLineLength;
    private final String indent;
    private final int indentWidth;
    private final String lineEnding;
    private final String open;
    private final String close;
    private final String emptyArray;
    private final String emptyObject;

    private final StringBuilder out = new StringBuilder();
    /** The column the next character goes to; a tab counts as {@link #indentWidth}. */
    private int column;

    private IntellijJsonWriter(JsonLayout layout, String indent, int indentWidth, String lineEnding) {
        this.wrapping = layout.arrayWrapping();
        this.maxLineLength = layout.maxLineLength() == null
                ? JsonLayout.DEFAULT_MAX_LINE_LENGTH : layout.maxLineLength();
        this.indent = indent;
        this.indentWidth = indentWidth;
        this.lineEnding = lineEnding;
        boolean bracketSpaces = Boolean.TRUE.equals(layout.spacesWithinBrackets());
        this.open = bracketSpaces ? "[ " : "[";
        this.close = bracketSpaces ? " ]" : "]";
        this.emptyArray = bracketSpaces ? "[ ]" : "[]";
        this.emptyObject = Boolean.TRUE.equals(layout.spacesWithinBraces()) ? "{ }" : "{}";
    }

    /**
     * @param indent      one level of indentation: spaces, or a tab
     * @param indentWidth the columns one level takes, for measuring lines
     */
    static String write(JsonNode root, JsonLayout layout, String indent, int indentWidth, String lineEnding) {
        IntellijJsonWriter writer = new IntellijJsonWriter(layout, indent, indentWidth, lineEnding);
        writer.value(root, 0, 0);
        return writer.out.toString();
    }

    /** @param trailing the characters that follow the value on its line: 1 for a comma, else 0 */
    private void value(JsonNode node, int level, int trailing) {
        if (node.isObject() && !node.isEmpty()) {
            object(node, level);
        } else if (node.isArray() && !node.isEmpty()) {
            array(node, level, trailing);
        } else {
            append(inline(node));
        }
    }

    private void object(JsonNode node, int level) {
        append("{");
        int remaining = node.size();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            remaining--;
            newline(level + 1);
            append(scalar(TextNode.valueOf(field.getKey())) + ": ");
            element(field.getValue(), level + 1, remaining > 0);
        }
        newline(level);
        append("}");
    }

    private void array(JsonNode node, int level, int trailing) {
        String inline = inline(node);
        if (inline != null && staysInline(inline, trailing)) {
            append(inline);
            return;
        }
        append("[");
        if (wrapping == ArrayWrapping.DO_NOT_WRAP || wrapping == ArrayWrapping.WRAP_IF_LONG) {
            flow(node, level + 1);
        } else {
            chop(node, level + 1);
        }
        newline(level);
        append("]");
    }

    private boolean staysInline(String inline, int trailing) {
        return switch (wrapping) {
            case DO_NOT_WRAP -> true;
            case WRAP_ALWAYS -> false;
            case WRAP_IF_LONG, CHOP_DOWN_IF_LONG -> fits(inline.length() + trailing);
        };
    }

    /** One element per line. */
    private void chop(JsonNode array, int level) {
        int remaining = array.size();
        for (JsonNode element : array) {
            remaining--;
            newline(level);
            element(element, level, remaining > 0);
        }
    }

    /**
     * The elements one after the other from a fresh line; "wrap if long" starts another line before an
     * element that would not fit on this one. A multi-line element (an object) always continues the
     * line it starts on.
     */
    private void flow(JsonNode array, int level) {
        newline(level);
        int remaining = array.size();
        boolean first = true;
        for (JsonNode element : array) {
            remaining--;
            if (!first) {
                separate(element, level, remaining > 0);
            }
            first = false;
            element(element, level, remaining > 0);
        }
    }

    private void separate(JsonNode element, int level, boolean comma) {
        String inline = inline(element);
        if (wrapping == ArrayWrapping.WRAP_IF_LONG && inline != null
                && !fits(1 + inline.length() + (comma ? 1 : 0))) {
            newline(level);
        } else {
            append(" ");
        }
    }

    private void element(JsonNode element, int level, boolean comma) {
        value(element, level, comma ? 1 : 0);
        if (comma) {
            append(",");
        }
    }

    /** The value on one line, or {@code null} when it cannot be: it holds a non-empty object. */
    private String inline(JsonNode node) {
        if (node.isObject()) {
            return node.isEmpty() ? emptyObject : null;
        }
        if (!node.isArray()) {
            return scalar(node);
        }
        if (node.isEmpty()) {
            return emptyArray;
        }
        StringJoiner joined = new StringJoiner(", ", open, close);
        for (JsonNode element : node) {
            String text = inline(element);
            if (text == null) {
                return null;
            }
            joined.add(text);
        }
        return joined.toString();
    }

    private static String scalar(JsonNode node) {
        try {
            return SCALARS.writeValueAsString(node);
        } catch (IOException e) {
            throw new IllegalStateException("A JSON scalar could not be written: " + node, e);
        }
    }

    private boolean fits(int width) {
        return column + width <= maxLineLength;
    }

    private void newline(int level) {
        out.append(lineEnding).append(indent.repeat(level));
        column = level * indentWidth;
    }

    private void append(String text) {
        out.append(text);
        column += text.length();
    }
}
