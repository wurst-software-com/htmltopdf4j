package com.wurstsoftware.htmltopdf4j.text;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;

/**
 * The font programs on this machine that tests may shape with.
 *
 * <p>The reference engine tests against system fonts too, so a machine with no
 * usable fonts skips rather than fails: it cannot tell us anything about Parity
 * either way.
 */
final class TestFaces {

    private static final List<Path> ROOTS =
            List.of(
                    Path.of("/usr/share/fonts"),
                    Path.of("/usr/local/share/fonts"),
                    Path.of(System.getProperty("user.home"), ".fonts"));

    private TestFaces() {}

    /** A handful of distinct system faces, enough to catch a format-specific assumption. */
    static List<Path> available() {
        List<Path> faces = scan();
        Assumptions.assumeFalse(faces.isEmpty(), "no system fonts to test against");
        return faces;
    }

    private static List<Path> scan() {
        return ROOTS.stream()
                .filter(Files::isDirectory)
                .flatMap(TestFaces::walk)
                .filter(path -> path.toString().endsWith(".ttf"))
                // Variable fonts expose an interpolated instance to AWT whose
                // metrics need not match the default instance in the file, so
                // they are not a fair test of the identity assumption.
                .filter(path -> !path.getFileName().toString().contains("["))
                .sorted()
                .limit(6)
                .toList();
    }

    private static Stream<Path> walk(Path root) {
        try {
            return Files.walk(root).filter(Files::isRegularFile).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
