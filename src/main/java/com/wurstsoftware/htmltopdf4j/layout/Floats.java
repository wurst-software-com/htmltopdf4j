package com.wurstsoftware.htmltopdf4j.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * The floated boxes currently shortening the lines around them.
 *
 * <p>A float is out of normal flow but not out of the way: line boxes beside it
 * are narrowed, and a box that {@code clear}s drops below it. Only the vertical
 * band a float occupies matters, so a float is remembered as a band on one side
 * of one Page and forgotten when the Page ends.
 */
final class Floats {

    /** One float: the Page it is on, the band it occupies, and which side it sits on. */
    private record Band(int page, float top, float bottom, float edge, boolean left) {}

    private final List<Band> bands = new ArrayList<>();

    void add(int page, float top, float bottom, float edge, boolean left) {
        bands.add(new Band(page, top, bottom, edge, left));
    }

    /** How far in from {@code left} a line starting at {@code y} must begin. */
    float leftEdge(int page, float y, float height, float left) {
        float edge = left;
        for (Band band : bands) {
            if (band.left() && overlaps(band, page, y, height)) {
                edge = Math.max(edge, band.edge());
            }
        }
        return edge;
    }

    /** How far in from {@code right} a line starting at {@code y} must end. */
    float rightEdge(int page, float y, float height, float right) {
        float edge = right;
        for (Band band : bands) {
            if (!band.left() && overlaps(band, page, y, height)) {
                edge = Math.min(edge, band.edge());
            }
        }
        return edge;
    }

    /**
     * The first y at or below {@code y} that is clear of the floats on the given
     * sides, which is where a {@code clear}ed box starts.
     */
    float clearance(int page, float y, boolean left, boolean right) {
        float cleared = y;
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Band band : bands) {
                boolean side = band.left() ? left : right;
                if (side && band.page() == page && band.bottom() > cleared && band.top() <= cleared) {
                    cleared = band.bottom();
                    moved = true;
                }
            }
        }
        return cleared;
    }

    /** Drops every float from Pages before this one; they can no longer affect anything. */
    void retain(int page) {
        bands.removeIf(band -> band.page() < page);
    }

    private static boolean overlaps(Band band, int page, float y, float height) {
        return band.page() == page && band.top() < y + height && band.bottom() > y;
    }
}
