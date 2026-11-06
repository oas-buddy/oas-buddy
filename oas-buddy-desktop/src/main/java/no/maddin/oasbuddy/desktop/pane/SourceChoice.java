package no.maddin.oasbuddy.desktop.pane;

import javafx.scene.control.ComboBox;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One entry of a "defined where" picker: inline ({@code name == null}) or a reference to the named
 * component. Shared by operation responses and by operation and path parameters.
 */
record SourceChoice(String name) {

    static final SourceChoice INLINE = new SourceChoice(null);

    /** Inline, then a reference to each component, then {@code current} if it is none of those. */
    static List<SourceChoice> choices(List<String> componentNames, SourceChoice current) {
        List<SourceChoice> choices = new java.util.ArrayList<>();
        choices.add(INLINE);
        componentNames.forEach(name -> choices.add(new SourceChoice(name)));
        if (!choices.contains(current)) {
            // a reference to something not declared: shown as it is, so the form agrees with the file
            choices.add(current);
        }
        return choices;
    }

    /**
     * The picker itself. Going to inline hands {@code toInline} the component the reference named;
     * going to a reference asks {@code toReference}, which returns {@code false} to decline (the
     * picker then goes back). Either way a taken switch ends in {@code refresh}.
     */
    static ComboBox<SourceChoice> picker(String styleClass, SourceChoice current, List<String> componentNames,
                                         Consumer<String> toInline, Predicate<String> toReference, Runnable refresh) {
        ComboBox<SourceChoice> picker = new ComboBox<>();
        picker.getStyleClass().add(styleClass);
        picker.getItems().addAll(choices(componentNames, current));
        picker.setValue(current);
        FormFields.guarded(picker.valueProperty(), (was, now) -> {
            if (now.isInline()) {
                toInline.accept(was.name());
            } else if (!toReference.test(now.name())) {
                return false;
            }
            refresh.run();
            return true;
        });
        return picker;
    }

    boolean isInline() {
        return name == null;
    }

    /** A reference is marked the same way {@link TypeChoice} marks one. */
    @Override
    public String toString() {
        return isInline() ? "Inline" : "→ " + name;
    }
}
