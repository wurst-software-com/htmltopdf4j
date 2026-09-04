package com.wurstsoftware.htmltopdf4j.box;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import java.util.List;

/**
 * A contiguous piece of inline content sharing one computed style: text, an
 * image flowing with the text, or an {@code inline-block} element.
 *
 * <p>Exactly one of {@code text}, {@code image} and {@code inlineBlock} is
 * meaningful. Text keeps whatever whitespace survived collapsing in the Box tree
 * generator, because whether a space falls at a wrap point is Layout's decision.
 *
 * @param link the {@code href} this run is inside, or {@code null}
 * @param inlines the decorated inline elements this run is inside, outermost
 *     first, so a chip knows which of its lines to draw its near edges on
 */
public record InlineRun(
        String text,
        ComputedStyle style,
        String link,
        ImageBox image,
        BlockBox inlineBlock,
        List<InlineBox> inlines) {

    public InlineRun {
        inlines = List.copyOf(inlines);
    }

    public static InlineRun text(String text, ComputedStyle style, String link) {
        return text(text, style, link, List.of());
    }

    public static InlineRun text(String text, ComputedStyle style, String link, List<InlineBox> inlines) {
        return new InlineRun(text, style, link, null, null, inlines);
    }

    public static InlineRun image(ImageBox image, ComputedStyle style, String link, List<InlineBox> inlines) {
        return new InlineRun("", style, link, image, null, inlines);
    }

    public static InlineRun inlineBlock(
            BlockBox block, ComputedStyle style, String link, List<InlineBox> inlines) {
        return new InlineRun("", style, link, null, block, inlines);
    }

    /**
     * The same run, inside these inline boxes instead. A line break re-files the
     * runs it did not fit with the boxes they are still inside, which by then
     * have lost their left edge.
     */
    public InlineRun withInlines(List<InlineBox> inlines) {
        return new InlineRun(text, style, link, image, inlineBlock, inlines);
    }

    public boolean isText() {
        return image == null && inlineBlock == null;
    }

    public boolean hasContent() {
        return image != null || inlineBlock != null || !text.isBlank();
    }
}
