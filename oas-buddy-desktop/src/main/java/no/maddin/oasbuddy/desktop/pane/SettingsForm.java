package no.maddin.oasbuddy.desktop.pane;

import atlantafx.base.theme.Styles;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import no.maddin.oasbuddy.core.document.ArrayWrapping;
import no.maddin.oasbuddy.core.document.FormatOptions;
import no.maddin.oasbuddy.core.document.JsonLayout;
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
    static final String LINE_LENGTH_ID = "format-line-length";
    static final String JSON_BRACKETS_ID = "format-json-brackets";
    static final String JSON_BRACES_ID = "format-json-braces";
    static final String YAML_SEQUENCES_ID = "format-yaml-sequences";
    static final String LINE_ENDING_ID = "format-line-ending";
    static final String FINAL_NEWLINE_ID = "format-final-newline";

    private static final List<Integer> INDENT_SIZES = List.of(2, 4, 8);
    private static final List<Integer> LINE_LENGTHS = List.of(80, 100, 120, 160);

    /** One entry of a choice box: what it says and the option value it stands for ({@code null} = default). */
    record Choice<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final ComboBox<Choice<Integer>> indentSize;
    private final ComboBox<Choice<Boolean>> jsonIndent;
    private final ComboBox<Choice<ArrayWrapping>> jsonArrays;
    private final ComboBox<Choice<Integer>> lineLength;
    private final ComboBox<Choice<Boolean>> jsonBrackets;
    private final ComboBox<Choice<Boolean>> jsonBraces;
    private final ComboBox<Choice<Boolean>> yamlSequences;
    private final ComboBox<Choice<LineEnding>> lineEnding;
    private final ComboBox<Choice<Boolean>> finalNewline;
    private final Node node;

    public SettingsForm(FormatOptions current) {
        indentSize = box(INDENT_SIZE_ID, indentChoices(current.indentSize()), current.indentSize());
        jsonIndent = box(JSON_INDENT_ID, List.of(
                new Choice<>("Default (spaces)", null), new Choice<>("Spaces", false), new Choice<>("Tabs", true)),
                current.indentWithTabs());
        JsonLayout json = current.json();
        jsonArrays = box(JSON_ARRAYS_ID, List.of(
                new Choice<>("Default (inline)", null),
                new Choice<>("Do not wrap", ArrayWrapping.DO_NOT_WRAP),
                new Choice<>("Wrap if long", ArrayWrapping.WRAP_IF_LONG),
                new Choice<>("Chop down if long", ArrayWrapping.CHOP_DOWN_IF_LONG),
                new Choice<>("Wrap always", ArrayWrapping.WRAP_ALWAYS)), json.arrayWrapping());
        lineLength = box(LINE_LENGTH_ID, lineLengthChoices(json.maxLineLength()), json.maxLineLength());
        jsonBrackets = box(JSON_BRACKETS_ID, yesNo("Default (no)"), json.spacesWithinBrackets());
        jsonBraces = box(JSON_BRACES_ID, yesNo("Default (no)"), json.spacesWithinBraces());
        // they shape IntelliJ's layouts only; Jackson's default has its own fixed spacing and no wrapping
        for (ComboBox<?> intellijOnly : List.of(lineLength, jsonBrackets, jsonBraces)) {
            intellijOnly.disableProperty().bind(jsonArrays.valueProperty().map(choice -> choice.value() == null));
        }
        yamlSequences = box(YAML_SEQUENCES_ID, List.of(
                new Choice<>("Default (flush with the key)", null), new Choice<>("Flush with the key", false),
                new Choice<>("Indented under the key", true)), current.yamlIndentSequences());
        lineEnding = box(LINE_ENDING_ID, List.of(
                new Choice<>("Default (JSON: the system's, YAML: LF)", null),
                new Choice<>("LF", LineEnding.LF), new Choice<>("CRLF", LineEnding.CRLF)), current.lineEnding());
        finalNewline = box(FINAL_NEWLINE_ID, yesNo("Default (JSON: no, YAML: yes)"), current.finalNewline());

        GridPane grid = FormFields.grid();
        int row = 0;
        grid.addRow(row++, new Label("Indent size"), indentSize);
        grid.addRow(row++, new Label("JSON indentation"), jsonIndent);
        grid.addRow(row++, new Label("JSON arrays"), jsonArrays);
        grid.addRow(row++, new Label("Line length"), lineLength);
        grid.addRow(row++, new Label("Spaces inside [ ]"), jsonBrackets);
        grid.addRow(row++, new Label("Spaces inside empty { }"), jsonBraces);
        grid.addRow(row++, new Label("YAML sequences"), yamlSequences);
        grid.addRow(row++, new Label("Line ending"), lineEnding);
        grid.addRow(row, new Label("Final newline"), finalNewline);

        Label note = new Label("The JSON array choices are IntelliJ IDEA's, and lay JSON out the way its "
                + "formatter does; line length and spacing apply to them only. A .editorconfig in the "
                + "document's folder or above takes precedence, including IntelliJ's ij_json_ and ij_yaml_ "
                + "keys. YAML is always indented with spaces.");
        note.setWrapText(true);
        note.getStyleClass().add(Styles.TEXT_MUTED);

        node = FormFields.root(FormFields.heading("Formatting"), grid, note);
    }

    public Node node() {
        return node;
    }

    /** The options as currently chosen. */
    public FormatOptions options() {
        JsonLayout json = new JsonLayout(value(jsonArrays), value(lineLength), value(jsonBrackets), value(jsonBraces));
        return new FormatOptions(value(indentSize), value(jsonIndent), json, value(yamlSequences),
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

    /** As {@link #indentChoices}: the usual lengths, "no limit", and the current one when it is something else. */
    private static List<Choice<Integer>> lineLengthChoices(Integer current) {
        List<Choice<Integer>> choices = new ArrayList<>();
        choices.add(new Choice<>("Default (" + JsonLayout.DEFAULT_MAX_LINE_LENGTH + ")", null));
        LINE_LENGTHS.forEach(length -> choices.add(new Choice<>(String.valueOf(length), length)));
        choices.add(new Choice<>("No limit", JsonLayout.NO_LIMIT));
        if (current != null && current != JsonLayout.NO_LIMIT && !LINE_LENGTHS.contains(current)) {
            choices.add(new Choice<>(String.valueOf(current), current));
        }
        return choices;
    }

    private static List<Choice<Boolean>> yesNo(String defaultLabel) {
        return List.of(new Choice<>(defaultLabel, null), new Choice<>("Yes", true), new Choice<>("No", false));
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
