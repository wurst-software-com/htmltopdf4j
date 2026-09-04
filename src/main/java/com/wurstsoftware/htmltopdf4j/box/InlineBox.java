package com.wurstsoftware.htmltopdf4j.box;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;

/**
 * An inline element that a run sits inside and that has something of its own to
 * draw or to reserve — a background, a border, padding.
 *
 * <p>An inline box is not one rectangle. It is one per line it occupies, and its
 * near edges belong to the first and last of them, so the runs it contains have
 * to know which element they came from rather than merely what it looked like:
 * two adjacent chips with identical styles are still two chips.
 *
 * @param id identity of the element, unique within one Box tree
 * @param opensHere whether this run is on the line the box starts on, which is
 *     the only line that gets its left edge
 */
public record InlineBox(int id, ComputedStyle style, boolean opensHere) {

    /** The same box, seen on a later line of the one it opened on. */
    public InlineBox continued() {
        return opensHere ? new InlineBox(id, style, false) : this;
    }

    public boolean sameBoxAs(InlineBox other) {
        return other != null && other.id == id;
    }

    /**
     * Whether an inline element is worth tracking at all: one that paints
     * nothing and reserves nothing costs its runs no space and leaves no marks.
     */
    public static boolean decorates(ComputedStyle style) {
        if (style.backgroundColor().isPresent()) {
            return true;
        }
        for (String side : new String[] {"top", "right", "bottom", "left"}) {
            if (style.length("padding-" + side).isPresent()
                    || !style.raw("border-" + side + "-style", "none").trim().equalsIgnoreCase("none")) {
                return true;
            }
        }
        return false;
    }
}
