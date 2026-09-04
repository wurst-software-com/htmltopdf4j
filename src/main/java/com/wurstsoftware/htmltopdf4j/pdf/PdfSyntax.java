package com.wurstsoftware.htmltopdf4j.pdf;

import java.util.Locale;
import java.util.SortedSet;

/** The small pieces of PDF syntax the writer builds strings out of. */
final class PdfSyntax {

    private PdfSyntax() {}

    /**
     * A number, always with a decimal point and never in the locale's notation.
     * A comma decimal separator would silently produce a corrupt content stream
     * on a machine whose default locale uses one.
     */
    static String number(float value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    /** Coordinates and sizes, at the two decimals the reference engine writes. */
    static String coord(float value) {
        return number(value, 2);
    }

    /**
     * Escapes a string for a PDF literal {@code (...)}. UTF-8 bytes pass through
     * verbatim, which is what URIs and ASCII titles want; text drawn in a
     * standard-14 Face goes through {@link ContentStream} instead, which maps to
     * WinAnsi.
     */
    static String escapeLiteral(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '(' -> out.append("\\(");
                case ')' -> out.append("\\)");
                case '\\' -> out.append("\\\\");
                case '\r' -> out.append("\\r");
                case '\n' -> out.append("\\n");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }

    /**
     * A human-readable string for a PDF text-string context such as an outline
     * {@code /Title}: ASCII as an escaped literal, anything else as UTF-16BE hex
     * behind a byte-order mark.
     */
    static String textString(String text) {
        if (isAscii(text)) {
            return "(" + escapeLiteral(text) + ")";
        }
        StringBuilder hex = new StringBuilder("<FEFF");
        for (int i = 0; i < text.length(); i++) {
            hex.append(String.format(Locale.ROOT, "%04X", (int) text.charAt(i)));
        }
        return hex.append('>').toString();
    }

    /** Hex-encodes a string as UTF-16BE, the form {@code /ToUnicode} destinations take. */
    static String utf16BeHex(String text) {
        StringBuilder hex = new StringBuilder(text.length() * 4);
        for (int i = 0; i < text.length(); i++) {
            hex.append(String.format(Locale.ROOT, "%04X", (int) text.charAt(i)));
        }
        return hex.toString();
    }

    /**
     * The six-uppercase-letter {@code ABCDEF+} prefix readers use to recognise a
     * subset font. Derived from the retained glyph ids by FNV-1a, so it is stable
     * across runs — two renders of the same Document produce the same tag.
     */
    static String subsetTag(SortedSet<Integer> usedGlyphIds) {
        long hash = 0xcbf29ce484222325L;
        for (int gid : usedGlyphIds) {
            hash ^= gid;
            hash *= 0x00000100000001B3L;
        }
        StringBuilder tag = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            tag.append((char) ('A' + Long.remainderUnsigned(hash, 26)));
            hash = Long.divideUnsigned(hash, 26);
        }
        return tag.toString();
    }

    private static boolean isAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}
