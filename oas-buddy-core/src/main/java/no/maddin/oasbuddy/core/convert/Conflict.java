package no.maddin.oasbuddy.core.convert;

import java.util.List;
import java.util.Objects;

/**
 * One thing a conversion could not carry over as it was. The conversion still goes ahead; the user
 * is shown every conflict and decides whether to keep the result.
 *
 * @param location   where in the document, rendered for a person (see {@link Location#render})
 * @param problem    what the target version cannot express
 * @param resolution what the conversion did about it instead
 */
public record Conflict(String location, String problem, String resolution) {

    public Conflict {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(resolution, "resolution");
    }

    /** A conflict at the location given as the field names leading to it from the root. */
    public static Conflict at(List<String> location, String problem, String resolution) {
        return new Conflict(Location.render(location), problem, resolution);
    }
}
