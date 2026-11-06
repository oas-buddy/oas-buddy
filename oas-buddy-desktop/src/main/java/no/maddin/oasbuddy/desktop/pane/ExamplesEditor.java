package no.maddin.oasbuddy.desktop.pane;

import atlantafx.base.theme.Styles;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import no.maddin.oasbuddy.core.model.Example;
import no.maddin.oasbuddy.core.model.MediaTypeExamples;

import java.util.List;
import java.util.function.Supplier;

/**
 * A named {@code examples} map, shared by the media type editor and the parameter editor so the two
 * cannot drift apart. The names are plain labels: renaming an example is not offered.
 */
final class ExamplesEditor extends ReferableList<Example> {

    private final Supplier<String> type;
    private final ComponentCatalog catalog;

    private ExamplesEditor(MediaTypeExamples examples, Supplier<String> type, ComponentCatalog catalog,
                           RemovalConfirmation confirmation) {
        super("example", examples, catalog::exampleNames, catalog::example,
                new Accessors<>(Example::isReference, Example::getReferencedExampleName, Example::getRef,
                        Example::isEmpty),
                "Its summary and value", confirmation);
        this.type = type;
        this.catalog = catalog;
    }

    /**
     * @param type    the type a value is read as when typed (see {@code SchemaValues}), asked at the
     *                moment of entry; {@code null} where there is none to go by
     * @param refresh rebuilds whatever contains this editor, run after the list changes
     */
    static Node build(MediaTypeExamples examples, Supplier<String> type, ComponentCatalog catalog,
                      RemovalConfirmation confirmation, Runnable refresh) {
        return new ExamplesEditor(examples, type, catalog, confirmation).build(refresh);
    }

    @Override
    List<Node> inline(Example example) {
        var value = ExampleForm.valueArea(example, type);
        value.setPrefRowCount(1);
        HBox.setHgrow(value, Priority.ALWAYS);
        return List.of(ExampleForm.summaryField(example), value);
    }

    /** What a reference stands for, so the row says more than a component name. */
    @Override
    Node resolved(Example reference) {
        Example component = catalog.example(reference.getReferencedExampleName());
        Label resolved = new Label(component == null ? "(not declared)"
                : component.getSummary() == null ? "" : component.getSummary());
        resolved.getStyleClass().add(Styles.TEXT_MUTED);
        return resolved;
    }

    @Override
    boolean renamable() {
        return false;
    }
}
