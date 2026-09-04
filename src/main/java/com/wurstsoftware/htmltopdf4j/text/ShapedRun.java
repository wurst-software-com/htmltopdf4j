package com.wurstsoftware.htmltopdf4j.text;

import java.util.List;

/**
 * A string converted by the shaper into positioned {@link Glyph}s. The unit both
 * measurement and text output use.
 *
 * @param glyphs the glyphs in visual order
 * @param advance the total width of the run in points
 */
public record ShapedRun(List<Glyph> glyphs, float advance) {

    public ShapedRun {
        glyphs = List.copyOf(glyphs);
    }

    public boolean isEmpty() {
        return glyphs.isEmpty();
    }
}
