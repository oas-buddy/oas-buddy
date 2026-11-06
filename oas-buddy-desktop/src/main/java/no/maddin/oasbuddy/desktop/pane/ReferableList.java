package no.maddin.oasbuddy.desktop.pane;

import atlantafx.base.theme.Styles;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import no.maddin.oasbuddy.core.model.ReferableObjects;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A named map of entries that are each inline or a reference to a component — a response's headers
 * and links, an operation's callbacks, a media type's examples. A row is the entry's name, a
 * {@link SourceChoice} picker and a Remove button; below the rows is an add row with a "Reusable X"
 * combo. A kind supplies only what is inside an inline row.
 *
 * <p>The style classes derive from the noun ({@code header-row}, {@code new-header-name},
 * {@code add-header-reference}, …). Nothing carries an id: entry names are user data and several
 * lists are on screen at once.
 *
 * <p>Switching rules: inline → reference asks only when the entry has something to lose; reference →
 * inline copies the component and never asks.
 */
abstract class ReferableList<T> {

    /** How to read an entry; the facade types share no interface, so each passes its own methods. */
    record Accessors<T>(Predicate<T> isReference, Function<T, String> referencedName,
                        Function<T, String> ref, Predicate<T> isEmpty) {
    }

    private final String noun;
    private final ReferableObjects<T> entries;
    private final Supplier<List<String>> componentNames;
    private final Function<String, T> component;
    private final Accessors<T> entry;
    private final String lost;
    final RemovalConfirmation confirmation;

    /** @param lost what an inline entry would lose, as "Its description and schema" */
    ReferableList(String noun, ReferableObjects<T> entries, Supplier<List<String>> componentNames,
                  Function<String, T> component, Accessors<T> entry, String lost, RemovalConfirmation confirmation) {
        this.noun = noun;
        this.entries = entries;
        this.componentNames = componentNames;
        this.component = component;
        this.entry = entry;
        this.lost = lost;
        this.confirmation = confirmation;
    }

    /** What follows the picker in the row of an inline entry. */
    abstract List<Node> inline(T inlineEntry);

    /** Shown on the row after the picker for a reference; {@code null} for nothing. */
    Node resolved(T referenceEntry) {
        return null;
    }

    /** Shown under the row of an inline entry, in a card; {@code null} for nothing. */
    Node below(T inlineEntry) {
        return null;
    }

    /** Whether the entry may be removed now — a kind that holds a lot asks first. */
    boolean mayRemove(String name, T removable) {
        return true;
    }

    /** Whether the name is a rename field; otherwise a plain label. */
    boolean renamable() {
        return true;
    }

    Pos align() {
        return Pos.CENTER_LEFT;
    }

    /**
     * @param onChanged rebuilds whatever contains this list, run after every change; {@code null}
     *                  means the list rebuilds itself
     */
    final VBox build(Runnable onChanged) {
        VBox box = new VBox(6);
        fill(box, onChanged);
        return box;
    }

    private void fill(VBox box, Runnable onChanged) {
        box.getChildren().clear();
        Runnable refresh = onChanged != null ? onChanged : () -> fill(box, null);
        for (String name : entries.names()) {
            box.getChildren().add(row(name, refresh));
        }
        box.getChildren().add(addRow(refresh));
    }

    private Node addRow(Runnable refresh) {
        TextField nameField = new TextField();
        nameField.getStyleClass().add("new-" + noun + "-name");
        nameField.setPromptText(noun + " name");
        Button addButton = new Button("Add " + noun);
        addButton.getStyleClass().addAll(Styles.SMALL, "add-" + noun);
        Runnable add = () -> {
            String name = nameField.getText();
            if (name != null && !name.isBlank() && !entries.names().contains(name.strip())) {
                entries.add(name.strip());
                refresh.run();
            }
        };
        addButton.setOnAction(e -> add.run());
        nameField.setOnAction(e -> add.run());
        HBox addRow = new HBox(8, nameField, addButton);
        addRow.setAlignment(Pos.CENTER_LEFT);

        if (!componentNames.get().isEmpty()) {
            ComboBox<String> components = new ComboBox<>();
            components.getStyleClass().add("reference-" + noun);
            components.setPromptText("Reusable " + noun);
            components.getItems().addAll(componentNames.get());
            Button referButton = new Button("Add reference");
            referButton.getStyleClass().addAll(Styles.SMALL, "add-" + noun + "-reference");
            // named by the name field if one was typed, otherwise after the component
            referButton.setOnAction(e -> {
                String chosen = components.getValue();
                String typed = nameField.getText();
                String name = typed == null || typed.isBlank() ? chosen : typed.strip();
                if (chosen != null && !entries.names().contains(name)) {
                    entries.referTo(name, chosen);
                    refresh.run();
                }
            });
            addRow.getChildren().addAll(components, referButton);
        }
        return addRow;
    }

    private Node row(String name, Runnable refresh) {
        T value = entries.get(name);
        HBox row = new HBox(8, nameControl(name, refresh));
        row.getStyleClass().add(noun + "-row");
        row.setAlignment(align());
        Node below = null;

        if (value == null) {
            row.getChildren().add(FormFields.notAnObject());
        } else if (entry.isReference().test(value) && entry.referencedName().apply(value) == null) {
            // a reference this editor cannot follow: shown, not edited
            Label raw = new Label("$ref: " + entry.ref().apply(value));
            raw.getStyleClass().add(Styles.TEXT_MUTED);
            row.getChildren().add(raw);
        } else {
            row.getChildren().add(sourcePicker(name, value, refresh));
            if (entry.isReference().test(value)) {
                Node resolved = resolved(value);
                if (resolved != null) {
                    row.getChildren().add(resolved);
                }
            } else {
                row.getChildren().addAll(inline(value));
                below = below(value);
            }
        }

        Button removeButton = new Button("Remove");
        removeButton.getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED, Styles.SMALL);
        removeButton.setOnAction(e -> {
            if (mayRemove(name, value)) {
                entries.remove(name);
                refresh.run();
            }
        });
        row.getChildren().add(removeButton);
        if (below == null) {
            return row;
        }
        VBox card = new VBox(8, row, below);
        card.getStyleClass().add(noun + "-card");
        return card;
    }

    private Node nameControl(String name, Runnable refresh) {
        if (!renamable()) {
            return new Label(name);
        }
        TextField nameField = FormFields.renameField(name, candidate ->
                entries.names().contains(candidate)
                        ? "A " + noun + " named \"" + candidate + "\" already exists."
                        : entries.rename(name, candidate) ? null : "");
        nameField.getStyleClass().add(noun + "-name");
        nameField.setPrefColumnCount(12);
        // the row is rebuilt after a rename so its buttons act on the new key
        nameField.focusedProperty().addListener((obs, was, is) -> {
            if (!is && !entries.names().contains(name)) {
                refresh.run();
            }
        });
        return nameField;
    }

    private ComboBox<SourceChoice> sourcePicker(String name, T value, Runnable refresh) {
        boolean reference = entry.isReference().test(value);
        SourceChoice current = reference ? new SourceChoice(entry.referencedName().apply(value)) : SourceChoice.INLINE;
        return SourceChoice.picker(noun + "-source", current, componentNames.get(),
                was -> entries.defineInline(name, component.apply(was)),
                to -> {
                    if (!reference && !entry.isEmpty().test(value) && !confirmation.confirm(
                            "Replace the inline " + noun + " \"" + name + "\" with a reference to \"" + to + "\"?",
                            lost + " are defined only here, and will be discarded.")) {
                        return false;
                    }
                    entries.referTo(name, to);
                    return true;
                }, refresh);
    }
}
