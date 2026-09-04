package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.box.InlineBox;
import com.wurstsoftware.htmltopdf4j.box.InlineRun;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.style.TextAlign;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapping a block's inline content into the visual lines that fit its width.
 *
 * <p>Breaking is greedy, which is what browsers do: a word is placed if it fits
 * and starts a new line if it does not. There is no Knuth-Plass paragraph
 * optimisation, and none is wanted — the Expectations are Chromium's output, and
 * Chromium breaks greedily too.
 *
 * <p>An atomic inline — an image or an {@code inline-block} — is measured
 * through {@link AtomicMeasurer} rather than here, because knowing its size
 * means laying out a whole subtree, which is Layout's job and not this class's.
 */
public final class LineBreaker {

    /** The size an atomic inline occupies on a line. */
    public record Size(float width, float height, float baseline) {}

    /** How Layout tells the breaker how big an image or inline-block is. */
    @FunctionalInterface
    public interface AtomicMeasurer {
        Size measure(InlineRun run, float availableWidth);
    }

    /** One piece of a visual line, already positioned along it. */
    public sealed interface Fragment {
        float x();

        float width();

        InlineRun run();
    }

    public record TextFragment(String text, InlineRun run, int face, float x, float width, float size)
            implements Fragment {}

    /** An image or an {@code inline-block}, occupying a box on the line. */
    public record AtomicFragment(InlineRun run, float x, float width, float height, float baseline)
            implements Fragment {}

    /**
     * One visual line.
     *
     * @param ascent the distance from the line's top to its baseline
     * @param descent the distance from the baseline to the line's bottom
     * @param leading the extra space {@code line-height} adds around the text,
     *     half above and half below
     */
    public record VisualLine(
            List<Fragment> fragments, float width, float ascent, float descent, float leading) {

        public VisualLine {
            fragments = List.copyOf(fragments);
        }

        public float height() {
            return ascent + descent + leading;
        }
    }

    private final java.util.function.IntFunction<FaceChain> chains;
    private final java.util.function.ToIntFunction<ComputedStyle> faceIndex;
    private final AtomicMeasurer atomics;

    public LineBreaker(
            java.util.function.ToIntFunction<ComputedStyle> faceIndex,
            java.util.function.IntFunction<FaceChain> chains,
            AtomicMeasurer atomics) {
        this.faceIndex = faceIndex;
        this.chains = chains;
        this.atomics = atomics;
    }

    /**
     * Breaks {@code runs} into visual lines no wider than {@code width}.
     *
     * @param firstLineIndent {@code text-indent}, applied to the first line only
     */
    public List<VisualLine> breakLines(List<InlineRun> runs, float width, float firstLineIndent) {
        Wrapper wrapper = new Wrapper(width, Math.max(0f, firstLineIndent));
        List<InlineBox> open = List.of();
        for (InlineRun run : runs) {
            // An inline box's padding and border are space on the line, on the
            // line it opens and the line it closes on: reserving it here is what
            // stops a chip's background from lying under the word beside it.
            wrapper.crossEdges(open, run.inlines(), width);
            open = run.inlines();
            if (!run.isText()) {
                wrapper.atomic(run);
            } else if (run.text().isEmpty()) {
                // An empty run exists only to give an otherwise blank line its height.
                wrapper.place(new TextFragment("", run, faceIndex.applyAsInt(run.style()), wrapper.x, 0f,
                        run.style().fontSize()));
            } else {
                wrapper.text(run);
            }
        }
        wrapper.crossEdges(open, List.of(), width);
        return wrapper.finish();
    }

    /** The space an inline box reserves before its first fragment on a line. */
    private static float leading(InlineBox box, float containingWidth) {
        return Edges.padding(box.style(), containingWidth).left()
                + Edges.borderWidths(box.style()).left();
    }

    /** The space it reserves after its last one. */
    private static float trailing(InlineBox box, float containingWidth) {
        return Edges.padding(box.style(), containingWidth).right()
                + Edges.borderWidths(box.style()).right();
    }

    /** How many inline boxes two runs have in common, from the outside in. */
    private static int commonDepth(List<InlineBox> before, List<InlineBox> after) {
        int depth = 0;
        while (depth < before.size() && depth < after.size()
                && before.get(depth).sameBoxAs(after.get(depth))) {
            depth++;
        }
        return depth;
    }

    /**
     * How much wider than the line a word may measure and still be treated as
     * fitting.
     *
     * <p>A box sized to its own content — a flex item, a shrink-to-fit float —
     * measures its text, adds its padding, and hands the sum back as a width;
     * the width the text is then wrapped at is that sum minus the padding
     * again. In float arithmetic that round trip can land a hair below where it
     * started, and without this tolerance a box sized exactly to its content
     * wraps its own last character onto a second line.
     */
    private static final float FIT_TOLERANCE = 0.01f;

    /** The state of a wrap in progress: the lines done, and the line being filled. */
    private final class Wrapper {
        private final List<VisualLine> lines = new ArrayList<>();
        private final List<Fragment> current = new ArrayList<>();
        private final float available;
        private float x;

        Wrapper(float available, float firstLineIndent) {
            this.available = available;
            this.x = firstLineIndent;
        }

        /**
         * Reserves the edges of the inline boxes that end between two runs and
         * of those that begin there. A box that opened on an earlier line has
         * no left edge here: it was cut by the break, not started by it.
         */
        void crossEdges(List<InlineBox> before, List<InlineBox> after, float containingWidth) {
            int common = commonDepth(before, after);
            for (int i = before.size() - 1; i >= common; i--) {
                x += trailing(before.get(i), containingWidth);
            }
            for (int i = common; i < after.size(); i++) {
                if (after.get(i).opensHere()) {
                    x += leading(after.get(i), containingWidth);
                }
            }
        }

        void place(Fragment fragment) {
            current.add(fragment);
            x += fragment.width();
        }

        void newLine() {
            lines.add(LineBreaker.this.finish(reorder(current), x));
            current.clear();
            x = 0f;
        }

        void atomic(InlineRun run) {
            Size size = atomics.measure(run, available - x);
            if (x > 0f && x + size.width() > available && !current.isEmpty()) {
                newLine();
                size = atomics.measure(run, available);
            }
            place(new AtomicFragment(run, x, size.width(), size.height(), size.baseline()));
        }

        void text(InlineRun run) {
            int face = faceIndex.applyAsInt(run.style());
            FaceChain chain = chains.apply(face);
            float size = run.style().fontSize();
            float spacing = letterSpacing(run.style());
            boolean wraps = wraps(run.style());

            for (String word : splitIntoWords(run.text())) {
                float wordWidth = advance(chain, word, size, spacing);
                if (wraps && x + wordWidth > available + FIT_TOLERANCE && !current.isEmpty() && !word.isBlank()) {
                    newLine();
                }
                if (x == 0f && word.isBlank()) {
                    // A space that fell to the start of a line is not drawn.
                    continue;
                }
                if (wraps && wordWidth > available + FIT_TOLERANCE && x == 0f) {
                    breakWithinWord(word, run, face, chain, size, spacing);
                    continue;
                }
                place(new TextFragment(word, run, face, x, wordWidth, size));
            }
        }

        /**
         * Breaks a single word that is wider than the whole line, character by
         * character, so a long URL wraps instead of running off the Page.
         */
        private void breakWithinWord(
                String word, InlineRun run, int face, FaceChain chain, float size, float spacing) {

            int start = 0;
            for (int i = 0; i < word.length(); ) {
                int next = word.offsetByCodePoints(i, 1);
                float candidate = advance(chain, word.substring(start, next), size, spacing);
                if (x + candidate > available && next > start + 1) {
                    String chunk = word.substring(start, i);
                    place(new TextFragment(chunk, run, face, x, advance(chain, chunk, size, spacing), size));
                    newLine();
                    start = i;
                }
                i = next;
            }
            String rest = word.substring(start);
            if (!rest.isEmpty()) {
                place(new TextFragment(rest, run, face, x, advance(chain, rest, size, spacing), size));
            }
        }

        List<VisualLine> finish() {
            if (!current.isEmpty()) {
                newLine();
            }
            return lines;
        }
    }

    /**
     * Puts a line's fragments into visual order.
     *
     * <p>Fragments are built in logical order — the order the characters appear
     * in the source. In a right-to-left paragraph that is not the order they are
     * drawn in, so the line is reordered by the Unicode bidirectional algorithm
     * and the fragments are re-laid along the line in their new order. The text
     * of each fragment is left alone: it is one directional run by construction,
     * so only the runs move.
     */
    private static List<Fragment> reorder(List<Fragment> fragments) {
        if (fragments.size() < 2 || fragments.stream().noneMatch(LineBreaker::isRightToLeft)) {
            return fragments;
        }
        List<Fragment> visual = new ArrayList<>(fragments);
        // A right-to-left paragraph draws its runs right to left; the runs that
        // are themselves left-to-right keep their internal order, which they do
        // because each fragment's own text is never reversed.
        java.util.Collections.reverse(visual);

        List<Fragment> placed = new ArrayList<>(visual.size());
        float x = fragments.get(0).x();
        for (Fragment fragment : visual) {
            placed.add(switch (fragment) {
                case TextFragment text ->
                        new TextFragment(text.text(), text.run(), text.face(), x, text.width(), text.size());
                case AtomicFragment atomic ->
                        new AtomicFragment(atomic.run(), x, atomic.width(), atomic.height(), atomic.baseline());
            });
            x += fragment.width();
        }
        return placed;
    }

    private static boolean isRightToLeft(Fragment fragment) {
        return fragment.run().style().rtl()
                || (fragment instanceof TextFragment text
                        && java.text.Bidi.requiresBidi(
                                text.text().toCharArray(), 0, text.text().length()));
    }

    /** Whether this element's {@code white-space} lets its lines wrap at all. */
    private static boolean wraps(ComputedStyle style) {
        String value = style.raw("white-space", "normal").trim().toLowerCase(java.util.Locale.ROOT);
        return !value.equals("pre") && !value.equals("nowrap");
    }

    private VisualLine finish(List<Fragment> fragments, float width) {
        Extent extent = extentOf(fragments);
        float leading = 0f;
        for (Fragment fragment : fragments) {
            if (fragment instanceof TextFragment text) {
                FaceChain chain = chains.apply(text.face());
                leading = Math.max(leading, extraLeading(text.run().style(), chain, text.size()));
            }
        }
        return new VisualLine(fragments, width, extent.ascent(), extent.descent(), leading);
    }

    /** How far a run of fragments reaches above and below the baseline they share. */
    public record Extent(float ascent, float descent) {

        public float height() {
            return ascent + descent;
        }
    }

    /**
     * The extent of some fragments: the tallest ascent and the deepest descent
     * among them, from the Faces' own metrics rather than a ratio of the font
     * size.
     *
     * <p>A whole line is measured this way, and so is the slice of one an inline
     * box covers — a box spanning two font sizes has to be as tall as the taller
     * of them, on each line it occupies.
     */
    public Extent extentOf(List<Fragment> fragments) {
        float ascent = 0f;
        float descent = 0f;
        for (Fragment fragment : fragments) {
            switch (fragment) {
                case TextFragment text -> {
                    FaceChain chain = chains.apply(text.face());
                    float size = text.size();
                    ascent = Math.max(ascent, chain.primary().lineAscentFraction() * size);
                    descent = Math.max(descent, Math.abs(chain.primary().descent(size)));
                }
                case AtomicFragment atomic -> {
                    ascent = Math.max(ascent, atomic.baseline());
                    descent = Math.max(descent, atomic.height() - atomic.baseline());
                }
            }
        }
        return new Extent(ascent, descent);
    }

    /**
     * The space {@code line-height} adds beyond the Face's own line box. A
     * declared line height smaller than the text does not clip it; it just adds
     * nothing.
     */
    private static float extraLeading(ComputedStyle style, FaceChain chain, float size) {
        float natural = chain.primary().normalLineBoxFraction() * size;
        float declared = style.lineHeight()
                .map(height -> switch (height) {
                    case ComputedStyle.LineHeight.Multiplier multiplier -> multiplier.times() * size;
                    case ComputedStyle.LineHeight.Absolute absolute -> style.resolve(absolute.length(), size);
                })
                .orElse(natural);
        return Math.max(0f, declared - natural);
    }

    private static float letterSpacing(ComputedStyle style) {
        return style.length("letter-spacing").map(length -> style.resolve(length, 0f)).orElse(0f);
    }

    private static float advance(FaceChain chain, String text, float size, float letterSpacing) {
        return chain.measure(text, size) + letterSpacing * text.codePointCount(0, text.length());
    }

    /**
     * Splits text at break opportunities, keeping each space attached to the
     * word before it so a space at a wrap point disappears with the line rather
     * than indenting the next one.
     */
    static List<String> splitIntoWords(String text) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                pieces.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            pieces.add(text.substring(start));
        }
        return pieces;
    }

    /**
     * The width of the space a fragment carries at its end.
     *
     * <p>A word keeps the space that followed it, so the last fragment of a
     * wrapped line ends in one. It is not drawn, and a justified line that
     * counted it would stop a space short of the right margin.
     */
    float trailingSpace(Fragment fragment) {
        if (!(fragment instanceof TextFragment text) || !text.text().endsWith(" ")) {
            return 0f;
        }
        return advance(chains.apply(text.face()), " ", text.size(), letterSpacing(text.run().style()));
    }

    /** The width a run of inline content would take if it were never wrapped. */
    public float unwrappedWidth(List<InlineRun> runs) {
        float width = 0f;
        for (InlineRun run : runs) {
            if (run.isText()) {
                int face = faceIndex.applyAsInt(run.style());
                width += advance(chains.apply(face), run.text(), run.style().fontSize(),
                        letterSpacing(run.style()));
            } else {
                width += atomics.measure(run, Float.MAX_VALUE).width();
            }
        }
        return width;
    }

    /** Where a line starts, given how the block aligns its text. */
    public static float alignmentOffset(TextAlign align, float lineWidth, float available) {
        float slack = Math.max(0f, available - lineWidth);
        return switch (align) {
            case RIGHT -> slack;
            case CENTER -> slack / 2f;
            case LEFT, JUSTIFY -> 0f;
        };
    }
}
