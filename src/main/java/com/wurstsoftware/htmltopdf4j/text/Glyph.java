package com.wurstsoftware.htmltopdf4j.text;

/**
 * One drawable shape in a {@link Face}.
 *
 * <p>{@code advance} is the shaped advance, so it already carries any kerning
 * the shaper applied. The PDF writer compares it against the face's nominal
 * advance for the same glyph id to recover the kern it must emit as a {@code TJ}
 * adjustment, since the viewer applies the nominal width from the {@code /W}
 * array on its own.
 *
 * @param glyphId the id of the glyph within the face, identical in the embedded
 *     face and in the subset produced from it
 * @param advance how far the pen moves after drawing it, in points at the size
 *     the run was shaped at
 * @param charStart index of the first character in the shaped string this glyph
 *     covers, inclusive
 * @param charEnd index one past the last character it covers; more than one past
 *     {@code charStart} for a ligature
 */
public record Glyph(int glyphId, float advance, int charStart, int charEnd) {

    public Glyph {
        if (charEnd <= charStart) {
            throw new IllegalArgumentException(
                    "glyph must cover at least one character, got [" + charStart + ", " + charEnd + ")");
        }
    }

    /** The characters of {@code source} this glyph stands for; its {@code /ToUnicode} entry. */
    public String text(String source) {
        return source.substring(charStart, charEnd);
    }

    Glyph scaled(float factor) {
        return new Glyph(glyphId, advance * factor, charStart, charEnd);
    }
}
