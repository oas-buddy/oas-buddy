package no.maddin.oasbuddy.desktop;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the one value in the Linux desktop entry that duplicates a Java name.
 *
 * <p>JavaFX derives a window's {@code WM_CLASS} from the {@link javafx.application.Application}
 * subclass, and {@code StartupWMClass} in the desktop entry has to repeat it for a shell to match
 * the running window to the launcher. Nothing connects the two: renaming or moving {@link MainApp}
 * compiles, packages and starts exactly as before, and the only symptom is a taskbar entry that
 * falls back to a generic icon and window title.
 *
 * <p>There are two entries: jpackage's, for the DEB and RPM, and the Arch package's, which the
 * app-image it repackages does not contain. Both are packaging input rather than classpath
 * resources, so they are read from the source tree — see {@code jpackage.resourceDir} in the POM
 * and {@code src/main/aur/PKGBUILD}.
 */
class LinuxDesktopEntryTest {

    @ParameterizedTest
    @ValueSource(strings = {"src/main/jpackage/linux/oas-buddy.desktop", "src/main/aur/oas-buddy.desktop"})
    void startupWmClassNamesTheApplicationClass(String relativePath) throws IOException {
        List<String> values = Files.readAllLines(desktopEntry(relativePath)).stream()
                .filter(line -> line.startsWith("StartupWMClass="))
                .map(line -> line.substring("StartupWMClass=".length()))
                .toList();

        assertEquals(1, values.size(), "expected exactly one StartupWMClass in " + relativePath);
        assertEquals(MainApp.class.getName(), values.getFirst(),
                "StartupWMClass must match the WM_CLASS JavaFX derives from the Application subclass");
    }

    /**
     * Surefire runs with the module directory as the working directory; an IDE may use the repo
     * root instead, so both are tried before giving up.
     */
    private static Path desktopEntry(String relativePath) {
        Path fromModule = Path.of(relativePath);
        Path candidate = Files.exists(fromModule) ? fromModule : Path.of("oas-buddy-desktop", relativePath);
        assertTrue(Files.exists(candidate), "cannot find " + relativePath + " from " + Path.of("").toAbsolutePath());
        return candidate;
    }
}
