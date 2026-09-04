package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.style.Declaration;
import com.wurstsoftware.htmltopdf4j.style.Stylesheet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What one {@code @font-face} rule declares: a family, where to find the font
 * program, and which variant of the family that program is.
 *
 * <p>The descriptors matter because a family is usually declared several times,
 * once per variant. Without reading them a second rule replaces the first rather
 * than supplying the bold.
 *
 * @param family the family name the rule defines, unquoted
 * @param source the rule's {@code src}, still in its comma-separated form
 */
record FontFaceRules(String family, String source, boolean bold, boolean italic) {

    /** The weight at which a font stops being a regular and starts being a bold. */
    private static final int BOLD_WEIGHT = 600;

    /** The rules of a Stylesheet that declare both a family and a source. */
    static List<FontFaceRules> of(Stylesheet stylesheet) {
        List<FontFaceRules> rules = new ArrayList<>();
        for (Stylesheet.FontFaceRule rule : stylesheet.fontFaceRules()) {
            FontFaceRules parsed = parse(rule);
            if (parsed != null) {
                rules.add(parsed);
            }
        }
        return rules;
    }

    private static FontFaceRules parse(Stylesheet.FontFaceRule rule) {
        String family = null;
        String source = null;
        boolean bold = false;
        boolean italic = false;
        for (Declaration declaration : rule.declarations()) {
            String value = declaration.value().trim();
            switch (declaration.property().toLowerCase(Locale.ROOT)) {
                case "font-family" -> family = value.replaceAll("^['\"]|['\"]$", "");
                case "src" -> source = declaration.value();
                case "font-weight" -> bold = isBold(value.toLowerCase(Locale.ROOT));
                case "font-style" -> {
                    String slope = value.toLowerCase(Locale.ROOT);
                    italic = slope.startsWith("italic") || slope.startsWith("oblique");
                }
                default -> { }
            }
        }
        return family == null || source == null ? null : new FontFaceRules(family, source, bold, italic);
    }

    private static boolean isBold(String value) {
        if (value.startsWith("bold")) {
            return true;
        }
        try {
            // A range such as `400 700` is the variant it starts at: it covers
            // the regular too, and filing it as the bold would leave the regular
            // to a fallback family.
            return Integer.parseInt(value.split("\\s+")[0]) >= BOLD_WEIGHT;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
