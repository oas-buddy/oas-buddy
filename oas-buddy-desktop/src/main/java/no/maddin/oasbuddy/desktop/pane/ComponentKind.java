package no.maddin.oasbuddy.desktop.pane;

import no.maddin.oasbuddy.core.model.ComponentSection;
import no.maddin.oasbuddy.core.model.Components;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * What differs between the reusable sections under {@code components} that are listed, edited and
 * removed the same way (responses, parameters, examples, headers, links, callbacks). The list pane,
 * the item pane frame and the removal flow read their texts and ids from here, so a new section is
 * one {@link ComponentKinds} entry rather than a list pane, a removal class and an outline block.
 *
 * @param noun          singular, lower case: {@code header}; ids and sentences derive from it
 * @param article       {@code a} or {@code an}, for "a header" in the not-editable note
 * @param addButtonId   the id of the list's add button
 * @param prompt        what the new-name field suggests
 * @param summaryHeading heading of the list's second column
 * @param section       the {@code components} field, as {@code ComponentReferences} names it
 * @param access        reaches the section from {@code components}
 * @param summary       the second column for an entry that is an object
 * @param initialise    what a newly added entry must have (given the key it was added under)
 */
public record ComponentKind<T>(String noun, String article, String addButtonId, String prompt,
                               String summaryHeading, String section,
                               Function<Components, ComponentSection<T>> access,
                               Function<T, String> summary, BiConsumer<T, String> initialise) {

    /** The heading the section has in the outline: {@code Headers}. */
    public String plural() {
        return capitalised() + "s";
    }

    String capitalised() {
        return Character.toUpperCase(noun.charAt(0)) + noun.substring(1);
    }
}
