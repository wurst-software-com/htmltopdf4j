package com.wurstsoftware.htmltopdf4j.layout;

import java.util.Locale;

/**
 * Where content sits in a box taller than it is.
 *
 * <p>A table cell says this with {@code vertical-align} and a grid item with
 * {@code align-items} or {@code align-self}, but the arithmetic is the same one:
 * the leftover room either stays below the content, is split around it, or goes
 * above it. This is the vertical twin of
 * {@link LineBreaker#alignmentOffset}.
 *
 * <p>The keywords of both properties are accepted here rather than split into
 * two sets. Anything unrecognised leaves the content at the top, which is where
 * it already is, so being lenient costs nothing.
 */
final class VerticalAlign {

    private VerticalAlign() {}

    /**
     * How far down to start, given the room the content leaves over. Negative or
     * zero free space means the content fills the box and cannot move.
     */
    static float offset(String keyword, float free) {
        if (keyword == null || free <= 0f) {
            return 0f;
        }
        return switch (keyword.trim().toLowerCase(Locale.ROOT)) {
            case "middle", "center" -> free / 2f;
            case "bottom", "end", "flex-end", "self-end" -> free;
            default -> 0f;
        };
    }
}
