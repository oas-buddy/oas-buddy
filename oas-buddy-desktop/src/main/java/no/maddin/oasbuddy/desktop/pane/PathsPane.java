package no.maddin.oasbuddy.desktop.pane;

import no.maddin.oasbuddy.core.document.OasDocument;
import no.maddin.oasbuddy.core.model.Paths;
import javafx.scene.Node;

import java.util.function.Consumer;

public final class PathsPane {

    private static final NameList LIST = new NameList(
            "Paths", "paths-list", "Path", "No paths yet.", "/example/{id}", "Add path");

    private PathsPane() {
    }

    /**
     * @param onStructureChanged run after a path is added, so the outline picks it up
     * @param onRemovePath       asked to remove the named path; the pane only reports the request,
     *                           it never removes anything itself
     */
    public static Node build(OasDocument document, Runnable onStructureChanged, Consumer<String> onRemovePath) {
        Paths paths = document.getPaths();
        return LIST.build(paths.pathNames(), path -> {
            paths.addPath(path);
            onStructureChanged.run();
        }, onRemovePath);
    }
}
