package com.wurstsoftware.htmltopdf4j.text;

import java.util.Collections;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What a Document needs from an {@link EmbeddedFace} in order to embed it as a
 * Type0/CIDFontType2 composite.
 *
 * @param widths the natural {@code hmtx} advance of every used glyph, in a
 *     1000-unit em, for the PDF {@code /W} array
 * @param toUnicode the characters each used glyph stands for, for the
 *     {@code /ToUnicode} CMap — a ligature glyph maps back to all of them, which
 *     is what keeps ligated text selectable and searchable
 */
public record CidLayout(SortedMap<Integer, Integer> widths, SortedMap<Integer, String> toUnicode) {

    public CidLayout {
        widths = Collections.unmodifiableSortedMap(new java.util.TreeMap<>(widths));
        toUnicode = Collections.unmodifiableSortedMap(new java.util.TreeMap<>(toUnicode));
    }

    /** The glyphs to retain when subsetting. */
    public SortedSet<Integer> usedGlyphIds() {
        return new TreeSet<>(widths.keySet());
    }

    public boolean isEmpty() {
        return widths.isEmpty();
    }
}
