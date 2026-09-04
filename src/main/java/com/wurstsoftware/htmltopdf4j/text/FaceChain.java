package com.wurstsoftware.htmltopdf4j.text;

import java.util.ArrayList;
import java.util.List;

/**
 * A primary Face and the Faces consulted for characters it cannot draw.
 *
 * <p>One level deep: a fallback Face has no chain of its own, which bounds the
 * search and keeps a missing glyph from walking every font on the machine.
 *
 * @param primary the Face the Cascade resolved for the run
 * @param fallbacks the Faces tried in order for characters {@code primary} lacks
 */
public record FaceChain(Face primary, List<Face> fallbacks) {

    public FaceChain {
        fallbacks = List.copyOf(fallbacks);
    }

    public static FaceChain of(Face primary) {
        return new FaceChain(primary, List.of());
    }

    /** The Face at a chain position: 0 is the primary, 1 the first fallback. */
    public Face at(int index) {
        return index == 0 ? primary : fallbacks.get(index - 1);
    }

    /**
     * One run of characters to be drawn in a single Face.
     *
     * @param chainIndex 0 for the primary, otherwise the fallback's position plus one
     */
    public record Segment(int chainIndex, String text) {}

    /**
     * Splits {@code text} into runs by which Face can draw them.
     *
     * <p>Returns {@code null} — meaning "draw it all in the primary" — whenever
     * the primary covers everything, which is the overwhelmingly common case and
     * keeps this off the hot path for ASCII.
     */
    public List<Segment> segment(String text) {
        if (fallbacks.isEmpty() || primary.canDisplayAll(text)) {
            return null;
        }

        List<Segment> segments = new ArrayList<>();
        int current = 0;
        int start = 0;
        boolean started = false;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int width = Character.charCount(codePoint);
            int face = faceFor(codePoint);
            if (face < 0) {
                // Whitespace is neutral: it joins whichever run surrounds it
                // rather than forcing a font switch in the middle of a word.
                i += width;
                continue;
            }
            if (!started) {
                // Leading neutrals belong to the first run that has a Face.
                current = face;
                started = true;
                i += width;
                continue;
            }
            if (face != current) {
                segments.add(new Segment(current, text.substring(start, i)));
                start = i;
                current = face;
            }
            i += width;
        }
        segments.add(new Segment(current, text.substring(start)));
        return segments;
    }

    /** The chain position that can draw {@code codePoint}, or -1 if it is neutral. */
    private int faceFor(int codePoint) {
        if (Character.isWhitespace(codePoint)) {
            return -1;
        }
        String character = new String(Character.toChars(codePoint));
        if (primary.canDisplayAll(character)) {
            return 0;
        }
        for (int i = 0; i < fallbacks.size(); i++) {
            if (fallbacks.get(i).canDisplayAll(character)) {
                return i + 1;
            }
        }
        // Covered nowhere: the primary draws its .notdef, which at least shows
        // the reader that something is missing rather than silently vanishing.
        return 0;
    }
}
