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
     * Whether a table cell is aligned on the row's baseline, which is what it
     * does unless it says otherwise.
     */
    static boolean isBaseline(String keyword) {
        return keyword == null || keyword.isBlank() || asksForBaseline(keyword);
    }

    /**
     * Whether this keyword asks for the baseline outright. A flex or grid item
     * has to say so, because the initial value of {@code align-items} is
     * {@code stretch} rather than the baseline a table cell falls back to.
     */
    static boolean asksForBaseline(String keyword) {
        if (keyword == null) {
            return false;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return value.equals("baseline") || value.equals("first baseline");
    }

    /** {@code align-self: auto} defers to the container, which is the default. */
    static boolean isAuto(String keyword) {
        return keyword == null || keyword.isBlank() || keyword.trim().equalsIgnoreCase("auto");
    }

    /**
     * Whether this keyword grows the box rather than moving it. {@code stretch}
     * is the initial value of {@code align-items}, so an absent one stretches
     * too. {@code baseline} does not: it moves an item onto the common baseline
     * its line works out, which is a shift rather than a height.
     */
    static boolean stretches(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return switch (keyword.trim().toLowerCase(Locale.ROOT)) {
            case "stretch", "normal", "auto" -> true;
            default -> false;
        };
    }

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
