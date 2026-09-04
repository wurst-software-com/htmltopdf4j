package com.wurstsoftware.htmltopdf4j.box;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;

/**
 * A contiguous piece of inline content sharing one computed style: text, an
 * image flowing with the text, or an {@code inline-block} element.
 *
 * <p>Exactly one of {@code text}, {@code image} and {@code inlineBlock} is
 * meaningful. Text keeps whatever whitespace survived collapsing in the Box tree
 * generator, because whether a space falls at a wrap point is Layout's decision.
 *
 * @param link the {@code href} this run is inside, or {@code null}
 */
public record InlineRun(
        String text,
        ComputedStyle style,
        String link,
        ImageBox image,
        BlockBox inlineBlock) {

    public static InlineRun text(String text, ComputedStyle style, String link) {
        return new InlineRun(text, style, link, null, null);
    }

    public static InlineRun image(ImageBox image, ComputedStyle style, String link) {
        return new InlineRun("", style, link, image, null);
    }

    public static InlineRun inlineBlock(BlockBox block, ComputedStyle style, String link) {
        return new InlineRun("", style, link, null, block);
    }

    public boolean isText() {
        return image == null && inlineBlock == null;
    }

    public boolean hasContent() {
        return image != null || inlineBlock != null || !text.isBlank();
    }
}
