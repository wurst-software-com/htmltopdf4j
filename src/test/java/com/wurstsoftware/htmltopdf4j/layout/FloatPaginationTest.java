package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.paint.Rect;
import org.junit.jupiter.api.Test;

/**
 * A float meeting a Page boundary.
 *
 * <p>A float is placed whole: it is measured before it is laid out, so it moves
 * to the next Page rather than being divided by the boundary, and the band it
 * leaves behind belongs to the Page it is painted on. A float too tall for a
 * Page of its own has to be divided anyway, and then it excludes a band on
 * every Page it crosses.
 */
class FloatPaginationTest {

    /** The bottom of the content area of an A4 Page, in PDF coordinates. */
    private static final float CONTENT_BOTTOM = 48f;

    /** Enough content to leave about 95pt of the first Page free. */
    private static final String FILLER = "<div style='height:650pt'>TOP</div>";

    private static final String ASIDE =
            "<div style='float:left; width:150pt; height:200pt; background:#ff0'>ASIDE</div>";

    /** Words enough that the paragraph beside a float runs over several lines. */
    private static final String PARAGRAPH = "<p>beside one two three four five six seven eight nine ten "
            + "eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty.</p>";

    /** The painted box of the float, told apart from any other fill by its width. */
    private static Rect floatBox(Laid laid) {
        return laid.fills().stream()
                .filter(rect -> Math.abs(rect.width() - 150f) < 0.5f)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the float painted no box; filled: " + laid.fills()));
    }

    @Test
    void aFloatThatWouldBeDividedStartsOnTheNextPage() {
        Laid laid = Laid.of(FILLER + ASIDE + PARAGRAPH);

        assertEquals(1, laid.pageOf("ASIDE"), "the float does not fit under the filler");
    }

    @Test
    void theBandBelongsToThePageTheFloatIsPaintedOn() {
        Laid laid = Laid.of(FILLER + ASIDE + PARAGRAPH);

        assertEquals(1, laid.pageOf("beside"), "the text follows the float");
        assertTrue(laid.text("beside").x() > 150f,
                "and wraps beside it rather than running under it");
    }

    @Test
    void theFlowResumesAtTheTopOfThePageTheFloatMovedTo() {
        Laid laid = Laid.of(FILLER + ASIDE + PARAGRAPH);

        // A cursor carried over from the previous Page would drop this line to
        // the foot of the new one, and leave the Page all but empty.
        assertTrue(laid.text("beside").y() > 700f,
                "the line after the float should sit near the top of its Page, not at " + laid.text("beside").y());
    }

    @Test
    void aFloatNeverPaintsBelowTheContentBottom() {
        Laid laid = Laid.of("<div style='height:620pt'>TOP</div>"
                + "<div style='float:left; width:150pt; height:90pt; background:#ff0'>ASIDE</div>");

        assertTrue(floatBox(laid).y() >= CONTENT_BOTTOM - 0.5f,
                "the float's box should not cross the bottom margin: " + floatBox(laid));
    }

    @Test
    void aDeclaredHeightSizesThePaintedBoxAndNotOnlyTheBand() {
        Laid laid = Laid.of(
                "<div style='float:left; width:150pt; height:90pt; background:#ff0'>ASIDE</div>" + PARAGRAPH);

        assertEquals(90f, floatBox(laid).height(), 0.5f, "the box is as tall as it was declared");
    }

    @Test
    void aMinimumHeightSizesThePaintedBoxTheSameWay() {
        Laid laid = Laid.of(
                "<div style='float:left; width:150pt; min-height:90pt; background:#ff0'>ASIDE</div>" + PARAGRAPH);

        assertEquals(90f, floatBox(laid).height(), 0.5f, "a minimum its content does not reach still applies");
    }

    @Test
    void aFloatTallerThanItsDeclaredHeightIsPaintedAsTallAsItsContent() {
        StringBuilder lines = new StringBuilder();
        for (int i = 1; i <= 6; i++) {
            lines.append("<p style='margin:0; line-height:20pt'>L").append(i).append("</p>");
        }
        Laid laid = Laid.of("<div style='float:left; width:150pt; height:20pt; background:#ff0'>"
                + lines + "</div>" + PARAGRAPH);

        assertTrue(floatBox(laid).height() > 100f,
                "the declared height is a minimum, not a cap: " + floatBox(laid).height());
    }

    @Test
    void aFloatTallerThanAPageExcludesABandOnEveryPageItCrosses() {
        Laid laid = Laid.of("<div style='float:left; width:150pt; height:1200pt; background:#ff0'>ASIDE</div>"
                + PARAGRAPH.repeat(14));

        assertTrue(laid.pageCount() > 1, "the text beside it runs past the first Page");
        for (Laid.Line line : laid.lines()) {
            if (line.page < 2 && line.text().contains("beside")) {
                assertTrue(line.x() > 150f,
                        "a line on Page " + line.page + " still runs beside the float, at x " + line.x());
            }
        }
    }

    @Test
    void flowResumesOnTheStartPageOfAFloatTallerThanAPage() {
        Laid laid = Laid.of("<div style='float:left; width:150pt; height:1200pt; background:#ff0'>ASIDE</div>"
                + PARAGRAPH);

        assertEquals(0, laid.pageOf("beside"),
                "the flow the float was taken out of carries on where it was");
    }

    @Test
    void aFloatTallerThanAPageEndsWhereItsContentEnds() {
        // The box is measured from the Page the float started on, while the
        // cursor that laid its content out belongs to the Page it ended on.
        // Reading one as the other reserves the whole of the last Page, and
        // narrows the lines below the float for nothing.
        StringBuilder lines = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            lines.append("<p style='margin:0; line-height:20pt'>L").append(i).append("</p>");
        }
        Laid laid = Laid.of("<div style='float:left; width:150pt; background:#ff0'>" + lines + "</div>"
                + PARAGRAPH.repeat(20));

        Laid.Line last = laid.lines().stream()
                .filter(line -> line.text().equals("L50"))
                .findFirst()
                .orElseThrow();
        for (Laid.Line line : laid.lines()) {
            if (line.page == last.page && line.y < last.y - 20f) {
                assertTrue(line.x() < 60f,
                        "below the float's last line the Page is free, so this one should not start at x "
                                + line.x() + ": " + line.text());
            }
        }
    }
}
