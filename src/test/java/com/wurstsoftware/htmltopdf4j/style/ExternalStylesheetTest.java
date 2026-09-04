package com.wurstsoftware.htmltopdf4j.style;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wurstsoftware.htmltopdf4j.paint.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stylesheets a Document links to rather than carries.
 *
 * <p>Nothing is fetched: a sheet is read only from under the base directory the
 * caller named, which is the same rule an {@code @font-face} source follows.
 */
class ExternalStylesheetTest {

    @TempDir
    Path directory;

    private static final Color RED = Color.fromRgb255(255, 0, 0);
    private static final Color GREEN = Color.fromRgb255(0, 128, 0);

    private void write(String name, String css) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, css);
    }

    private Color colorOfTarget(String html) {
        return colorOfTarget(html, directory);
    }

    private static Color colorOfTarget(String html, Path baseDirectory) {
        Document document = org.jsoup.Jsoup.parse(html);
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document, baseDirectory));
        Element target = document.selectFirst("#target");
        return cascade.styleOf(target).color();
    }

    @Test
    void aLinkedStylesheetIsLoadedRelativeToTheBaseDirectory() throws IOException {
        write("theme.css", "p { color: red }");

        assertEquals(RED, colorOfTarget(
                "<link rel=stylesheet href=theme.css><p id=target>x</p>"));
    }

    @Test
    void anImportInsideALoadedSheetIsFollowed() throws IOException {
        write("theme.css", "@import url('parts/colors.css');\np { font-size: 20pt }");
        write("parts/colors.css", "p { color: red }");

        assertEquals(RED, colorOfTarget(
                "<link rel=stylesheet href=theme.css><p id=target>x</p>"));
    }

    @Test
    void anImportCycleTerminates() throws IOException {
        write("a.css", "@import 'b.css'; p { color: red }");
        write("b.css", "@import 'a.css';");

        assertEquals(RED, colorOfTarget("<link rel=stylesheet href=a.css><p id=target>x</p>"));
    }

    @Test
    void aLinkedSheetCascadesWhereItAppearsInTheDocument() throws IOException {
        write("theme.css", "p { color: red }");

        // The block comes after the link, so it wins at equal specificity.
        assertEquals(GREEN, colorOfTarget("<link rel=stylesheet href=theme.css>"
                + "<style>p { color: #008000 }</style><p id=target>x</p>"));
        // And the other way round the sheet wins.
        assertEquals(RED, colorOfTarget("<style>p { color: #008000 }</style>"
                + "<link rel=stylesheet href=theme.css><p id=target>x</p>"));
    }

    @Test
    void aTargetOutsideTheBaseDirectoryIsRefused() throws IOException {
        Files.writeString(directory.getParent().resolve("outside.css"), "p { color: red }");
        write("inside/marker.css", "");

        assertEquals(Color.BLACK, colorOfTarget(
                "<link rel=stylesheet href='../outside.css'><p id=target>x</p>",
                directory.resolve("inside")));
    }

    @Test
    void anHttpTargetIsNotFetched() {
        assertEquals(Color.BLACK, colorOfTarget(
                "<link rel=stylesheet href='https://example.invalid/theme.css'><p id=target>x</p>"));
    }

    @Test
    void withNoBaseDirectoryNothingIsLoadedFromDisk() throws IOException {
        write("theme.css", "p { color: red }");

        assertEquals(Color.BLACK, colorOfTarget(
                "<link rel=stylesheet href=theme.css><p id=target>x</p>", null));
    }

    @Test
    void aScreenOnlyLinkIsSkipped() throws IOException {
        write("screen.css", "p { color: red }");

        assertEquals(Color.BLACK, colorOfTarget(
                "<link rel=stylesheet href=screen.css media=screen><p id=target>x</p>"));
    }

    @Test
    void aMissingSheetLeavesTheDocumentUnstyledRatherThanFailing() {
        assertEquals(Color.BLACK, colorOfTarget(
                "<link rel=stylesheet href=absent.css><p id=target>x</p>"));
    }
}
