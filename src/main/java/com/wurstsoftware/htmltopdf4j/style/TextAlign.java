package com.wurstsoftware.htmltopdf4j.style;

import java.util.Locale;

/** How a block's line boxes are aligned within its content width. */
public enum TextAlign {
    LEFT,
    RIGHT,
    CENTER,
    JUSTIFY;

    /** Resolves the direction-relative keywords against the block's own direction. */
    public static TextAlign parse(String value, boolean rtl, TextAlign fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "left" -> LEFT;
            case "right" -> RIGHT;
            case "center" -> CENTER;
            case "justify" -> JUSTIFY;
            case "start" -> rtl ? RIGHT : LEFT;
            case "end" -> rtl ? LEFT : RIGHT;
            default -> fallback;
        };
    }
}
