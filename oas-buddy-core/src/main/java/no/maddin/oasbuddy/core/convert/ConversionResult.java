package no.maddin.oasbuddy.core.convert;

import no.maddin.oasbuddy.core.document.OasDocument;

import java.util.List;
import java.util.Objects;

/**
 * A converted document and everything the conversion could not carry over as it was. The original
 * document is untouched whatever the conflicts are; the caller decides whether to adopt this one.
 *
 * @param converted the new document, in the original's format
 * @param conflicts every step's conflicts, in the order the steps ran
 */
public record ConversionResult(OasDocument converted, List<Conflict> conflicts) {

    public ConversionResult {
        Objects.requireNonNull(converted, "converted");
        conflicts = List.copyOf(conflicts);
    }

    /** {@code true} when nothing was lost or moved aside. */
    public boolean isLossless() {
        return conflicts.isEmpty();
    }
}
