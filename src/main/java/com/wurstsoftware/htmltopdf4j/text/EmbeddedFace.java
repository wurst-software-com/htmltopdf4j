package com.wurstsoftware.htmltopdf4j.text;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fontbox.ttf.HeaderTable;
import org.apache.fontbox.ttf.HorizontalHeaderTable;
import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.PostScriptTable;
import org.apache.fontbox.ttf.HorizontalMetricsTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

/**
 * A Face backed by real bytes: the font program is shaped with, subset, and
 * embedded in the output.
 *
 * <p>A Face is immutable and safe to share across threads. Its one piece of
 * mutable state is the Shaped run cache, which is a {@link ConcurrentHashMap}
 * and observationally pure: a hit and a miss return equal runs.
 *
 * <p>Faces are always built with {@link Font#createFont} from the exact bytes
 * that get embedded. ADR 0002 depends on this: a logical font would return glyph
 * codes belonging to some other font program, and the subsetter would then keep
 * the wrong outlines. {@code GlyphIdentityTest} guards the assumption.
 */
public final class EmbeddedFace implements Face {

    /**
     * Shaping is linear in font size, so runs are shaped once at this size and
     * scaled, which lets one cache entry serve every size the Document uses.
     */
    private static final float SHAPING_EM = 1000f;

    /**
     * Antialiasing off, fractional metrics on: we want the design metrics of the
     * outline, not metrics rounded to some device's pixel grid.
     */
    private static final FontRenderContext FRC = new FontRenderContext(null, false, true);

    private final byte[] bytes;
    private final String name;
    private final Font plain;
    private final Font shaped;
    private final int unitsPerEm;
    private final int glyphCount;
    private final boolean bold;
    private final int[] advanceWidths;
    private final int ascender;
    private final int descender;
    private final int lineGap;
    private final PdfFontDescriptor descriptor;
    private final Map<ShapeKey, ShapedRun> runCache = new ConcurrentHashMap<>();

    private record ShapeKey(String text, Direction direction, ShapingFeatures features) {}

    private EmbeddedFace(byte[] bytes, String name, Font plain, TrueTypeFont metrics) throws IOException {
        this.bytes = bytes;
        this.name = name;
        this.plain = plain;
        this.shaped = plain.deriveFont(
                Map.of(
                        TextAttribute.KERNING, TextAttribute.KERNING_ON,
                        TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON));
        this.unitsPerEm = metrics.getUnitsPerEm();
        this.glyphCount = metrics.getNumberOfGlyphs();
        // `head.macStyle` bit 0 is the font program's own claim to be bold,
        // which is what decides whether the writer has to fake it.
        this.bold = (metrics.getHeader().getMacStyle() & 1) != 0;

        HorizontalMetricsTable hmtx = metrics.getHorizontalMetrics();
        this.advanceWidths = new int[glyphCount];
        for (int gid = 0; gid < glyphCount; gid++) {
            this.advanceWidths[gid] = hmtx.getAdvanceWidth(gid);
        }

        HorizontalHeaderTable hhea = metrics.getHorizontalHeader();
        this.ascender = hhea.getAscender();
        this.descender = hhea.getDescender();
        this.lineGap = hhea.getLineGap();
        this.descriptor = PdfFontDescriptor.of(metrics, unitsPerEm, ascender, descender);
    }

    /** The metrics the PDF {@code /FontDescriptor} needs, in a 1000-unit em. */
    public PdfFontDescriptor descriptor() {
        return descriptor;
    }

    /**
     * The line box for {@code line-height: normal}, as a fraction of the em: the
     * face's own ascent plus descent. The {@code hhea} line gap is deliberately
     * left out — browsers do not add it to the line box, they distribute explicit
     * leading around it.
     */
    @Override
    public float normalLineBoxFraction() {
        return (float) (ascender - descender) / unitsPerEm;
    }

    /**
     * The face's real ascender, so glyphs sit on the baseline a browser would use.
     * Clamped against a face that reports something degenerate.
     */
    @Override
    public float lineAscentFraction() {
        return Math.clamp((float) ascender / unitsPerEm, 0.6f, 1.2f);
    }

    /**
     * The glyph data needed to embed this Face as a Type0/CIDFontType2 composite:
     * the natural advance of every glyph the Document uses, for {@code /W}, and
     * the characters each glyph stands for, for {@code /ToUnicode}.
     *
     * <p>The advances recorded are the natural {@code hmtx} ones, not the shaped
     * ones. A viewer applies {@code /W} on its own, so kerning is reproduced by
     * the writer as {@code TJ} adjustments rather than by lying about the widths.
     */
    public CidLayout cidLayout(Iterable<String> texts) {
        java.util.TreeMap<Integer, Integer> widths = new java.util.TreeMap<>();
        java.util.TreeMap<Integer, String> toUnicode = new java.util.TreeMap<>();
        for (String text : texts) {
            for (Glyph glyph : shape(text, SHAPING_EM, Direction.LTR, ShapingFeatures.SHAPED).glyphs()) {
                widths.computeIfAbsent(
                        glyph.glyphId(), gid -> Math.round(advanceWidth(gid) * 1000f / unitsPerEm));
                toUnicode.putIfAbsent(glyph.glyphId(), glyph.text(text));
            }
        }
        return new CidLayout(widths, toUnicode);
    }

    /**
     * Reads a Face from a TrueType or OpenType font program.
     *
     * @param bytes the font program, retained verbatim for embedding
     * @param name a human-readable name used only in diagnostics
     */
    public static EmbeddedFace fromBytes(byte[] bytes, String name) {
        Font awt;
        try {
            awt = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes));
        } catch (java.awt.FontFormatException | IOException e) {
            throw new IllegalArgumentException("not a usable font program: " + name, e);
        }
        // The metrics tables are read once and copied out, so the Face holds no
        // parser state and needs no locking to be shared between threads.
        try (TrueTypeFont metrics = new TTFParser().parse(new RandomAccessReadBuffer(bytes))) {
            return new EmbeddedFace(bytes.clone(), name, awt.deriveFont(SHAPING_EM), metrics);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read font tables of " + name, e);
        }
    }

    @Override
    public boolean bold() {
        return bold;
    }

    /** The font program, for embedding or subsetting. */
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public String name() {
        return name;
    }

    /** The family name the font program declares, for {@code font-family} matching. */
    @Override
    public String family() {
        return plain.getFamily(java.util.Locale.ROOT);
    }

    public int glyphCount() {
        return glyphCount;
    }

    public int unitsPerEm() {
        return unitsPerEm;
    }

    /** The nominal advance of a glyph in font design units, as the {@code /W} array reports it. */
    public int advanceWidth(int glyphId) {
        return glyphId >= 0 && glyphId < advanceWidths.length ? advanceWidths[glyphId] : 0;
    }

    /** Distance from the baseline to the top of the tallest glyph, in points at {@code size}. */
    @Override
    public float ascent(float size) {
        return ascender * size / unitsPerEm;
    }

    /** Distance from the baseline to the bottom of the deepest glyph (negative), at {@code size}. */
    @Override
    public float descent(float size) {
        return descender * size / unitsPerEm;
    }

    /** The face's own recommended distance between baselines, in points at {@code size}. */
    public float lineHeight(float size) {
        return (ascender - descender + lineGap) * size / unitsPerEm;
    }

    /** Whether this Face can draw every character of {@code text} itself. */
    @Override
    public boolean canDisplayAll(String text) {
        return plain.canDisplayUpTo(text) == -1;
    }

    /** The index of the first character this Face cannot draw, or -1 if it can draw them all. */
    @Override
    public int firstUndisplayable(String text) {
        return plain.canDisplayUpTo(text);
    }

    /** The width of {@code text} at {@code size}, in points, shaped left to right. */
    @Override
    public float measure(String text, float size) {
        return measure(text, size, Direction.LTR, ShapingFeatures.SHAPED);
    }

    /** The width of {@code text} at {@code size}, in points. */
    public float measure(String text, float size, Direction direction, ShapingFeatures features) {
        return shape(text, size, direction, features).advance();
    }

    /**
     * Shapes {@code text} into positioned glyphs at {@code size} points.
     *
     * <p>{@code direction} decides visual order only; the caller has already
     * split mixed-direction text into single-direction runs. Glyphs come back in
     * visual order, each still carrying the logical character range it covers.
     */
    public ShapedRun shape(String text, float size, Direction direction, ShapingFeatures features) {
        if (text.isEmpty()) {
            return new ShapedRun(List.of(), 0f);
        }
        ShapedRun atEm = runCache.computeIfAbsent(
                new ShapeKey(text, direction, features), key -> layout(key.text(), key.direction(), key.features()));
        if (size == SHAPING_EM) {
            return atEm;
        }
        float factor = size / SHAPING_EM;
        List<Glyph> scaled = new ArrayList<>(atEm.glyphs().size());
        for (Glyph glyph : atEm.glyphs()) {
            scaled.add(glyph.scaled(factor));
        }
        return new ShapedRun(scaled, atEm.advance() * factor);
    }

    private ShapedRun layout(String text, Direction direction, ShapingFeatures features) {
        char[] chars = text.toCharArray();
        Font font = features == ShapingFeatures.SHAPED ? shaped : plain;
        int flags = direction.isRtl() ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT;
        GlyphVector vector = font.layoutGlyphVector(FRC, chars, 0, chars.length, flags);

        int count = vector.getNumGlyphs();
        int[] clusterEnd = clusterEnds(vector, count, chars.length);

        List<Glyph> glyphs = new ArrayList<>(count);
        float pen = (float) vector.getGlyphPosition(0).getX();
        for (int i = 0; i < count; i++) {
            float next = (float) vector.getGlyphPosition(i + 1).getX();
            int start = vector.getGlyphCharIndex(i);
            glyphs.add(new Glyph(vector.getGlyphCode(i), next - pen, start, clusterEnd[i]));
            pen = next;
        }
        return new ShapedRun(glyphs, (float) vector.getGlyphPosition(count).getX());
    }

    /**
     * Where each glyph's cluster ends in logical character order.
     *
     * <p>AWT reports only the first character of a cluster, so a ligature's extra
     * characters have to be recovered: a cluster runs until the next character
     * some glyph claims as its start. Sorting the claimed starts gives that in
     * one pass, and works for RTL too, where visual and logical order differ.
     */
    private static int[] clusterEnds(GlyphVector vector, int count, int length) {
        int[] starts = new int[count];
        for (int i = 0; i < count; i++) {
            starts[i] = vector.getGlyphCharIndex(i);
        }
        int[] ascending = starts.clone();
        java.util.Arrays.sort(ascending);

        int[] ends = new int[count];
        for (int i = 0; i < count; i++) {
            int position = java.util.Arrays.binarySearch(ascending, starts[i]);
            int next = position + 1;
            // Skip duplicates: two glyphs on one character (a base and its mark)
            // share a start and therefore share the cluster's end.
            while (next < count && ascending[next] == starts[i]) {
                next++;
            }
            ends[i] = next < count ? ascending[next] : length;
        }
        return ends;
    }

    @Override
    public String toString() {
        return "Face[" + name + ", embedded]";
    }
}
