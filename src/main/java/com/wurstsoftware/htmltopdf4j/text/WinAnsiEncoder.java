package com.wurstsoftware.htmltopdf4j.text;

/**
 * The CP1252 encoding PDF calls {@code /WinAnsiEncoding}, used for text drawn in
 * a standard-14 Face.
 *
 * <p>Only that path needs it. An embedded Face is written as a Type0 composite
 * with Identity-H encoding, where glyph ids go into the content stream directly
 * and no character encoding is involved at all.
 */
public final class WinAnsiEncoder {

    /** The CP1252 characters in {@code 0x80}–{@code 0x9F}; {@code 0} where undefined. */
    private static final char[] HIGH_SPECIALS = {
        '€', 0, '‚', 'ƒ', '„', '…', '†', '‡',
        'ˆ', '‰', 'Š', '‹', 'Œ', 0, 'Ž', 0,
        0, '‘', '’', '“', '”', '•', '–', '—',
        '˜', '™', 'š', '›', 'œ', 0, 'ž', 'Ÿ'
    };

    private WinAnsiEncoder() {}

    /**
     * The WinAnsi byte for {@code ch}, or {@code -1} if the encoding has none.
     * ASCII and Latin-1 map through unchanged; the CP1252 specials — curly
     * quotes, dashes, the bullet, the euro sign — occupy the gap Latin-1 leaves
     * at {@code 0x80}–{@code 0x9F}.
     */
    public static int encode(char ch) {
        if ((ch >= 0x20 && ch <= 0x7E) || (ch >= 0xA0 && ch <= 0xFF)) {
            return ch;
        }
        for (int i = 0; i < HIGH_SPECIALS.length; i++) {
            if (HIGH_SPECIALS[i] == ch && ch != 0) {
                return 0x80 + i;
            }
        }
        return -1;
    }

    /** The character a WinAnsi byte stands for, or {@code 0} where undefined. */
    public static char decode(int code) {
        if (code >= 0x80 && code <= 0x9F) {
            return HIGH_SPECIALS[code - 0x80];
        }
        if ((code >= 0x20 && code <= 0x7E) || (code >= 0xA0 && code <= 0xFF)) {
            return (char) code;
        }
        return 0;
    }

    /**
     * Escapes {@code text} for a PDF literal string drawn in a standard-14 Face.
     *
     * <p>Printable ASCII goes out as itself. Everything else becomes an octal
     * escape of its WinAnsi byte, so Latin-1 text and the CP1252 specials —
     * curly quotes, dashes, the bullet — render rather than turning into
     * question marks. A character WinAnsi cannot represent at all becomes
     * {@code ?}: the standard-14 Face embeds nothing, so there is no fallback
     * chain that could rescue it.
     */
    public static String escapeLiteral(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '(' -> out.append("\\(");
                case ')' -> out.append("\\)");
                case '\\' -> out.append("\\\\");
                case '\r' -> out.append("\\r");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch >= 0x21 && ch <= 0x7E || ch == ' ') {
                        out.append(ch);
                    } else {
                        int code = encode(ch);
                        out.append(code < 0 ? "?" : String.format(java.util.Locale.ROOT, "\\%03o", code));
                    }
                }
            }
        }
        return out.toString();
    }
}
