package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.style.Length;

/**
 * The four sides of one CSS box edge — margin, padding or border — resolved to
 * points against a known containing block.
 *
 * <p>Percentages on every side resolve against the containing block's
 * <em>width</em>, including top and bottom. That is what CSS says, however
 * surprising it reads.
 */
public record Edges(float top, float right, float bottom, float left) {

    public static final Edges NONE = new Edges(0f, 0f, 0f, 0f);

    public float horizontal() {
        return left + right;
    }

    public float vertical() {
        return top + bottom;
    }

    public Edges plus(Edges other) {
        return new Edges(
                top + other.top, right + other.right, bottom + other.bottom, left + other.left);
    }

    /** Resolves the {@code margin-*} longhands. {@code auto} resolves to zero and is centred elsewhere. */
    public static Edges margin(ComputedStyle style, float containingWidth) {
        return of(style, "margin", containingWidth);
    }

    /** Resolves the {@code padding-*} longhands. */
    public static Edges padding(ComputedStyle style, float containingWidth) {
        return of(style, "padding", containingWidth);
    }

    /** Resolves the {@code border-*-width} longhands, treating a styleless side as absent. */
    public static Edges borderWidths(ComputedStyle style) {
        return new Edges(
                borderWidth(style, "top"),
                borderWidth(style, "right"),
                borderWidth(style, "bottom"),
                borderWidth(style, "left"));
    }

    private static Edges of(ComputedStyle style, String prefix, float containingWidth) {
        return new Edges(
                side(style, prefix + "-top", containingWidth),
                side(style, prefix + "-right", containingWidth),
                side(style, prefix + "-bottom", containingWidth),
                side(style, prefix + "-left", containingWidth));
    }

    private static float side(ComputedStyle style, String property, float containingWidth) {
        return style.length(property)
                .map(length -> style.resolve(length, containingWidth))
                .orElse(0f);
    }

    private static float borderWidth(ComputedStyle style, String side) {
        if (BorderStyle.of(style, side) == BorderStyle.NONE) {
            return 0f;
        }
        Length declared = style.length("border-" + side + "-width").orElse(null);
        if (declared == null) {
            return switch (style.raw("border-" + side + "-width", "medium").trim().toLowerCase(java.util.Locale.ROOT)) {
                case "thin" -> 1f;
                case "thick" -> 5f;
                default -> 3f;
            };
        }
        return style.resolve(declared, 0f);
    }
}
