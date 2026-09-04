package com.wurstsoftware.htmltopdf4j.text;

/**
 * Which OpenType features the shaper applies.
 *
 * <p>AWT does not apply kerning or ligatures unless the {@code Font} carries
 * {@link java.awt.font.TextAttribute#KERNING} and
 * {@link java.awt.font.TextAttribute#LIGATURES}, so this is a real choice rather
 * than a formality. {@link #NONE} exists because the subsetter and the metrics
 * tests need glyph ids that a cmap can be checked against, and a ligature glyph
 * has no cmap entry.
 */
public enum ShapingFeatures {
    /** Straight cmap mapping: no substitution, no positioning adjustment. */
    NONE,
    /** Kerning and standard ligatures, as the face's design intends. */
    SHAPED
}
