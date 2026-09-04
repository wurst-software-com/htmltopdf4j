package com.wurstsoftware.htmltopdf4j.text;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.junit.jupiter.api.Test;

/** Unwrapping a WOFF1 container back into the bare SFNT the rest of the stack reads. */
class WoffTest {

    private static byte[] systemFont() {
        try {
            return Files.readAllBytes(TestFonts.available().get(0));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void aWoffContainerUnwrapsToAFaceTheParserAccepts() throws IOException {
        byte[] sfnt = systemFont();

        byte[] decoded = Woff.decode(WoffFixture.wrap(sfnt));

        assertNotNull(decoded);
        try (TrueTypeFont original = parse(sfnt); TrueTypeFont round = parse(decoded)) {
            assertEquals(original.getNumberOfGlyphs(), round.getNumberOfGlyphs());
            assertEquals(original.getNaming().getFontFamily(), round.getNaming().getFontFamily());
        }
    }

    @Test
    void everyTableSurvivesTheRoundTripByteForByte() throws IOException {
        byte[] sfnt = systemFont();

        byte[] decoded = Woff.decode(WoffFixture.wrap(sfnt));

        for (String tag : List.of("glyf", "loca", "cmap", "head", "hhea", "hmtx", "maxp")) {
            byte[] before = tableOf(sfnt, tag);
            if (before != null) {
                assertArrayEquals(before, tableOf(decoded, tag), tag + " changed");
            }
        }
    }

    @Test
    void anUncompressedTableIsCopiedRatherThanInflated() throws IOException {
        // A table that deflates no smaller is stored verbatim, and the decoder
        // has to notice that from the lengths rather than trying to inflate it.
        byte[] sfnt = systemFont();

        byte[] decoded = Woff.decode(WoffFixture.wrap(sfnt, false));

        assertNotNull(decoded);
        assertArrayEquals(tableOf(sfnt, "head"), tableOf(decoded, "head"));
    }

    @Test
    void aBareSfntIsHandedBackUntouched() {
        byte[] sfnt = systemFont();

        assertArrayEquals(sfnt, Woff.decode(sfnt));
    }

    @Test
    void aWoff2ContainerIsRefusedRatherThanMisread() {
        byte[] woff2 = "wOF2".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        assertNull(Woff.decode(woff2));
    }

    @Test
    void aTruncatedContainerIsRefusedRatherThanThrowing() throws IOException {
        byte[] woff = WoffFixture.wrap(systemFont());

        assertNull(Woff.decode(java.util.Arrays.copyOf(woff, woff.length / 2)));
    }

    @Test
    void nothingInIsNothingOut() {
        assertNull(Woff.decode(null));
        assertNull(Woff.decode(new byte[0]));
    }

    @Test
    void aContainerOfNoTablesIsRefused() {
        ByteBuffer header = ByteBuffer.allocate(44);
        header.put("wOFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(0x00010000);
        header.putInt(44);
        header.putShort((short) 0);

        assertNull(Woff.decode(header.array()));
    }

    @Test
    void theSfntFlavourTheContainerDeclaresIsTheOneWritten() throws IOException {
        byte[] decoded = Woff.decode(WoffFixture.wrap(systemFont()));

        assertEquals(0x00010000, ByteBuffer.wrap(decoded).getInt(0));
    }

    @Test
    void theRebuiltDirectoryIsSortedByTagAsTheFormatRequires() throws IOException {
        byte[] decoded = Woff.decode(WoffFixture.wrap(systemFont()));

        ByteBuffer buffer = ByteBuffer.wrap(decoded);
        int tables = buffer.getShort(4) & 0xFFFF;
        long previous = -1;
        for (int i = 0; i < tables; i++) {
            long tag = buffer.getInt(12 + i * 16) & 0xFFFFFFFFL;
            assertTrue(tag > previous, "directory is not ascending");
            previous = tag;
        }
    }

    private static byte[] tableOf(byte[] sfnt, String tag) {
        return WoffFixture.tablesOf(sfnt).stream()
                .filter(table -> table.tag().equals(tag))
                .map(WoffFixture.Table::data)
                .findFirst()
                .orElse(null);
    }

    private static TrueTypeFont parse(byte[] sfnt) throws IOException {
        return new TTFParser(true).parse(new org.apache.pdfbox.io.RandomAccessReadBuffer(sfnt));
    }
}
