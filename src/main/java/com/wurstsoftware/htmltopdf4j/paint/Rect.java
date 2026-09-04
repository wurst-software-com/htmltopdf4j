package com.wurstsoftware.htmltopdf4j.paint;

/** An axis-aligned rectangle in points, with {@code (x, y)} its lower-left corner. */
public record Rect(float x, float y, float width, float height) {

    public float right() {
        return x + width;
    }

    public float top() {
        return y + height;
    }
}
