package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.paint.Rect;

/**
 * The clickable rectangle of one laid-out piece of a link, in Page space.
 *
 * @param link 1-based index into the render context's interned link targets
 */
public record LinkArea(Rect rect, int link) {

    /** Two areas are on one line when their rectangles sit at the same height. */
    private static final float SAME_LINE = 0.01f;

    /**
     * How far apart two pieces of one link may be and still count as adjacent,
     * as a share of the line's height. The space between two words is well under
     * half a line; anything further off is a second line or a second link.
     */
    private static final float ADJACENT = 0.5f;

    /** Whether {@code next} is the same link continuing along the same line. */
    boolean adjoins(LinkArea next) {
        return link == next.link()
                && Math.abs(rect.y() - next.rect().y()) < SAME_LINE
                && next.rect().x() >= rect.x()
                && next.rect().x() - rect.right() < rect.height() * ADJACENT;
    }

    /** One area covering both, for two pieces that {@link #adjoins} each other. */
    LinkArea and(LinkArea next) {
        return new LinkArea(
                new Rect(
                        rect.x(),
                        rect.y(),
                        Math.max(rect.right(), next.rect().right()) - rect.x(),
                        Math.max(rect.height(), next.rect().height())),
                link);
    }
}
