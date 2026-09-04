package com.wurstsoftware.htmltopdf4j.text;

import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.PostScriptTable;
import org.apache.fontbox.ttf.TrueTypeFont;

/**
 * The metrics a PDF {@code /FontDescriptor} carries, all scaled to the
 * 1000-unit em PDF works in regardless of the face's own units per em.
 *
 * @param flags the PDF font flags; bit 1 fixed-pitch, bit 6 nonsymbolic, bit 7 italic
 * @param stemV a nominal vertical stem width — PDF requires the entry, no reader
 *     is known to use it for an embedded face, and deriving it properly would
 *     mean measuring outlines
 */
public record PdfFontDescriptor(
        String postScriptName,
        int flags,
        int bboxXMin,
        int bboxYMin,
        int bboxXMax,
        int bboxYMax,
        float italicAngle,
        int ascent,
        int descent,
        int capHeight,
        int stemV) {

    private static final int FLAG_FIXED_PITCH = 1;
    private static final int FLAG_NONSYMBOLIC = 32;
    private static final int FLAG_ITALIC = 64;
    private static final int NOMINAL_STEM_V = 80;

    static PdfFontDescriptor of(TrueTypeFont font, int unitsPerEm, int ascender, int descender)
            throws java.io.IOException {
        var head = font.getHeader();
        PostScriptTable post = post(font);
        float italicAngle = post != null ? post.getItalicAngle() : 0f;
        boolean monospaced = post != null && post.getIsFixedPitch() != 0;

        int flags = FLAG_NONSYMBOLIC;
        if (monospaced) {
            flags |= FLAG_FIXED_PITCH;
        }
        if (italicAngle != 0f) {
            flags |= FLAG_ITALIC;
        }

        return new PdfFontDescriptor(
                sanitiseName(name(font)),
                flags,
                scale(head.getXMin(), unitsPerEm),
                scale(head.getYMin(), unitsPerEm),
                scale(head.getXMax(), unitsPerEm),
                scale(head.getYMax(), unitsPerEm),
                italicAngle,
                scale(ascender, unitsPerEm),
                scale(descender, unitsPerEm),
                scale(capHeight(font, ascender), unitsPerEm),
                NOMINAL_STEM_V);
    }

    private static int capHeight(TrueTypeFont font, int ascender) {
        try {
            OS2WindowsMetricsTable os2 = font.getOS2Windows();
            // Version 0 and 1 of OS/2 have no sCapHeight field at all.
            if (os2 != null && os2.getVersion() >= 2 && os2.getCapHeight() > 0) {
                return os2.getCapHeight();
            }
        } catch (java.io.IOException | RuntimeException e) {
            // Fall through to the estimate.
        }
        return Math.round(ascender * 0.7f);
    }

    private static PostScriptTable post(TrueTypeFont font) {
        try {
            return font.getPostScript();
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    private static String name(TrueTypeFont font) {
        try {
            var naming = font.getNaming();
            if (naming != null) {
                String postScript = naming.getPostScriptName();
                if (postScript != null && !postScript.isBlank()) {
                    return postScript;
                }
                String family = naming.getFontFamily();
                if (family != null && !family.isBlank()) {
                    return family;
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            // Fall through to the placeholder.
        }
        return "EmbeddedFont";
    }

    /** A PDF name has to survive being written unescaped, so keep it to safe characters. */
    private static String sanitiseName(String raw) {
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isLetterOrDigit(ch) && ch < 128 || ch == '-' || ch == '+') {
                cleaned.append(ch);
            }
        }
        return cleaned.isEmpty() ? "EmbeddedFont" : cleaned.toString();
    }

    private static int scale(int units, int unitsPerEm) {
        return Math.round(units * 1000f / unitsPerEm);
    }
}
