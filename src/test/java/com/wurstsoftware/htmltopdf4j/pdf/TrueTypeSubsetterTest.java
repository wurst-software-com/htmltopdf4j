package com.wurstsoftware.htmltopdf4j.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.text.Direction;
import com.wurstsoftware.htmltopdf4j.text.EmbeddedFace;
import com.wurstsoftware.htmltopdf4j.text.Glyph;
import com.wurstsoftware.htmltopdf4j.text.ShapedRun;
import com.wurstsoftware.htmltopdf4j.text.ShapingFeatures;
import com.wurstsoftware.htmltopdf4j.text.TestFonts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Subsetting retains glyph ids rather than renumbering them. That is the whole
 * reason the writer is hand-ported: it keeps the cmap, the {@code /W} widths,
 * the {@code /ToUnicode} CMap and {@code /CIDToGIDMap /Identity} valid against
 * the subset without any of them being rewritten.
 */
class TrueTypeSubsetterTest {

    static Stream<Path> systemFaces() {
        return TestFonts.available().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("systemFaces")
    void subsetKeepsGlyphIdsAndDropsUnusedOutlines(Path path) throws IOException {
        byte[] original = Files.readAllBytes(path);
        EmbeddedFace face = EmbeddedFace.fromBytes(original, path.getFileName().toString());
        SortedSet<Integer> used = glyphsOf(face, "Hi");

        byte[] subset = TrueTypeSubsetter.subset(original, used);
        assertNotNull(subset, "a glyf-based face must be subsettable");
        assertTrue(subset.length < original.length, "subsetting must shrink the program");

        try (TrueTypeFont before = parse(original);
                TrueTypeFont after = parse(subset)) {
            assertEquals(
                    before.getNumberOfGlyphs(),
                    after.getNumberOfGlyphs(),
                    "glyph ids are retained, so the glyph count is unchanged");
            assertEquals(before.getUnitsPerEm(), after.getUnitsPerEm());

            int[] kept = outlineLengths(subset);
            int[] all = outlineLengths(original);
            for (int gid : used) {
                assertTrue(kept[gid] > 0 || all[gid] == 0, "kept glyph " + gid + " must keep its outline");
            }
            long dropped = 0;
            for (int gid = 1; gid < before.getNumberOfGlyphs(); gid++) {
                if (!used.contains(gid) && all[gid] > 0 && kept[gid] == 0) {
                    dropped++;
                }
            }
            assertTrue(dropped > 0, "unused outlines must actually be dropped");
        }
    }

    /**
     * The advance widths the {@code /W} array is built from come from {@code hmtx},
     * which is copied verbatim. If they moved, every glyph would be drawn at the
     * wrong pitch.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("systemFaces")
    void subsetPreservesMetricsForEveryGlyph(Path path) throws IOException {
        byte[] original = Files.readAllBytes(path);
        EmbeddedFace face = EmbeddedFace.fromBytes(original, path.getFileName().toString());
        byte[] subset = TrueTypeSubsetter.subset(original, glyphsOf(face, "The quick brown fox"));

        try (TrueTypeFont before = parse(original);
                TrueTypeFont after = parse(subset)) {
            for (int gid = 0; gid < before.getNumberOfGlyphs(); gid++) {
                assertEquals(
                        before.getHorizontalMetrics().getAdvanceWidth(gid),
                        after.getHorizontalMetrics().getAdvanceWidth(gid),
                        "advance width of gid " + gid);
            }
        }
    }

    /** A Face built from the subset must shape identically to one built from the original. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("systemFaces")
    void subsetShapesTheKeptTextIdentically(Path path) throws IOException {
        byte[] original = Files.readAllBytes(path);
        EmbeddedFace face = EmbeddedFace.fromBytes(original, path.getFileName().toString());
        String text = "Hamburgefonstiv";

        byte[] subset = TrueTypeSubsetter.subset(original, glyphsOf(face, text));
        EmbeddedFace subsetFace = EmbeddedFace.fromBytes(subset, "subset");

        ShapedRun expected = face.shape(text, 12f, Direction.LTR, ShapingFeatures.SHAPED);
        ShapedRun actual = subsetFace.shape(text, 12f, Direction.LTR, ShapingFeatures.SHAPED);
        assertEquals(expected.glyphs(), actual.glyphs());
        assertEquals(expected.advance(), actual.advance(), 0.001f);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("systemFaces")
    void subsettingIsDeterministic(Path path) throws IOException {
        byte[] original = Files.readAllBytes(path);
        SortedSet<Integer> used = new TreeSet<>(List.of(0, 3, 40, 41));

        assertArrayEquals(
                TrueTypeSubsetter.subset(original, used), TrueTypeSubsetter.subset(original, used));
    }

    private static SortedSet<Integer> glyphsOf(EmbeddedFace face, String text) {
        SortedSet<Integer> gids = new TreeSet<>();
        for (Glyph glyph : face.shape(text, 12f, Direction.LTR, ShapingFeatures.SHAPED).glyphs()) {
            gids.add(glyph.glyphId());
        }
        return gids;
    }

    /**
     * The byte length of each glyph's outline, read from {@code loca}. A dropped
     * glyph is exactly a zero-length entry, which is the subsetter's contract.
     */
    private static int[] outlineLengths(byte[] font) throws IOException {
        try (TrueTypeFont ttf = parse(font)) {
            int glyphCount = ttf.getNumberOfGlyphs();
            long[] offsets = ttf.getIndexToLocation().getOffsets();
            int[] lengths = new int[glyphCount];
            for (int gid = 0; gid < glyphCount; gid++) {
                lengths[gid] = (int) (offsets[gid + 1] - offsets[gid]);
            }
            return lengths;
        }
    }

    private static TrueTypeFont parse(byte[] bytes) throws IOException {
        return new TTFParser().parse(new RandomAccessReadBuffer(bytes));
    }
}
