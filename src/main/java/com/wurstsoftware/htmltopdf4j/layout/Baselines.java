package com.wurstsoftware.htmltopdf4j.layout;

/**
 * Putting boxes that stand side by side on one baseline.
 *
 * <p>A table row does this with its cells and a flex line with its items, and it
 * is the same arithmetic both times: measure how far below its own top each box
 * carries its first line, take the deepest of those as the common baseline, and
 * drop every shallower box by the difference. A box with no first line to align
 * — one holding nothing but an image, or nothing at all — abstains: it neither
 * moves nor votes on where the common baseline is.
 */
final class Baselines {

    /** How far below its own top each box carries its first line; NaN for a box that abstained. */
    private final float[] measured;

    private float common;

    Baselines(int count) {
        this.measured = new float[count];
        java.util.Arrays.fill(measured, Float.NaN);
    }

    /** Records where one box carries its first line, and raises the common baseline to it. */
    void measured(int index, float baseline) {
        measured[index] = baseline;
        common = Math.max(common, baseline);
    }

    /** How far this box has to drop to reach the common baseline; zero if it abstained. */
    float shift(int index) {
        return Float.isNaN(measured[index]) ? 0f : common - measured[index];
    }

    /**
     * Where inside its row or its line a box starts: on the common baseline when
     * it asked for one and has a line to put there, and otherwise wherever its
     * alignment puts it in the room it leaves over.
     */
    float offset(int index, Alignment alignment, float free) {
        float shift = shift(index);
        return shift > 0f ? shift : alignment.offset(free);
    }
}
