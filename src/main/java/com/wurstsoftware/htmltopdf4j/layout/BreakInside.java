package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import java.util.Locale;

/**
 * Whether a box asks not to be divided across Pages.
 *
 * <p>Both the legacy {@code page-break-inside} and the modern {@code break-inside}
 * are read. {@code avoid-column} and {@code avoid-region} are accepted and mean
 * nothing here, because this engine has neither columns nor regions — treating
 * them as {@code avoid-page} would break Pages the author never asked for.
 *
 * <p>Neither property inherits, so an {@code avoid} on a section does not
 * quietly make every box inside it unbreakable.
 */
final class BreakInside {

    private BreakInside() {}

    /** Whether this box is an Unbreakable box: one to place on a Page or not at all. */
    static boolean avoids(ComputedStyle style) {
        if (style.keyword("page-break-inside", "avoid")) {
            return true;
        }
        String value = style.raw("break-inside", "auto").trim().toLowerCase(Locale.ROOT);
        return value.equals("avoid") || value.equals("avoid-page");
    }
}
