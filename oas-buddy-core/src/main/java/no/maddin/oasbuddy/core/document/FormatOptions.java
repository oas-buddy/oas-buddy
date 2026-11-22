package no.maddin.oasbuddy.core.document;

/**
 * How a document is laid out when it is written. Every field is optional: {@code null} means "the
 * format's own default", which is exactly how this editor wrote documents before the options existed,
 * so {@link #DEFAULTS} changes no byte of anyone's output.
 *
 * <p>Options come in layers — a repository's {@code .editorconfig} ({@link EditorConfigFormat}) over
 * the user's preferences — and {@link #orElse} stacks them field by field.
 *
 * @param indentSize           spaces per indentation level; JSON and YAML default to 2
 * @param indentWithTabs       JSON only: indent with one tab per level. YAML forbids tabs for
 *                             indentation, so it is ignored there
 * @param json                 JSON only: how arrays and brackets are laid out ({@link JsonLayout});
 *                             {@code null} is taken as {@link JsonLayout#DEFAULTS}, Jackson's layout
 * @param yamlIndentSequences  YAML: indent sequence items under their key ({@code   - a}); the default
 *                             writes them flush with it ({@code - a})
 * @param lineEnding           the default is the platform's for JSON and LF for YAML
 * @param finalNewline         whether the file ends with a line ending; the default is no for JSON and
 *                             yes for YAML
 */
public record FormatOptions(Integer indentSize, Boolean indentWithTabs, JsonLayout json,
                            Boolean yamlIndentSequences, LineEnding lineEnding, Boolean finalNewline) {

    public FormatOptions {
        json = json == null ? JsonLayout.DEFAULTS : json;
    }

    /** No option set: every format writes as it always has. */
    public static final FormatOptions DEFAULTS = new FormatOptions(null, null, null, null, null, null);

    /** These options, with every field they leave unset taken from {@code fallback}. */
    public FormatOptions orElse(FormatOptions fallback) {
        return new FormatOptions(
                indentSize != null ? indentSize : fallback.indentSize,
                indentWithTabs != null ? indentWithTabs : fallback.indentWithTabs,
                json.orElse(fallback.json),
                yamlIndentSequences != null ? yamlIndentSequences : fallback.yamlIndentSequences,
                lineEnding != null ? lineEnding : fallback.lineEnding,
                finalNewline != null ? finalNewline : fallback.finalNewline);
    }
}
