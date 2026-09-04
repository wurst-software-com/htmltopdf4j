package com.wurstsoftware.htmltopdf4j.text;

/**
 * A single font program: one family at one weight and one style.
 *
 * <p>Two kinds exist, and the difference is whether the bytes travel with the
 * output. A {@link Standard14Face} is referenced by name and drawn with whatever
 * the reader supplies, so a simple Latin Document carries no font payload at
 * all. An {@link EmbeddedFace} is backed by real bytes, which are shaped,
 * subset, and embedded.
 *
 * <p>Every Face is immutable and safe to share across threads.
 */
public sealed interface Face permits EmbeddedFace, Standard14Face {

    /** A name for diagnostics. */
    String name();

    /** The family this Face belongs to, for {@code font-family} matching. */
    String family();

    /** The width of {@code text} at {@code size} points. */
    float measure(String text, float size);

    /** Distance from the baseline to the top of the tallest glyph, at {@code size}. */
    float ascent(float size);

    /** Distance from the baseline to the bottom of the deepest glyph (negative), at {@code size}. */
    float descent(float size);

    /** Whether this Face can draw every character of {@code text} itself. */
    boolean canDisplayAll(String text);

    /** The index of the first character this Face cannot draw, or -1 if it can draw them all. */
    int firstUndisplayable(String text);

    /**
     * The line box for {@code line-height: normal} as a fraction of the em: the
     * height a browser places glyphs on, which any explicit {@code line-height}
     * splits its leading around.
     */
    float normalLineBoxFraction();

    /**
     * The distance from the top of the line box down to the baseline, as a
     * fraction of the em.
     */
    float lineAscentFraction();
}
