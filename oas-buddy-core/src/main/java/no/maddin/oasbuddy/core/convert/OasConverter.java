package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.document.OasVersion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Converts a document to another OAS version by chaining {@link ConversionStep}s, e.g. 2.0 → 3.0 →
 * 3.1 when no single step goes from 2.0 to 3.1.
 *
 * <p>The conversion works on a deep copy, so the original document is never touched — not even when
 * a step fails halfway. A deep copy keeps Jackson's insertion order, so every key a step leaves
 * alone stays where it was.
 *
 * <p>Swagger 2.0 is import-only: no step may convert <em>to</em> it, and the constructor refuses one
 * that claims to.
 */
public final class OasConverter {

    private final Map<OasVersion, List<ConversionStep>> stepsFrom = new EnumMap<>(OasVersion.class);

    public OasConverter(List<? extends ConversionStep> steps) {
        for (ConversionStep step : steps) {
            if (step.to() == OasVersion.V2_0) {
                throw new IllegalArgumentException("No conversion to Swagger 2.0 exists: " + describe(step));
            }
            if (step.from() == step.to()) {
                throw new IllegalArgumentException("A step must change the version: " + describe(step));
            }
            List<ConversionStep> siblings = stepsFrom.computeIfAbsent(step.from(), v -> new ArrayList<>());
            if (siblings.stream().anyMatch(other -> other.to() == step.to())) {
                throw new IllegalArgumentException("Two steps convert " + step.from() + " to " + step.to());
            }
            siblings.add(step);
        }
    }

    /** The converter with every step this editor ships. */
    public static OasConverter standard() {
        return new OasConverter(List.of(new Oas30To31(), new Oas31To30()));
    }

    /** The versions a document of version {@code from} can be converted to, not counting itself. */
    public Set<OasVersion> targets(OasVersion from) {
        Set<OasVersion> targets = EnumSet.noneOf(OasVersion.class);
        for (OasVersion candidate : OasVersion.values()) {
            if (candidate != from && route(from, candidate).isPresent()) {
                targets.add(candidate);
            }
        }
        return targets;
    }

    /**
     * Converts a copy of {@code document} to {@code target}. Converting to the version the document
     * already declares returns an unchanged copy with no conflicts.
     *
     * @throws IllegalArgumentException when the document declares no version this editor knows, or
     *                                  there is no chain of steps to {@code target}
     * @throws IllegalStateException    when a step leaves the document declaring a version other than
     *                                  the one it promised — a bug in that step
     */
    public ConversionResult convert(OasDocument document, OasVersion target) {
        OasVersion source = document.getVersion().orElseThrow(() -> new IllegalArgumentException(
                "The document declares no OAS version this editor knows"));
        List<ConversionStep> route = route(source, target).orElseThrow(() -> new IllegalArgumentException(
                "No conversion from " + source + " to " + target + " exists"));

        ObjectNode copy = document.getRoot().deepCopy();
        List<Conflict> conflicts = new ArrayList<>();
        for (ConversionStep step : route) {
            conflicts.addAll(step.apply(copy));
            Optional<OasVersion> reached = OasVersion.detect(copy);
            if (reached.isEmpty() || reached.get() != step.to()) {
                throw new IllegalStateException(describe(step) + " left the document declaring "
                        + reached.map(OasVersion::name).orElse("no known version"));
            }
        }
        return new ConversionResult(OasDocument.wrap(copy, document.getFormat()), conflicts);
    }

    /** The shortest chain of steps from {@code from} to {@code to}; empty list when they are equal. */
    private Optional<List<ConversionStep>> route(OasVersion from, OasVersion to) {
        Map<OasVersion, ConversionStep> arrivedBy = new EnumMap<>(OasVersion.class);
        Set<OasVersion> visited = EnumSet.of(from);
        Deque<OasVersion> queue = new ArrayDeque<>(List.of(from));
        while (!queue.isEmpty()) {
            OasVersion current = queue.removeFirst();
            if (current == to) {
                List<ConversionStep> route = new ArrayList<>();
                for (OasVersion v = to; v != from; v = arrivedBy.get(v).from()) {
                    route.add(arrivedBy.get(v));
                }
                Collections.reverse(route);
                return Optional.of(route);
            }
            for (ConversionStep step : stepsFrom.getOrDefault(current, List.of())) {
                if (visited.add(step.to())) {
                    arrivedBy.put(step.to(), step);
                    queue.addLast(step.to());
                }
            }
        }
        return Optional.empty();
    }

    private static String describe(ConversionStep step) {
        return step.getClass().getSimpleName() + " (" + step.from() + " → " + step.to() + ")";
    }
}
