package com.wurstsoftware.htmltopdf4j.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The spike that ADR 0002 gates the whole font strategy on: the glyph codes
 * {@code java.awt.Font.layoutGlyphVector} returns must be the glyph ids of the
 * very bytes we embed and subset. If this ever fails, shaped output is silently
 * drawn with the wrong glyphs and the fallback ladder in issue #1 applies.
 *
 * <p>It stays in the suite as a regression test rather than being deleted once
 * green, because the assumption is about the JDK, not about our code.
 */
class GlyphIdentityTest {

    static Stream<Path> systemFaces() {
        return TestFonts.available().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("systemFaces")
    void awtGlyphCodesAreTheEmbeddedFacesGlyphIds(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        EmbeddedFace face = EmbeddedFace.fromBytes(bytes, path.getFileName().toString());

        try (TrueTypeFont reference = new TTFParser().parse(new RandomAccessReadBuffer(bytes))) {
            CmapLookup cmap = reference.getUnicodeCmapLookup();
            assertEquals(
                    reference.getNumberOfGlyphs(),
                    face.glyphCount(),
                    "AWT and the embedded face must agree on how many glyphs exist");

            // Unshaped: every glyph must be exactly what the face's own cmap maps
            // the character to. This is the identity the subsetter depends on.
            String text = "Hello, World! 0123";
            ShapedRun run = face.shape(text, 1000f, Direction.LTR, ShapingFeatures.NONE);
            assertEquals(text.length(), run.glyphs().size());
            for (int i = 0; i < text.length(); i++) {
                assertEquals(
                        cmap.getGlyphId(text.charAt(i)),
                        run.glyphs().get(i).glyphId(),
                        "glyph id for '" + text.charAt(i) + "'");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("systemFaces")
    void unkernedAdvancesMatchTheFacesHorizontalMetrics(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        EmbeddedFace face = EmbeddedFace.fromBytes(bytes, path.getFileName().toString());

        try (TrueTypeFont reference = new TTFParser().parse(new RandomAccessReadBuffer(bytes))) {
            var hmtx = reference.getHorizontalMetrics();
            int upem = reference.getUnitsPerEm();

            ShapedRun run = face.shape("Hamburgefonstiv", 1000f, Direction.LTR, ShapingFeatures.NONE);
            for (Glyph glyph : run.glyphs()) {
                float expected = hmtx.getAdvanceWidth(glyph.glyphId()) * 1000f / upem;
                assertEquals(expected, glyph.advance(), 0.05f, "advance for gid " + glyph.glyphId());
            }
        }
    }

    /**
     * Ligature substitution produces glyph ids that no cmap maps to, so the
     * {@code /ToUnicode} CMap can only be built from the glyph-to-character
     * mapping the shaper reports. This asserts that mapping covers every input
     * character exactly once, with no gaps.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("systemFaces")
    void shapedGlyphsCoverEveryInputCharacter(Path path) throws IOException {
        EmbeddedFace face = EmbeddedFace.fromBytes(Files.readAllBytes(path), path.getFileName().toString());

        String text = "affluent office";
        ShapedRun run = face.shape(text, 12f, Direction.LTR, ShapingFeatures.SHAPED);

        List<Glyph> glyphs = run.glyphs();
        assertEquals(0, glyphs.get(0).charStart());
        assertEquals(text.length(), glyphs.get(glyphs.size() - 1).charEnd());
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            assertTrue(glyph.charEnd() > glyph.charStart(), "every glyph covers at least one char");
            assertEquals(
                    glyph.text(text),
                    text.substring(glyph.charStart(), glyph.charEnd()),
                    "glyph " + i + " reports the characters it covers");
            if (i > 0) {
                assertEquals(
                        glyphs.get(i - 1).charEnd(),
                        glyph.charStart(),
                        "glyph " + i + " continues where glyph " + (i - 1) + " ended");
            }
        }
    }

    /**
     * Not every face has an {@code ffl} ligature — monospace faces deliberately
     * do not — so ligature forming is asserted of the machine, not of each face.
     * What every face must honour is the cluster invariant: shaping may merge
     * glyphs but must never lose a character.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("systemFaces")
    void shapingNeverLosesCharacters(Path path) throws IOException {
        EmbeddedFace face = EmbeddedFace.fromBytes(Files.readAllBytes(path), path.getFileName().toString());
        String text = "ffl fi office";

        ShapedRun unshaped = face.shape(text, 1000f, Direction.LTR, ShapingFeatures.NONE);
        ShapedRun shaped = face.shape(text, 1000f, Direction.LTR, ShapingFeatures.SHAPED);

        assertEquals(text.length(), unshaped.glyphs().size(), "unshaped is one glyph per character");
        assertTrue(
                shaped.glyphs().size() <= unshaped.glyphs().size(),
                "shaping substitutes glyphs, it never invents characters");
        assertEquals(0, shaped.glyphs().get(0).charStart());
        assertEquals(text.length(), shaped.glyphs().get(shaped.glyphs().size() - 1).charEnd());
    }

    /**
     * Ligature substitution has to actually happen somewhere, or ADR 0002's claim
     * that AWT gives us real shaping is empty. AWT applies it only when the Font
     * carries {@code TextAttribute.LIGATURES}, which is the detail this pins.
     */
    @Test
    void atLeastOneSystemFaceFormsLigatures() throws IOException {
        boolean anyLigated = false;
        for (Path path : TestFonts.available()) {
            EmbeddedFace face = EmbeddedFace.fromBytes(Files.readAllBytes(path), path.getFileName().toString());
            ShapedRun shaped = face.shape("ffl", 1000f, Direction.LTR, ShapingFeatures.SHAPED);
            if (shaped.glyphs().size() == 1) {
                anyLigated = true;
                Glyph ligature = shaped.glyphs().get(0);
                assertEquals(0, ligature.charStart());
                assertEquals(3, ligature.charEnd(), "the ligature covers all three characters");
            }
        }
        Assumptions.assumeTrue(anyLigated, "no system face on this machine has an ffl ligature");
    }

    /**
     * Kerning likewise: {@code AV} and {@code To} are the classic pairs, and a
     * kerned face must draw them tighter than the sum of the nominal advances.
     */
    @Test
    void atLeastOneSystemFaceKerns() throws IOException {
        boolean anyKerned = false;
        for (Path path : TestFonts.available()) {
            EmbeddedFace face = EmbeddedFace.fromBytes(Files.readAllBytes(path), path.getFileName().toString());
            for (String pair : new String[] {"AV", "To"}) {
                float unkerned = face.measure(pair, 1000f, Direction.LTR, ShapingFeatures.NONE);
                float kerned = face.measure(pair, 1000f, Direction.LTR, ShapingFeatures.SHAPED);
                assertTrue(kerned <= unkerned, "kerning never widens '" + pair + "' in " + face);
                anyKerned |= kerned < unkerned;
            }
        }
        Assumptions.assumeTrue(anyKerned, "no system face on this machine kerns");
    }

    /** Right-to-left text must come back in visual order, still carrying logical char indices. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("systemFaces")
    void rightToLeftTextIsReorderedVisually(Path path) throws IOException {
        EmbeddedFace face = EmbeddedFace.fromBytes(Files.readAllBytes(path), path.getFileName().toString());
        String hebrew = "שלום";
        Assumptions.assumeTrue(face.canDisplayAll(hebrew), "face must cover Hebrew");

        ShapedRun ltr = face.shape(hebrew, 1000f, Direction.LTR, ShapingFeatures.SHAPED);
        ShapedRun rtl = face.shape(hebrew, 1000f, Direction.RTL, ShapingFeatures.SHAPED);

        assertEquals(ltr.glyphs().size(), rtl.glyphs().size());
        for (int i = 0; i < rtl.glyphs().size(); i++) {
            assertEquals(
                    ltr.glyphs().get(ltr.glyphs().size() - 1 - i).glyphId(),
                    rtl.glyphs().get(i).glyphId(),
                    "RTL glyph " + i + " mirrors the LTR order");
        }
        assertEquals(ltr.advance(), rtl.advance(), 0.01f);
    }
}
