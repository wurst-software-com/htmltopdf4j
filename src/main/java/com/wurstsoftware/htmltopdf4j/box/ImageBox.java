package com.wurstsoftware.htmltopdf4j.box;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;

/**
 * An {@code <img>}, as either a block-level box or an atomic inline.
 *
 * <p>The intrinsic size is not known here — decoding happens once, later, so a
 * Document that uses the same image ten times decodes it once. Layout resolves
 * the used size from the CSS {@code width}/{@code height} in {@link #style()},
 * these presentational attributes, and the decoded intrinsic size, in that order
 * of precedence.
 *
 * @param attributeWidth the HTML {@code width} attribute in CSS pixels, or {@code null}
 * @param attributeHeight the HTML {@code height} attribute in CSS pixels, or {@code null}
 */
public record ImageBox(
        String source,
        ComputedStyle style,
        Float attributeWidth,
        Float attributeHeight,
        String alternativeText)
        implements BoxChild {

    @Override
    public boolean hasContent() {
        return !source.isBlank();
    }
}
