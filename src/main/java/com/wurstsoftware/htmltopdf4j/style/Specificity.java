package com.wurstsoftware.htmltopdf4j.style;

/**
 * A selector's specificity: how many ids, how many class-like simple selectors,
 * and how many type-like ones it contains.
 *
 * <p>Compared field by field, ids first, exactly as CSS orders them — no
 * base-ten packing, which would let 11 classes beat an id.
 */
public record Specificity(int ids, int classes, int types) implements Comparable<Specificity> {

    public static final Specificity NONE = new Specificity(0, 0, 0);

    /** An inline {@code style} attribute outranks every selector in a stylesheet. */
    public static final Specificity INLINE = new Specificity(Integer.MAX_VALUE, 0, 0);

    @Override
    public int compareTo(Specificity other) {
        int byIds = Integer.compare(ids, other.ids);
        if (byIds != 0) {
            return byIds;
        }
        int byClasses = Integer.compare(classes, other.classes);
        return byClasses != 0 ? byClasses : Integer.compare(types, other.types);
    }

    /**
     * Counts a selector's simple selectors.
     *
     * <p>Ids come from {@code #name}; classes from {@code .name}, attribute
     * selectors and pseudo-classes; types from element names and pseudo-elements.
     * Combinators and the universal selector contribute nothing, which is what
     * the spec says.
     */
    public static Specificity of(String selector) {
        int ids = 0;
        int classes = 0;
        int types = 0;

        for (int i = 0; i < selector.length(); ) {
            char ch = selector.charAt(i);
            switch (ch) {
                case '#' -> {
                    ids++;
                    i = skipName(selector, i + 1);
                }
                case '.' -> {
                    classes++;
                    i = skipName(selector, i + 1);
                }
                case '[' -> {
                    classes++;
                    int close = selector.indexOf(']', i);
                    i = close < 0 ? selector.length() : close + 1;
                }
                case ':' -> {
                    // A double colon marks a pseudo-element, which counts as a type.
                    boolean pseudoElement = i + 1 < selector.length() && selector.charAt(i + 1) == ':';
                    if (pseudoElement) {
                        types++;
                    } else {
                        classes++;
                    }
                    i = skipPseudo(selector, i + (pseudoElement ? 2 : 1));
                }
                case '*', ' ', '>', '+', '~', ',', '\t', '\n' -> i++;
                default -> {
                    if (Character.isLetter(ch)) {
                        types++;
                        i = skipName(selector, i);
                    } else {
                        i++;
                    }
                }
            }
        }
        return new Specificity(ids, classes, types);
    }

    private static int skipName(String selector, int from) {
        int i = from;
        while (i < selector.length() && (Character.isLetterOrDigit(selector.charAt(i))
                || selector.charAt(i) == '-' || selector.charAt(i) == '_')) {
            i++;
        }
        return Math.max(i, from + 1);
    }

    /** Skips a pseudo-class name and any parenthesised argument, such as {@code nth-child(2n+1)}. */
    private static int skipPseudo(String selector, int from) {
        int i = skipName(selector, from);
        if (i < selector.length() && selector.charAt(i) == '(') {
            int depth = 0;
            while (i < selector.length()) {
                char ch = selector.charAt(i++);
                if (ch == '(') {
                    depth++;
                } else if (ch == ')' && --depth == 0) {
                    break;
                }
            }
        }
        return i;
    }
}
