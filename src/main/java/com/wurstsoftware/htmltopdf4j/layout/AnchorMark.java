package com.wurstsoftware.htmltopdf4j.layout;

/**
 * Somewhere a link can land: where a block came to rest on its Page.
 *
 * @param name the element's HTML {@code id}, resolving {@code #fragment} links;
 *     {@code null} when the element has none
 * @param level 1–6 for {@code <h1>}–{@code <h6>}, which become outline entries;
 *     0 for a plain {@code id} anchor, which does not
 * @param title the heading's text, used as the outline entry's label
 * @param y the baseline the destination scrolls to, in Page space
 */
public record AnchorMark(String name, int level, String title, float y) {

    /** A destination that only resolves {@code #fragment} links. */
    public static AnchorMark named(String name, float y) {
        return new AnchorMark(name, 0, "", y);
    }

    public boolean isOutlineEntry() {
        return level > 0 && !title.isEmpty();
    }
}
