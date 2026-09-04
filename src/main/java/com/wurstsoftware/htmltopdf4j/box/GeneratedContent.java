package com.wurstsoftware.htmltopdf4j.box;

import java.util.Locale;
import org.jsoup.nodes.Element;

/**
 * The text a {@code content} property produces.
 *
 * <p>A {@code content} value is a sequence of pieces — quoted strings and
 * {@code attr()} references — concatenated. A value containing anything this
 * engine does not implement, {@code counter()} above all, produces nothing at
 * all rather than a partial string: half a generated label is worse than none.
 */
final class GeneratedContent {

    private GeneratedContent() {}

    /** The generated text, or the empty string when the value generates nothing. */
    static String of(String value, Element element) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()
                || trimmed.equalsIgnoreCase("none")
                || trimmed.equalsIgnoreCase("normal")) {
            return "";
        }

        StringBuilder generated = new StringBuilder();
        int i = 0;
        while (i < trimmed.length()) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '"' || c == '\'') {
                int end = closingQuote(trimmed, i);
                if (end < 0) {
                    return "";
                }
                generated.append(unescape(trimmed.substring(i + 1, end)));
                i = end + 1;
            } else if (trimmed.regionMatches(true, i, "attr(", 0, 5)) {
                int end = trimmed.indexOf(')', i);
                if (end < 0) {
                    return "";
                }
                generated.append(element.attr(trimmed.substring(i + 5, end).trim()));
                i = end + 1;
            } else {
                // counter(), open-quote, url() and the rest: not implemented.
                return "";
            }
        }
        return generated.toString();
    }

    private static int closingQuote(String value, int start) {
        char quote = value.charAt(start);
        for (int i = start + 1; i < value.length(); i++) {
            if (value.charAt(i) == '\\') {
                i++;
            } else if (value.charAt(i) == quote) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Resolves CSS string escapes. A {@code \\201C} is a code point in hex,
     * optionally followed by one space that terminates it rather than being part
     * of the text.
     */
    private static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            int digits = 0;
            while (digits < 6 && i + 1 + digits < text.length()
                    && isHex(text.charAt(i + 1 + digits))) {
                digits++;
            }
            if (digits == 0) {
                if (i + 1 < text.length()) {
                    out.append(text.charAt(++i));
                }
                continue;
            }
            out.appendCodePoint(Integer.parseInt(
                    text.substring(i + 1, i + 1 + digits).toLowerCase(Locale.ROOT), 16));
            i += digits;
            if (i + 1 < text.length() && text.charAt(i + 1) == ' ') {
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
