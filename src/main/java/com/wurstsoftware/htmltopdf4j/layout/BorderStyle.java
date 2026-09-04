package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.paint.DashPattern;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import java.util.Locale;

/** How a border side is drawn. */
public enum BorderStyle {
    NONE,
    SOLID,
    DASHED,
    DOTTED,
    DOUBLE;

    /**
     * The dash the PDF stroke needs, or {@code null} for a continuous line. The
     * lengths scale with the border width, so a thick dashed border has long
     * dashes rather than the same short ones a hairline gets.
     */
    public DashPattern dash(float width) {
        return switch (this) {
            case DASHED -> new DashPattern(width * 3f, width * 2f);
            case DOTTED -> new DashPattern(width, width * 2f);
            default -> null;
        };
    }

    public static BorderStyle of(ComputedStyle style, String side) {
        return parse(style.raw("border-" + side + "-style", "none"));
    }

    public static BorderStyle parse(String value) {
        return switch (value == null ? "none" : value.trim().toLowerCase(Locale.ROOT)) {
            case "solid", "groove", "ridge", "inset", "outset" -> SOLID;
            case "dashed" -> DASHED;
            case "dotted" -> DOTTED;
            case "double" -> DOUBLE;
            default -> NONE;
        };
    }
}
