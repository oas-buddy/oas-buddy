package no.maddin.oasbuddy.desktop.pane;

import atlantafx.base.theme.Styles;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import no.maddin.oasbuddy.core.document.FormatOptions;
import no.maddin.oasbuddy.core.document.LineEnding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The editable body of the Settings dialog: how documents are formatted when no {@code .editorconfig}
 * says otherwise. Every choice starts with "Default", which leaves the option unset — the format's own
 * behaviour, i.e. how this editor has always written. Built apart from the dialog so it can be tested
 * without driving a modal window; see {@link SettingsDialog}.
 */
public final class SettingsForm {

    static final String INDENT_SIZE_ID = "format-indent-size";
    static final String JSON_INDENT_ID = "format-json-indent";
    static final String JSON_ARRAYS_ID = "format-json-arrays";
    static final String YAML_SEQUENCES_ID = "format-yaml-sequences";
    static final String LINE_ENDING_ID = "format-line-ending";
    static final String FINAL_NEWLINE_ID = "format-final-newline";

    private static final List<Integer> INDENT_SIZES = List.of(2, 4, 8);

    /** One entry of a choice box: what it says and the option value it stands for ({@code null} = default). */
    record Choice<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final ComboBox<Choice<Integer>> indentSize;
    private final ComboBox<Choice<Boolean>> jsonIndent;
    private final ComboBox<Choice<Boolean>> jsonArrays;
    private final ComboBox<Choice<Boolean>> yamlSequences;
    private final ComboBox<Choice<LineEnding>> lineEnding;
    private final ComboBox<Choice<Boolean>> finalNewline;
    private final Node node;

    public SettingsForm(FormatOptions current) {
        indentSize = box(INDENT_SIZE_ID, indentChoices(current.indentSize()), current.indentSize());
        jsonIndent = box(JSON_INDENT_ID, List.of(
                new Choice<>("Default (spaces)", null), new Choice<>("Spaces", false), new Choice<>("Tabs", true)),
                current.indentWithTabs());
        jsonArrays = box(JSON_ARRAYS_ID, List.of(
                new Choice<>("Default (inline)", null), new Choice<>("Inline", false),
                new Choice<>("One element per line", true)), current.jsonArraysOnePerLine());
        yamlSequences = box(YAML_SEQUENCES_ID, List.of(
                new Choice<>("Default (flush with the key)", null), new Choice<>("Flush with the key", false),
                new Choice<>("Indented under the key", true)), current.yamlIndentSequences());
        lineEnding = box(LINE_ENDING_ID, List.of(
                new Choice<>("Default (JSON: the system's, YAML: LF)", null),
                new Choice<>("LF", LineEnding.LF), new Choice<>("CRLF", LineEnding.CRLF)), current.lineEnding());
        finalNewline = box(FINAL_NEWLINE_ID, List.of(
                new Choice<>("Default (JSON: no, YAML: yes)", null), new Choice<>("Yes", true),
                new Choice<>("No", false)), current.finalNewline());

        GridPane grid = FormFields.grid();
        int row = 0;
        grid.addRow(row++, new Label("Indent size"), indentSize);
        grid.addRow(row++, new Label("JSON indentation"), jsonIndent);
        grid.addRow(row++, new Label("JSON arrays"), jsonArrays);
        grid.addRow(row++, new Label("YAML sequences"), yamlSequences);
        grid.addRow(row++, new Label("Line ending"), lineEnding);
        grid.addRow(row, new Label("Final newline"), finalNewline);

        Label note = new Label("A .editorconfig in the document's folder or above takes precedence, "
                + "including IntelliJ's ij_json_array_wrapping and ij_yaml_indent_sequence_value. "
                + "YAML is always indented with spaces.");
        note.setWrapText(true);
        note.getStyleClass().add(Styles.TEXT_MUTED);

        node = FormFields.root(FormFields.heading("Formatting"), grid, note);
    }

    public Node node() {
        return node;
    }

    /** The options as currently chosen. */
    public FormatOptions options() {
        return new FormatOptions(value(indentSize), value(jsonIndent), value(jsonArrays), value(yamlSequences),
                value(lineEnding), value(finalNewline));
    }

    /** The usual sizes, plus the current one when it is something else, so it is never silently replaced. */
    private static List<Choice<Integer>> indentChoices(Integer current) {
        List<Choice<Integer>> choices = new ArrayList<>();
        choices.add(new Choice<>("Default (2)", null));
        INDENT_SIZES.forEach(size -> choices.add(new Choice<>(String.valueOf(size), size)));
        if (current != null && !INDENT_SIZES.contains(current)) {
            choices.add(new Choice<>(String.valueOf(current), current));
        }
        return choices;
    }

    private static <T> ComboBox<Choice<T>> box(String id, List<Choice<T>> choices, T current) {
        ComboBox<Choice<T>> box = new ComboBox<>();
        box.setId(id);
        box.getItems().setAll(choices);
        box.setValue(choices.stream()
                .filter(choice -> Objects.equals(choice.value(), current))
                .findFirst()
                .orElse(choices.getFirst()));
        return box;
    }

    private static <T> T value(ComboBox<Choice<T>> box) {
        return box.getValue() == null ? null : box.getValue().value();
    }
}
