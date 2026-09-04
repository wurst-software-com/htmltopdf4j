package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import java.util.Locale;

/**
 * Where content sits in a box taller than it is.
 *
 * <p>A table cell says this with {@code vertical-align}, a flex or grid item
 * with {@code align-items} or {@code align-self}, but the arithmetic is the same
 * one: the leftover room either stays below the content, is split around it, or
 * goes above it. This is the vertical twin of {@link LineBreaker#alignmentOffset}.
 *
 * <p>The keywords of both properties are parsed here rather than split into two
 * sets, once per read, so no caller trims or lower-cases a raw string of its
 * own. Anything unrecognised starts, which is where the content already is, so
 * being lenient costs nothing.
 */
enum Alignment {

    /**
     * Nothing was said. What that means is the caller's to decide: a flex item
     * defers to its container, a container stretches, and a table cell sits on
     * its row's baseline — which is why {@link #orElse} exists.
     */
    AUTO,
    START,
    CENTER,
    END,
    BASELINE,
    STRETCH;

    /** What a style says about one alignment property. */
    static Alignment of(ComputedStyle style, String property) {
        return of(style.raw(property));
    }

    static Alignment of(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return AUTO;
        }
        return switch (keyword.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            // `normal` behaves as `stretch` here rather than deferring: it is
            // the initial value of `align-items`, which nothing defers to.
            case "stretch", "normal" -> STRETCH;
            case "baseline", "first baseline" -> BASELINE;
            case "middle", "center" -> CENTER;
            case "bottom", "end", "flex-end", "self-end" -> END;
            default -> START;
        };
    }

    /** What {@code align-self} means when it says nothing: whatever the container said. */
    Alignment orElse(Alignment fallback) {
        return this == AUTO ? fallback : this;
    }

    /**
     * Whether this grows the box rather than moving it. {@code stretch} is the
     * initial value of {@code align-items}, so an absent one stretches too.
     */
    boolean stretches() {
        return this == STRETCH || this == AUTO;
    }

    /** Whether the content sits on the baseline its row or its line shares. */
    boolean isBaseline() {
        return this == BASELINE;
    }

    /**
     * How far down to start, given the room the content leaves over. Negative or
     * zero free space means the content fills the box and cannot move.
     */
    float offset(float free) {
        if (free <= 0f) {
            return 0f;
        }
        return switch (this) {
            case CENTER -> free / 2f;
            case END -> free;
            default -> 0f;
        };
    }
}
