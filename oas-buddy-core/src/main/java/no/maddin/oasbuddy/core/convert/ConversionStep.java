package no.maddin.oasbuddy.core.convert;

import com.fasterxml.jackson.databind.node.ObjectNode;
import no.maddin.oasbuddy.core.document.OasVersion;

import java.util.List;

/**
 * Converts a document from one OAS version to the next. {@link OasConverter} chains steps to reach a
 * version no single step goes to.
 *
 * <p>A step edits the tree it is given <em>in place</em>: the converter hands it a copy, so it never
 * sees the user's document. It must keep key order (rename with {@code JsonNodes.renameField}, never
 * remove-then-add), and it must leave the tree declaring {@link #to()} — the converter checks.
 */
public interface ConversionStep {

    OasVersion from();

    OasVersion to();

    /**
     * Rewrites {@code root} from {@link #from()} to {@link #to()}.
     *
     * @return what could not be carried over as it was, in a stable order; empty when the step was
     *         lossless for this document
     */
    List<Conflict> apply(ObjectNode root);
}
