package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.JsonNodes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * What a conversion cannot express in the target version is not dropped: the keyword is renamed in
 * place to {@code x-oas-buddy-<keyword>} — an extension, which every object that can hold the
 * keyword also accepts — with its value untouched, and reported as a {@link Conflict}. The step
 * going the other way {@link #restore restores} it, so converting there and back gives the original.
 */
final class Kept {

    /** The prefix every keyword set aside during a conversion is kept under. */
    static final String PREFIX = "x-oas-buddy-";

    private Kept() {
    }

    static String name(String keyword) {
        return PREFIX + keyword;
    }

    /**
     * Renames {@code keyword} in {@code parent} to its kept name, in place, and reports it. Does
     * nothing when the keyword is absent. When the kept name is already taken the keyword is left
     * where it is — never overwriting what is there — and the conflict says so.
     *
     * @param location the field names leading to {@code parent}
     * @param hint     what the user can do instead, appended to the resolution
     */
    static void setAside(ObjectNode parent, String keyword, List<String> location, String problem, String hint,
                         List<Conflict> conflicts) {
        if (!parent.has(keyword)) {
            return;
        }
        String kept = name(keyword);
        String resolution = JsonNodes.renameField(parent, keyword, kept)
                ? "kept as " + kept
                : "left as it is, since " + kept + " is already taken";
        conflicts.add(Conflict.at(append(location, keyword), problem,
                hint == null ? resolution : resolution + "; " + hint));
    }

    /** Renames each kept keyword back, in place, unless the keyword itself has appeared meanwhile. */
    static void restore(JsonNode parent, Collection<String> keywords) {
        if (parent instanceof ObjectNode object) {
            keywords.forEach(keyword -> JsonNodes.renameField(object, name(keyword), keyword));
        }
    }

    /**
     * Restores {@code keyword} only when its kept value passes {@code accepts} — for a keyword both
     * directions set aside, in different forms ({@code exclusiveMinimum} is kept as a boolean by
     * 3.0 → 3.1 and as a number by 3.1 → 3.0), so each step takes back only the form its target
     * version reads.
     */
    static void restoreIf(ObjectNode parent, String keyword, Predicate<JsonNode> accepts) {
        JsonNode value = parent.get(name(keyword));
        if (value != null && accepts.test(value)) {
            JsonNodes.renameField(parent, name(keyword), keyword);
        }
    }

    static List<String> append(List<String> location, String... segments) {
        List<String> longer = new ArrayList<>(location);
        longer.addAll(List.of(segments));
        return longer;
    }
}
