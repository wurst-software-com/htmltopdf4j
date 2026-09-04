package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reading the font program an {@code @font-face} rule's {@code src} names. */
class FontFaceSourceTest {

    private static final byte[] PROGRAM = {0x00, 0x01, 0x00, 0x00, 0x11, 0x22};
    private static final byte[] OTHER_PROGRAM = {0x00, 0x01, 0x00, 0x00, 0x33, 0x44};

    @TempDir
    Path directory;

    private Path write(String name, byte[] bytes) {
        try {
            Path path = directory.resolve(name);
            Files.write(path, bytes);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void aUrlIsResolvedAgainstTheBaseDirectory() {
        write("brand.ttf", PROGRAM);

        assertArrayEquals(PROGRAM, FontFaceSource.read("url(brand.ttf)", directory));
    }

    @Test
    void aQuotedUrlReadsTheSameFile() {
        write("brand.ttf", PROGRAM);

        assertArrayEquals(PROGRAM, FontFaceSource.read("url('brand.ttf')", directory));
        assertArrayEquals(PROGRAM, FontFaceSource.read("url(\"brand.ttf\")", directory));
    }

    @Test
    void aFormatHintIsNotPartOfTheSource() {
        write("brand.ttf", PROGRAM);

        assertArrayEquals(
                PROGRAM, FontFaceSource.read("url(brand.ttf) format(\"truetype\")", directory));
    }

    @Test
    void theFirstAlternativeThatCanBeReadWins() {
        write("second.ttf", PROGRAM);

        assertArrayEquals(
                PROGRAM,
                FontFaceSource.read("url(missing.ttf), url(second.ttf) format('truetype')", directory));
    }

    @Test
    void aBase64DataUriNeedsNoFile() {
        String source = "url(data:font/ttf;base64," + Base64.getEncoder().encodeToString(PROGRAM) + ")";

        assertArrayEquals(PROGRAM, FontFaceSource.read(source, null));
    }

    @Test
    void aCommaInsideADataUriDoesNotSplitTheAlternatives() {
        // The base64 payload can contain no comma, but the media-type part can,
        // and splitting there would hand the reader a truncated URI.
        String source = "url(data:font/ttf;charset=utf-8;base64,"
                + Base64.getEncoder().encodeToString(PROGRAM) + ")";

        assertArrayEquals(PROGRAM, FontFaceSource.read(source, null));
    }

    @Test
    void aRemoteUrlIsRefusedRatherThanFetched() {
        assertNull(FontFaceSource.read("url(https://fonts.example.com/brand.woff2)", directory));
        assertNull(FontFaceSource.read("url(http://fonts.example.com/brand.ttf)", directory));
    }

    @Test
    void aRemoteAlternativeIsSkippedInFavourOfALocalOne() {
        write("brand.ttf", PROGRAM);

        assertArrayEquals(
                PROGRAM,
                FontFaceSource.read("url(https://fonts.example.com/brand.woff2), url(brand.ttf)", directory));
    }

    @Test
    void aSourceThatNamesNothingReadableIsNull() {
        assertNull(FontFaceSource.read("url(missing.ttf)", directory));
        assertNull(FontFaceSource.read("", directory));
        assertNull(FontFaceSource.read("nonsense", directory));
    }

    @Test
    void aLocalFamilyThatIsNotInstalledIsNull() {
        assertNull(FontFaceSource.read("local('No Such Family At All')", directory));
    }

    @Test
    void aLocalFamilyThatIsInstalledIsRead() {
        java.util.Map<String, java.util.List<com.wurstsoftware.htmltopdf4j.text.FontLibrary.Entry>> index =
                com.wurstsoftware.htmltopdf4j.text.FontLibrary.index();
        org.junit.jupiter.api.Assumptions.assumeFalse(index.isEmpty(), "no system fonts installed");
        String family = index.values().iterator().next().get(0).family();

        assertNotNull(FontFaceSource.read("local('" + family + "')", directory));
    }

    @Test
    void aNonBase64DataUriIsTakenAsRawBytes() {
        byte[] expected = {0x00, 0x01, 0x00, 0x00, 0x61, 0x62};

        assertArrayEquals(
                expected,
                FontFaceSource.read(
                        "url(data:font/ttf," + new String(expected, StandardCharsets.ISO_8859_1) + ")",
                        null));
    }

    @Test
    void aWoffSourceIsUnwrappedIntoTheSfntTheEmbedderNeeds() {
        byte[] sfnt = systemFont();
        write("brand.woff", com.wurstsoftware.htmltopdf4j.text.WoffFixture.wrap(sfnt));

        byte[] loaded = FontFaceSource.read("url(brand.woff) format('woff')", directory);

        assertNotNull(loaded);
        assertEquals(0x00010000, java.nio.ByteBuffer.wrap(loaded).getInt(0));
        assertArrayEquals(
                com.wurstsoftware.htmltopdf4j.text.WoffFixture.tablesOf(sfnt).stream()
                        .filter(table -> table.tag().equals("head"))
                        .findFirst()
                        .orElseThrow()
                        .data(),
                com.wurstsoftware.htmltopdf4j.text.WoffFixture.tablesOf(loaded).stream()
                        .filter(table -> table.tag().equals("head"))
                        .findFirst()
                        .orElseThrow()
                        .data());
    }

    @Test
    void aWoff2AlternativeIsSkippedForTheNextOneRatherThanLoadedHalfWay() {
        // The format hint is only a hint; a woff2 payload has to be refused on
        // its own signature, or the src chain would stop at a Face nothing can
        // parse and the next alternative would never be tried.
        write("brand.woff2", "wOF2 and then some bytes".getBytes(StandardCharsets.ISO_8859_1));
        write("brand.ttf", PROGRAM);

        assertArrayEquals(
                PROGRAM,
                FontFaceSource.read("url(brand.woff2) format('woff2'), url(brand.ttf)", directory));
    }

    @Test
    void anAlternativeHintedAsAnUnsupportedFormatIsNotEvenRead() {
        // The chain falls through in order and skips what it cannot open: the
        // first alternative is a perfectly good SFNT, but its hint says woff2,
        // so the bytes are never fetched and the next alternative wins.
        write("hinted.bin", PROGRAM);
        write("brand.ttf", OTHER_PROGRAM);

        assertArrayEquals(
                OTHER_PROGRAM,
                FontFaceSource.read("url(hinted.bin) format('woff2'), url(brand.ttf)", directory));
    }

    @Test
    void aTruetypeHintDoesNotStopTheAlternativeBeingRead() {
        write("brand.ttf", PROGRAM);

        assertArrayEquals(
                PROGRAM, FontFaceSource.read("url(brand.ttf) format('truetype')", directory));
    }

    private static byte[] systemFont() {
        try {
            return Files.readAllBytes(com.wurstsoftware.htmltopdf4j.text.TestFonts.available().get(0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
