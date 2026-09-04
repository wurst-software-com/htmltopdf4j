package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The background and borders of an inline box.
 *
 * <p>An inline box is not one rectangle: it is one per line it occupies, and the
 * near edges belong to the first and last of them — which is what makes a
 * wrapped "chip" read as one chip cut in two rather than as two chips.
 */
class InlineDecorationTest {

    private static final String CHIP =
            "background:#e8f1f8; border:1pt solid #b9d3e8";

    /** The fills a chip left behind, told apart from anything else by their colour. */
    private static List<Rect> chipFills(Laid laid) {
        List<Rect> fills = new java.util.ArrayList<>();
        for (PaintCommand command : laid.commands()) {
            if (command instanceof PaintCommand.FillRect fill) {
                fills.add(fill.rect());
            }
        }
        return fills;
    }

    private static long strokedLines(Laid laid) {
        return laid.commands().stream().filter(PaintCommand.StrokeLine.class::isInstance).count();
    }

    @Test
    void anInlineBoxPaintsItsBackgroundBehindItsText() {
        Laid laid = Laid.of("<p>PO <span style='background:#e8f1f8'>CHIP</span> tail</p>");

        List<Rect> fills = chipFills(laid);
        assertEquals(1, fills.size(), "one background, behind the one line the chip is on");
        assertEquals(laid.text("CHIP").x(), fills.get(0).x(), 0.5f, "and it starts where the text does");
    }

    @Test
    void theTextIsPaintedOverItsOwnBackground() {
        Laid laid = Laid.of("<p><span style='background:#e8f1f8'>CHIP</span></p>");
        List<PaintCommand> commands = laid.commands();

        int fill = -1;
        int text = -1;
        for (int i = 0; i < commands.size(); i++) {
            if (fill < 0 && commands.get(i) instanceof PaintCommand.FillRect) {
                fill = i;
            }
            if (text < 0 && commands.get(i) instanceof PaintCommand.Text) {
                text = i;
            }
        }
        assertTrue(fill >= 0 && fill < text, "the background is emitted before the text it sits behind");
    }

    @Test
    void aWrappedInlineBoxPaintsOneBackgroundPerLine() {
        Laid laid = Laid.of("<p style='width:200pt'><span style='background:#e8f1f8'>"
                + "one two three four five six seven eight nine ten eleven twelve thirteen</span></p>");

        assertTrue(chipFills(laid).size() >= 2,
                "a chip that wraps is painted once per line, not once overall");
    }

    @Test
    void theNearEdgesBelongToTheFirstAndLastLineOnly() {
        Laid laid = Laid.of("<p style='width:200pt'><span style='" + CHIP + "'>"
                + "one two three four five six seven eight nine ten eleven twelve thirteen</span></p>");

        assertEquals(2, chipFills(laid).size(), "the chip takes two lines");
        // Top and bottom on both lines, left on the first, right on the last.
        assertEquals(6, strokedLines(laid), "six sides for a chip cut in two");
    }

    @Test
    void paddingWidensThePaintedAreaWithoutMovingTheTextBelowIt() {
        Laid bare = Laid.of("<p><span style='background:#e8f1f8'>CHIP</span></p><p>BELOW</p>");
        Laid padded = Laid.of(
                "<p><span style='background:#e8f1f8; padding:2pt 6pt'>CHIP</span></p><p>BELOW</p>");

        assertEquals(chipFills(bare).get(0).width() + 12f, chipFills(padded).get(0).width(), 0.5f,
                "horizontal padding widens the box");
        assertEquals(chipFills(bare).get(0).height() + 4f, chipFills(padded).get(0).height(), 0.5f,
                "vertical padding makes it taller");
        assertEquals(bare.text("BELOW").y(), padded.text("BELOW").y(), 0.5f,
                "but neither moves the line below it: vertical padding on an inline box does not "
                        + "change the line box");
    }

    @Test
    void horizontalPaddingPushesTheTextAfterTheChipAlong() {
        Laid bare = Laid.of("<p><span style='background:#e8f1f8'>CHIP</span>tail</p>");
        Laid padded = Laid.of("<p><span style='background:#e8f1f8; padding:0 6pt'>CHIP</span>tail</p>");

        assertEquals(bare.text("tail").x() + 12f, padded.text("tail").x(), 0.5f,
                "the padding is space on the line, not an overlap");
    }

    @Test
    void aBorderRadiusRoundsTheChip() {
        Laid laid = Laid.of(
                "<p><span style='background:#e8f1f8; border-radius:8pt'>CHIP</span></p>");

        assertTrue(laid.commands().stream().anyMatch(PaintCommand.FillRoundedRect.class::isInstance),
                "a radius on an inline box is honoured, the same as on a block");
    }

    @Test
    void aBoxOverTwoFontSizesCoversTheTallerOne() {
        Laid oneSize = Laid.of("<p><span style='background:#e8f1f8'>small small</span></p>");
        Laid mixed = Laid.of("<p><span style='background:#e8f1f8'>small "
                + "<b style='font-size:24pt'>BIG</b></span></p>");

        Rect covering = chipFills(mixed).get(0);
        assertTrue(covering.height() > chipFills(oneSize).get(0).height() + 10f,
                "a box holding a 24pt run is not the height of its 12pt one");
        assertTrue(covering.y() + covering.height() >= mixed.text("BIG").y() + 24f * 0.7f,
                "the big text's ascent is inside its own background, not above it");
        assertTrue(covering.y() <= mixed.text("BIG").y() - 24f * 0.15f,
                "and its descent is inside too");
    }

    @Test
    void eachLineIsDrawnToWhatThatLineHolds() {
        // The tall run is on the second line, so the first line's rectangle is
        // the short one and the second line's is the tall one.
        Laid laid = Laid.of("<p style='width:120pt'><span style='background:#e8f1f8'>"
                + "one two three four five <b style='font-size:24pt'>BIG</b></span></p>");

        List<Rect> fills = chipFills(laid);
        assertTrue(fills.size() >= 2, "the chip should take more than one line");
        Rect last = fills.get(fills.size() - 1);
        Rect first = fills.get(0);
        assertTrue(last.height() > first.height() + 10f,
                "the line carrying the 24pt run is the taller rectangle");
    }

    @Test
    void theBoxIsAsTallAsItsFaceSaysRatherThanAFixedRatio() {
        Laid laid = Laid.of("<p><span style='background:#e8f1f8; font-size:20pt'>CHIP</span></p>");
        Rect fill = chipFills(laid).get(0);

        // Helvetica's own ascent and descent, at 20pt, from the Face's metrics.
        com.wurstsoftware.htmltopdf4j.text.Face face =
                com.wurstsoftware.htmltopdf4j.text.Standard14Face.HELVETICA;
        float ascent = face.lineAscentFraction() * 20f;
        float descent = Math.abs(face.descent(20f));

        assertEquals(ascent + descent, fill.height(), 0.5f,
                "the rectangle is the Face's ascent plus its descent, not a fixed 1.2 of the size");
        assertEquals(laid.text("CHIP").y() - descent, fill.y(), 0.5f,
                "and it sits a real descent below the baseline");
    }

    @Test
    void anUndecoratedInlineBoxPaintsNothing() {
        Laid laid = Laid.of("<p>plain <span>text</span> here</p>");

        assertEquals(0, chipFills(laid).size(), "an inline box with nothing to paint costs nothing");
    }
}
