package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.box.InlineRun;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.style.TextAlign;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import com.wurstsoftware.htmltopdf4j.text.Standard14Face;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Greedy line breaking: a word is placed if it fits and starts a new line if it
 * does not, which is what the Expectations were recorded against.
 */
class LineBreakerTest {

    private static final FaceChain HELVETICA = FaceChain.of(Standard14Face.HELVETICA);

    /** A breaker with one Face and no atomic inlines, which is enough for text. */
    private static LineBreaker breaker() {
        return new LineBreaker(
                style -> 0,
                index -> HELVETICA,
                (run, available) -> new LineBreaker.Size(0f, 0f, 0f));
    }

    private static InlineRun text(String text) {
        return InlineRun.text(text, ComputedStyle.of(Map.of("font-size", "12pt")), null);
    }

    private static String textOf(LineBreaker.VisualLine line) {
        StringBuilder out = new StringBuilder();
        for (LineBreaker.Fragment fragment : line.fragments()) {
            if (fragment instanceof LineBreaker.TextFragment piece) {
                out.append(piece.text());
            }
        }
        return out.toString();
    }

    private static float measure(String text) {
        return HELVETICA.measure(text, 12f);
    }

    @Test
    void textThatFitsStaysOnOneLine() {
        List<LineBreaker.VisualLine> lines =
                breaker().breakLines(List.of(text("one two three")), 500f, 0f);

        assertEquals(1, lines.size());
        assertEquals("one two three", textOf(lines.get(0)).trim());
    }

    @Test
    void aWordThatDoesNotFitStartsTheNextLine() {
        float width = measure("one two ") + 1f;
        List<LineBreaker.VisualLine> lines =
                breaker().breakLines(List.of(text("one two three")), width, 0f);

        assertEquals(2, lines.size());
        assertEquals("one two", textOf(lines.get(0)).trim());
        assertEquals("three", textOf(lines.get(1)).trim());
    }

    @Test
    void breakingIsGreedyRatherThanBalanced() {
        // A paragraph-optimising breaker would even these two lines out; a
        // greedy one fills the first and leaves the remainder short.
        float width = measure("aaa bbb ccc ") + 1f;
        List<LineBreaker.VisualLine> lines =
                breaker().breakLines(List.of(text("aaa bbb ccc ddd")), width, 0f);

        assertEquals("aaa bbb ccc", textOf(lines.get(0)).trim());
        assertEquals("ddd", textOf(lines.get(1)).trim());
    }

    @Test
    void aWordWiderThanTheLineIsBrokenWithinItselfRatherThanOverflowingForever() {
        String word = "supercalifragilisticexpialidocious";
        List<LineBreaker.VisualLine> lines =
                breaker().breakLines(List.of(text(word)), measure("super"), 0f);

        assertTrue(lines.size() > 1, "an overlong word must be broken");
        assertEquals(word, lines.stream().map(LineBreakerTest::textOf).reduce("", String::concat),
                "breaking a word must not lose or duplicate any of it");
    }

    @Test
    void aWordThatExactlyFillsTheLineIsNotBrokenByARoundingError() {
        // A box sized to its own content round-trips text -> +padding -> width ->
        // -padding and can land an ulp low; without a tolerance the last letter
        // would fall onto a line of its own.
        String word = "Alpha";
        List<LineBreaker.VisualLine> lines =
                breaker().breakLines(List.of(text(word)), measure(word) - 0.001f, 0f);

        assertEquals(1, lines.size(), "expected the word to fit: " + lines.size() + " lines");
    }

    @Test
    void aFirstLineIndentShortensOnlyTheFirstLine() {
        float width = measure("one two ") + 1f;
        List<LineBreaker.VisualLine> indented =
                breaker().breakLines(List.of(text("one two three four")), width, measure("one "));

        assertEquals("one", textOf(indented.get(0)).trim());
        assertTrue(indented.get(0).fragments().get(0).x() > 0f, "the indent should offset the first line");
    }

    @Test
    void everyLineCarriesTheAscentAndDescentOfWhatIsOnIt() {
        LineBreaker.VisualLine line = breaker().breakLines(List.of(text("Hg")), 500f, 0f).get(0);

        assertTrue(line.ascent() > 0f);
        assertTrue(line.height() >= line.ascent() + line.descent());
    }

    @Test
    void anEmptyRunListProducesNoLines() {
        assertTrue(breaker().breakLines(List.of(), 500f, 0f).isEmpty());
    }

    @Test
    void fragmentsAdvanceAcrossTheLine() {
        LineBreaker.VisualLine line = breaker().breakLines(List.of(text("one two")), 500f, 0f).get(0);

        float previous = -1f;
        for (LineBreaker.Fragment fragment : line.fragments()) {
            assertTrue(fragment.x() > previous, "fragments should run left to right");
            previous = fragment.x();
        }
    }

    @Test
    void theUnwrappedWidthIsWhatTheRunsWouldTakeOnOneLine() {
        assertEquals(measure("one two"), breaker().unwrappedWidth(List.of(text("one two"))), 0.5f);
    }

    // --- Alignment ---------------------------------------------------------

    @Test
    void leftAlignmentDoesNotOffsetTheLine() {
        assertEquals(0f, LineBreaker.alignmentOffset(TextAlign.LEFT, 100f, 300f));
    }

    @Test
    void centringSplitsTheSlackEvenly() {
        assertEquals(100f, LineBreaker.alignmentOffset(TextAlign.CENTER, 100f, 300f));
    }

    @Test
    void rightAlignmentPushesTheLineToTheEnd() {
        assertEquals(200f, LineBreaker.alignmentOffset(TextAlign.RIGHT, 100f, 300f));
    }

    @Test
    void aLineWiderThanTheSpaceIsNotPulledBackwards() {
        assertFalse(LineBreaker.alignmentOffset(TextAlign.CENTER, 400f, 300f) < 0f,
                "an overflowing line should not be pulled off the left edge");
    }
}
