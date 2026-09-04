package com.wurstsoftware.htmltopdf4j.paint;

/**
 * An RGB colour with components in {@code [0, 1]}, the form the PDF {@code rg}
 * and {@code RG} operators take.
 *
 * <p>There is no alpha channel. CSS {@code transparent} and the alpha of
 * {@code rgba()} are resolved during the Cascade — a fully transparent fill
 * becomes no Paint command at all rather than a Paint command that draws
 * nothing — so by the time a colour reaches a Display list it is opaque.
 */
public record Color(float r, float g, float b) {

    public static final Color BLACK = new Color(0f, 0f, 0f);
    public static final Color WHITE = new Color(1f, 1f, 1f);

    public static Color fromRgb255(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f);
    }
}
