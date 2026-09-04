package com.wurstsoftware.htmltopdf4j.text;

/**
 * Helvetica, referenced rather than embedded.
 *
 * <p>The advance widths of the standard-14 fonts are a published table, so text
 * can be measured exactly for the font that will actually be drawn, with no font
 * file to read and nothing to embed. Characters outside WinAnsi fall back to the
 * width of an average lowercase glyph: that keeps measurement stable without
 * pretending to metrics we do not have.
 *
 * <p>Only Helvetica is offered. The other thirteen would each need their own
 * width table, and the engine has never defaulted to them.
 */
public final class Standard14Face implements Face {

    /** Adobe AFM advances for Helvetica, {@code 0x20}–{@code 0x7E}, in 1/1000 em. */
    private static final short[] ASCII_WIDTHS = {
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
    };

    /** The width of an average lowercase glyph, used for anything off the table. */
    private static final int FALLBACK_WIDTH = 556;

    /**
     * The historic line-box heuristics. Helvetica has no parsed face to measure,
     * and these are what font-less Documents have always been laid out with — in
     * the Parity harness they also track the reference renderer's own fallback
     * better than real Arial metrics do.
     */
    private static final float LINE_ASCENT_FRACTION = 0.8f;

    private static final float NORMAL_LINE_BOX_FRACTION = 1.35f;

    public static final Standard14Face HELVETICA = new Standard14Face();

    private Standard14Face() {}

    @Override
    public String name() {
        return "Helvetica";
    }

    @Override
    public String family() {
        return "Helvetica";
    }

    /** Only regular Helvetica is offered, so a bold request is always synthesised. */
    @Override
    public boolean bold() {
        return false;
    }

    /** The name the PDF {@code /BaseFont} entry references. */
    public String baseFontName() {
        return "Helvetica";
    }

    @Override
    public float measure(String text, float size) {
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            units += advance(text.charAt(i));
        }
        return units * size / 1000f;
    }

    /** The advance of one character in 1/1000 em. */
    static int advance(char ch) {
        if (ch >= 0x20 && ch <= 0x7E) {
            return ASCII_WIDTHS[ch - 0x20];
        }
        // A non-breaking space measures like a space; everything else we cannot
        // look up gets the average width rather than zero.
        return ch == ' ' ? ASCII_WIDTHS[0] : FALLBACK_WIDTH;
    }

    @Override
    public float ascent(float size) {
        return LINE_ASCENT_FRACTION * size;
    }

    @Override
    public float descent(float size) {
        return (LINE_ASCENT_FRACTION - NORMAL_LINE_BOX_FRACTION) * size;
    }

    /**
     * Helvetica is drawn by the reader from its own font, so a character it
     * cannot draw is not something we can detect — and not something a fallback
     * chain could fix, since nothing here is embedded. Characters outside WinAnsi
     * degrade in the writer instead.
     */
    @Override
    public boolean canDisplayAll(String text) {
        return firstUndisplayable(text) < 0;
    }

    @Override
    public int firstUndisplayable(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (WinAnsiEncoder.encode(text.charAt(i)) < 0) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public float normalLineBoxFraction() {
        return NORMAL_LINE_BOX_FRACTION;
    }

    @Override
    public float lineAscentFraction() {
        return LINE_ASCENT_FRACTION;
    }

    @Override
    public String toString() {
        return "Face[Helvetica, not embedded]";
    }
}
