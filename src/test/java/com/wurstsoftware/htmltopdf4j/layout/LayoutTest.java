package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.RenderOptions;
import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import com.wurstsoftware.htmltopdf4j.text.Standard14Face;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Flow: what Layout puts where.
 *
 * <p>Assertions are relative — this is above that, this is wider than that —
 * rather than absolute point positions, because the Expectations were never
 * about matching Chromium to the point and a test that pinned exact coordinates
 * would break on every legitimate metric improvement.
 */
class LayoutTest {

    private static final float TOLERANCE = 0.5f;

    // --- Block flow --------------------------------------------------------

    @Test
    void blocksStackDownThePage() {
        Laid laid = Laid.of("<p>First</p><p>Second</p><p>Third</p>");

        assertTrue(laid.text("First").y() > laid.text("Second").y());
        assertTrue(laid.text("Second").y() > laid.text("Third").y());
    }

    @Test
    void inlineContentFlowsAlongOneLineBeforeWrapping() {
        Laid laid = Laid.of("<p>alpha <b>beta</b></p>");

        List<PaintCommand.Text> texts = laid.texts();
        assertEquals(texts.get(0).y(), texts.get(1).y(), TOLERANCE);
        assertTrue(texts.get(1).x() > texts.get(0).x());
    }

    @Test
    void aLongParagraphWrapsOntoSeveralLines() {
        Laid laid = Laid.of("<p>" + "word ".repeat(200) + "</p>");

        assertTrue(laid.lines().size() > 5, "expected several wrapped lines");
    }

    @Test
    void contentTallerThanOnePageContinuesOntoTheNext() {
        Laid laid = Laid.of("<p>" + "word ".repeat(4000) + "</p>");

        assertTrue(laid.pageCount() > 1, "expected pagination");
        assertFalse(laid.pages().get(1).isEmpty());
    }

    @Test
    void aPageBreakStartsTheNextBlockOnAFreshPage() {
        Laid laid = Laid.of(
                "<p>Before</p><div style='page-break-before: always'>After</div>");

        assertEquals(0, laid.pageOf("Before"));
        assertEquals(1, laid.pageOf("After"));
    }

    @Test
    void marginsSeparateAdjacentBlocks() {
        Laid tight = Laid.of("<div style='margin:0'>One</div><div style='margin:0'>Two</div>");
        Laid loose = Laid.of("<div style='margin:0'>One</div><div style='margin-top:60pt'>Two</div>");

        float tightGap = tight.text("One").y() - tight.text("Two").y();
        float looseGap = loose.text("One").y() - loose.text("Two").y();
        assertTrue(looseGap > tightGap + 50f, "margin-top did not push the second block down");
    }

    @Test
    void displayNoneRemovesTheContentEntirely() {
        Laid laid = Laid.of("<p>Shown</p><p style='display:none'>Hidden</p>");

        assertTrue(laid.draws("Shown"));
        assertFalse(laid.draws("Hidden"));
    }

    // --- Page geometry -----------------------------------------------------

    @Test
    void textStartsInsideTheLeftMargin() {
        RenderOptions options = RenderOptions.builder().margins(72f, 72f, 72f, 72f).build();
        Laid laid = Laid.of("<p>Edge</p>", options);

        assertEquals(72f, laid.text("Edge").x(), TOLERANCE);
    }

    @Test
    void aWiderMarginLeavesLessRoomSoTheSameTextWrapsSooner() {
        String html = "<p>" + "word ".repeat(60) + "</p>";
        int narrow = Laid.of(html, RenderOptions.builder().margins(24f, 24f, 24f, 24f).build())
                .lines().size();
        int wide = Laid.of(html, RenderOptions.builder().margins(24f, 200f, 24f, 200f).build())
                .lines().size();

        assertTrue(wide > narrow, "narrower content box should produce more lines");
    }

    @Test
    void layoutRunsDownwardsButPaintsInPdfCoordinates() {
        Laid laid = Laid.of("<p>Top</p>");

        // y-up: the first line sits high on the Page, not near zero.
        assertTrue(laid.text("Top").y() > 700f, "expected a y near the top of A4");
    }

    // --- Alignment ---------------------------------------------------------

    private static final String PROSE =
            "One two three four five six seven eight nine ten eleven twelve "
                    + "thirteen fourteen fifteen sixteen seventeen eighteen.";

    @Test
    void justifyStretchesEveryLineButTheLast() {
        Laid justified = Laid.of("<p style='width:200pt; text-align:justify'>" + PROSE + "</p>");
        Laid ragged = Laid.of("<p style='width:200pt'>" + PROSE + "</p>");

        List<Laid.Line> stretched = justified.lines();
        List<Laid.Line> plain = ragged.lines();
        assertTrue(stretched.size() > 1, "the prose should wrap");
        assertEquals(plain.size(), stretched.size(), "justifying must not change where lines break");

        for (int i = 0; i < stretched.size() - 1; i++) {
            assertTrue(lastX(stretched.get(i)) > lastX(plain.get(i)),
                    "line " + i + " should reach further right when justified");
        }
    }

    @Test
    void theLastLineOfAJustifiedBlockIsLeftAsItFalls() {
        Laid justified = Laid.of("<p style='width:200pt; text-align:justify'>" + PROSE + "</p>");
        Laid ragged = Laid.of("<p style='width:200pt'>" + PROSE + "</p>");

        List<Laid.Line> stretched = justified.lines();
        List<Laid.Line> plain = ragged.lines();
        assertEquals(
                lastX(plain.get(plain.size() - 1)),
                lastX(stretched.get(stretched.size() - 1)),
                TOLERANCE);
    }

    @Test
    void aJustifiedLineStillStartsAtTheLeftEdge() {
        Laid justified = Laid.of("<p style='width:200pt; text-align:justify'>" + PROSE + "</p>");

        for (Laid.Line line : justified.lines()) {
            assertEquals(48f, line.x(), TOLERANCE, "justifying spreads the gaps, it does not indent");
        }
    }

    @Test
    void justificationWidensWordGapsAndNotWordsThemselves() {
        // A word split across two styles is still one word: `un<b>break</b>able`
        // is three fragments with no space between them, and stretching there
        // would print "un break able".
        String prose = "alpha un<b>break</b>able beta gamma delta epsilon zeta eta theta"
                + " iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon.";
        Laid justified = Laid.of("<p style='width:200pt; text-align:justify'>" + prose + "</p>");
        Laid ragged = Laid.of("<p style='width:200pt'>" + prose + "</p>");

        assertEquals(
                ragged.text("break").x() - ragged.text("un").x(),
                justified.text("break").x() - justified.text("un").x(),
                TOLERANCE,
                "the pieces of one word must stay touching");
    }

    @Test
    void aJustifiedLineReachesTheRightEdge() {
        Laid laid = Laid.of("<p style='width:200pt; text-align:justify'>" + PROSE + "</p>");

        List<Laid.Line> lines = laid.lines();
        assertTrue(lines.size() > 2, "the prose should wrap");
        for (int i = 0; i < lines.size() - 1; i++) {
            PaintCommand.Text last = lines.get(i).runs.get(lines.get(i).runs.size() - 1);
            float right = last.x()
                    + Standard14Face.HELVETICA.measure(last.text().stripTrailing(), last.fontSize());
            assertEquals(48f + 200f, right, 1f, "line " + i + " should end flush with the right edge");
        }
    }

    private static float lastX(Laid.Line line) {
        return line.runs.get(line.runs.size() - 1).x();
    }


    @Test
    void centredTextStartsFurtherInThanLeftAlignedText() {
        Laid left = Laid.of("<p style='text-align:left'>Word</p>");
        Laid centre = Laid.of("<p style='text-align:center'>Word</p>");

        assertTrue(centre.text("Word").x() > left.text("Word").x());
    }

    @Test
    void rightAlignedTextEndsAtTheRightEdge() {
        Laid centre = Laid.of("<p style='text-align:center'>Word</p>");
        Laid right = Laid.of("<p style='text-align:right'>Word</p>");

        assertTrue(right.text("Word").x() > centre.text("Word").x());
    }

    // --- Backgrounds and borders -------------------------------------------

    @Test
    void aBackgroundColourFillsTheBoxItWasDeclaredOn() {
        Laid laid = Laid.of("<div style='background:#ff0000; height:100pt'>Filled</div>");

        assertTrue(laid.fills().stream().anyMatch(rect -> rect.height() >= 99f),
                "expected a fill as tall as the box");
    }

    @Test
    void aBackgroundIsPaintedBeforeTheTextItSitsBehind() {
        Laid laid = Laid.of("<div style='background:#ff0000'>Over</div>");

        List<PaintCommand> commands = laid.commands();
        int fill = indexOfFirst(commands, PaintCommand.FillRect.class);
        int text = indexOfFirst(commands, PaintCommand.Text.class);
        assertTrue(fill >= 0 && fill < text, "the background must be painted first");
    }

    @Test
    void aBorderIsStroked() {
        Laid laid = Laid.of("<div style='border:2pt solid #000000'>Boxed</div>");

        assertTrue(laid.commands().stream().anyMatch(
                command -> command instanceof PaintCommand.StrokeRect
                        || command instanceof PaintCommand.StrokeLine));
    }

    @Test
    void aGradientBackgroundIsPaintedAsManyBands() {
        Laid laid = Laid.of(
                "<div style='height:200pt; background:linear-gradient(#ff0000, #0000ff)'>G</div>");

        assertTrue(laid.fills().size() > 8, "expected the gradient to be banded");
    }

    @Test
    void aTallerGradientGetsMoreBandsThanAShortOne() {
        int shortBands = Laid.of(
                "<div style='height:40pt; background:linear-gradient(#f00,#00f)'>G</div>").fills().size();
        int tallBands = Laid.of(
                "<div style='height:400pt; background:linear-gradient(#f00,#00f)'>G</div>").fills().size();

        assertTrue(tallBands > shortBands, "band count should follow the box, not a constant");
    }

    // --- Floats ------------------------------------------------------------

    @Test
    void textFlowsBesideALeftFloatRatherThanUnderIt() {
        Laid laid = Laid.of(
                "<div style='float:left; width:150pt; height:60pt'>F</div>"
                        + "<p>beside</p>");

        assertTrue(laid.text("beside").x() > 150f, "text should start past the float");
    }

    @Test
    void textBelowAFloatReturnsToTheFullWidth() {
        Laid laid = Laid.of(
                "<div style='float:left; width:150pt; height:20pt'>F</div>"
                        + "<p>" + "word ".repeat(120) + "</p>");

        List<Laid.Line> lines = laid.lines();
        float firstX = lines.get(1).x();
        float lastX = lines.get(lines.size() - 1).x();
        assertTrue(lastX < firstX, "later lines should no longer be indented by the float");
    }

    @Test
    void clearDropsABlockBelowTheFloat() {
        Laid floated = Laid.of(
                "<div style='float:left; width:150pt; height:120pt'>F</div><p>after</p>");
        Laid cleared = Laid.of(
                "<div style='float:left; width:150pt; height:120pt'>F</div>"
                        + "<p style='clear:left'>after</p>");

        assertTrue(cleared.text("after").y() < floated.text("after").y(),
                "the cleared block should sit lower");
    }

    // --- Positioning -------------------------------------------------------

    @Test
    void anAbsolutelyPositionedBoxLandsWhereItsOffsetsPutIt() {
        RenderOptions options = RenderOptions.builder().margins(48f, 48f, 48f, 48f).build();
        Laid laid = Laid.of(
                "<div style='position:absolute; top:300pt; left:200pt'>Pinned</div><p>Flow</p>",
                options);

        // In paged media the initial containing block is the page area, which
        // starts inside the page margins — so `left: 200pt` is 200pt in from
        // there, not from the paper's edge.
        assertEquals(48f + 200f, laid.text("Pinned").x(), 2f);
        assertTrue(laid.text("Pinned").y() < laid.text("Flow").y());
    }

    @Test
    void anAbsolutelyPositionedBoxDoesNotPushTheFlowAside() {
        Laid without = Laid.of("<p>Flow</p>");
        Laid with = Laid.of(
                "<div style='position:absolute; top:300pt; left:0'>Pinned</div><p>Flow</p>");

        assertEquals(without.text("Flow").y(), with.text("Flow").y(), TOLERANCE);
    }

    @Test
    void positionedBoxesArePaintedInZIndexOrder() {
        Laid laid = Laid.of(
                "<div style='position:absolute; top:10pt; z-index:5'>High</div>"
                        + "<div style='position:absolute; top:40pt; z-index:1'>Low</div>");

        List<String> order = laid.texts().stream().map(PaintCommand.Text::text).toList();
        assertTrue(order.indexOf("Low") < order.indexOf("High"),
                "a lower z-index must be painted first: " + order);
    }

    @Test
    void aFixedBoxRepeatsOnEveryPage() {
        Laid laid = Laid.of(
                "<div style='position:fixed; top:20pt'>Running</div>"
                        + "<p>" + "word ".repeat(4000) + "</p>");

        assertTrue(laid.pageCount() > 1);
        for (int page = 0; page < laid.pageCount(); page++) {
            int index = page;
            assertTrue(laid.texts(index).stream().anyMatch(text -> text.text().contains("Running")),
                    "page " + page + " is missing the fixed box");
        }
    }

    // --- Links and anchors -------------------------------------------------

    @Test
    void aLinkLeavesAClickableArea() {
        Laid laid = Laid.of("<p><a href='https://example.com'>Click</a></p>");

        List<LinkArea> areas = laid.pages().get(0).linkAreas();
        assertEquals(1, areas.size());
        assertTrue(areas.get(0).rect().width() > 0f);
    }

    @Test
    void adjacentLinkedWordsOnOneLineShareOneRect() {
        // Each word is its own drawn run, so without merging a three-word link
        // would put three separate annotations over one phrase.
        Laid laid = Laid.of("<p><a href='https://example.com'>Click right here</a></p>");

        List<LinkArea> areas = laid.pages().get(0).linkAreas();
        assertEquals(1, areas.size());
        assertTrue(areas.get(0).rect().width() > laid.text("Click").text().length() * 2f);
    }

    @Test
    void aLinkThatWrapsGetsARectPerLine() {
        Laid laid = Laid.of(
                "<p style='width: 120pt'><a href='https://example.com'>"
                        + "One two three four five six seven eight nine ten</a></p>");

        List<LinkArea> areas = laid.pages().get(0).linkAreas();
        assertTrue(areas.size() > 1, "a wrapped link cannot be one rectangle");
        assertEquals(
                areas.size(),
                areas.stream().map(area -> area.rect().y()).distinct().count(),
                "one rect per line, not one per word");
    }

    @Test
    void twoDifferentLinksSideBySideStayApart() {
        Laid laid = Laid.of(
                "<p><a href='https://one.example'>One</a> <a href='https://two.example'>Two</a></p>");

        assertEquals(2, laid.pages().get(0).linkAreas().size());
    }

    @Test
    void anIdBecomesAnAnchorAndAHeadingBecomesAnOutlineEntry() {
        Laid laid = Laid.of("<h1 id='intro'>Introduction</h1>");

        List<AnchorMark> anchors = laid.pages().get(0).anchors();
        assertTrue(anchors.stream().anyMatch(anchor -> "intro".equals(anchor.name())));
        assertTrue(anchors.stream().anyMatch(AnchorMark::isOutlineEntry));
    }

    // --- Flex --------------------------------------------------------------

    @Test
    void flexItemsSitSideBySide() {
        Laid laid = Laid.of(
                "<div style='display:flex'><div>Left</div><div>Right</div></div>");

        assertEquals(laid.text("Left").y(), laid.text("Right").y(), TOLERANCE);
        assertTrue(laid.text("Right").x() > laid.text("Left").x());
    }

    @Test
    void aFlexColumnStacksItsItems() {
        Laid laid = Laid.of(
                "<div style='display:flex; flex-direction:column'><div>Up</div><div>Down</div></div>");

        assertTrue(laid.text("Up").y() > laid.text("Down").y());
    }

    @Test
    void inlineFlexChildrenAreBlockifiedSoNoneGoMissing() {
        Laid laid = Laid.of(
                "<div style='display:flex'><span>One</span><span>Two</span><span>Three</span></div>");

        assertTrue(laid.draws("One") && laid.draws("Two") && laid.draws("Three"),
                "drawn: " + laid.drawnText());
    }

    @Test
    void justifyContentPushesTheItemsAcross() {
        Laid start = Laid.of("<div style='display:flex'><div>A</div></div>");
        Laid end = Laid.of(
                "<div style='display:flex; justify-content:flex-end'><div>A</div></div>");

        assertTrue(end.text("A").x() > start.text("A").x());
    }

    @Test
    void orderRearrangesFlexItemsWithoutChangingTheSource() {
        Laid laid = Laid.of(
                "<div style='display:flex'>"
                        + "<div style='order:2'>Second</div><div style='order:1'>First</div></div>");

        assertTrue(laid.text("First").x() < laid.text("Second").x());
    }

    @Test
    void aMinimumHeightHoldsAShortBlockOpen() {
        Laid laid = Laid.of(
                "<div style='min-height: 100pt'>Short</div><p>Below</p>");

        assertTrue(laid.text("Short").y() - laid.text("Below").y() > 100f,
                "the following block should clear the reserved height");
    }

    @Test
    void aMinimumHeightSmallerThanTheContentChangesNothing() {
        Laid tall = Laid.of("<div style='min-height: 4pt'>Short</div><p>Below</p>");
        Laid plain = Laid.of("<div>Short</div><p>Below</p>");

        assertEquals(
                plain.text("Short").y() - plain.text("Below").y(),
                tall.text("Short").y() - tall.text("Below").y(),
                TOLERANCE);
    }

    // --- Grid --------------------------------------------------------------

    @Test
    void gridItemsFillTheirColumnsBeforeStartingANewRow() {
        Laid laid = Laid.of(
                "<div style='display:grid; grid-template-columns: 1fr 1fr'>"
                        + "<div>A</div><div>B</div><div>C</div></div>");

        assertEquals(laid.text("A").y(), laid.text("B").y(), TOLERANCE);
        assertTrue(laid.text("C").y() < laid.text("A").y());
        assertEquals(laid.text("A").x(), laid.text("C").x(), TOLERANCE);
    }

    @Test
    void repeatExpandsIntoThatManyTracks() {
        Laid laid = Laid.of(
                "<div style='display:grid; grid-template-columns: repeat(3, 1fr)'>"
                        + "<div>A</div><div>B</div><div>C</div><div>D</div></div>");

        assertEquals(laid.text("A").y(), laid.text("C").y(), TOLERANCE);
        assertTrue(laid.text("D").y() < laid.text("A").y());
    }

    @Test
    void aDefiniteTrackTakesItsWidthAndTheFractionsShareTheRest() {
        Laid laid = Laid.of(
                "<div style='display:grid; grid-template-columns: 100pt 1fr'>"
                        + "<div>A</div><div>B</div></div>");

        assertEquals(100f, laid.text("B").x() - laid.text("A").x(), 2f);
    }

    @Test
    void aFixedRowTrackSetsTheRowHeightRatherThanTheContent() {
        Laid laid = Laid.of(
                "<div style='display:grid; grid-template-columns: 1fr 1fr;"
                        + " grid-template-rows: 80pt 40pt'>"
                        + "<div>A</div><div>B</div><div>C</div><div>D</div></div>");

        // The first row is 80pt tall whatever its one line of text needs.
        assertEquals(80f, laid.text("A").y() - laid.text("C").y(), 1f);
    }

    @Test
    void aFractionalRowTrackSharesTheContainersDefiniteHeight() {
        Laid laid = Laid.of(
                "<div style='display:grid; grid-template-columns: 1fr;"
                        + " grid-template-rows: 1fr 2fr; height: 180pt'>"
                        + "<div>A</div><div>B</div></div>");

        assertEquals(60f, laid.text("A").y() - laid.text("B").y(), 1f);
    }

    @Test
    void anAutoRowStillTakesItsHeightFromItsContent() {
        Laid laid = Laid.of(
                "<div style='display:grid; grid-template-columns: 1fr;"
                        + " grid-template-rows: auto auto'>"
                        + "<div>A</div><div>B</div></div>");

        assertTrue(laid.text("A").y() - laid.text("B").y() < 40f);
    }

    @Test
    void alignItemsCentresAnItemInATallerRow() {
        Laid laid = Laid.of(
                "<div style='display:grid; grid-template-columns: 1fr;"
                        + " grid-template-rows: 100pt; align-items: center'>"
                        + "<div>A</div></div>"
                        + "<div style='display:grid; grid-template-columns: 1fr;"
                        + " grid-template-rows: 100pt'>"
                        + "<div>B</div></div>");

        // A sits half a track lower than B, which is still at the track's top.
        float top = laid.text("B").y() + 100f;
        assertTrue(laid.text("A").y() < top - 30f && laid.text("A").y() > top - 60f,
                "expected A near the middle of its track, was " + laid.text("A").y());
    }

    @Test
    void alignSelfOverridesTheContainersAlignItems() {
        Laid laid = Laid.of(
                "<div style='display:grid; grid-template-columns: 1fr 1fr;"
                        + " grid-template-rows: 100pt; align-items: center'>"
                        + "<div style='align-self: flex-start'>A</div>"
                        + "<div style='align-self: flex-end'>B</div></div>");

        assertTrue(laid.text("A").y() - laid.text("B").y() > 60f,
                "start and end of a 100pt track should be far apart");
    }

    // --- Tables ------------------------------------------------------------

    @Test
    void tableCellsLineUpInRowsAndColumns() {
        Laid laid = Laid.of(
                "<table><tr><td>A</td><td>B</td></tr><tr><td>C</td><td>D</td></tr></table>");

        assertEquals(laid.text("A").y(), laid.text("B").y(), TOLERANCE);
        assertEquals(laid.text("A").x(), laid.text("C").x(), TOLERANCE);
        assertTrue(laid.text("C").y() < laid.text("A").y());
    }

    @Test
    void aRowspanKeepsTheRowsBelowItAligned() {
        Laid laid = Laid.of(
                "<table>"
                        + "<tr><td rowspan='2'>Span</td><td>B</td></tr>"
                        + "<tr><td>C</td></tr></table>");

        // C belongs in the second column, under B, not under the spanning cell.
        assertEquals(laid.text("B").x(), laid.text("C").x(), TOLERANCE);
        assertTrue(laid.text("C").y() < laid.text("B").y());
    }

    @Test
    void aColspanWidensTheCellAcrossItsColumns() {
        Laid laid = Laid.of(
                "<table>"
                        + "<tr><td colspan='2'>Wide</td></tr>"
                        + "<tr><td>A</td><td>B</td></tr></table>");

        assertEquals(laid.text("A").x(), laid.text("Wide").x(), TOLERANCE);
        assertTrue(laid.text("B").x() > laid.text("Wide").x());
    }

    private static Laid cellAligned(String alignment) {
        return Laid.of(
                "<style>td.mark { vertical-align: " + alignment + "; }</style>"
                        + "<table><tr>"
                        + "<td>One<br>Two<br>Three</td>"
                        + "<td class='mark'>Mark</td>"
                        + "</tr></table>");
    }

    @Test
    void aCellsContentSitsAtTheTopOfTheRowByDefault() {
        Laid laid = cellAligned("top");

        assertEquals(laid.text("One").y(), laid.text("Mark").y(), TOLERANCE);
    }

    @Test
    void aBottomAlignedCellDropsItsContentToTheFootOfTheRow() {
        Laid laid = cellAligned("bottom");

        // PDF y counts upward, so the foot of the row is the smallest y.
        assertEquals(laid.text("Three").y(), laid.text("Mark").y(), TOLERANCE);
    }

    @Test
    void aMiddleAlignedCellCentresItsContentInTheRow() {
        Laid laid = cellAligned("middle");

        float top = laid.text("One").y();
        float bottom = laid.text("Three").y();
        assertEquals((top + bottom) / 2f, laid.text("Mark").y(), TOLERANCE);
    }

    @Test
    void aCellWithOnlyABottomBorderGetsALineNotABox() {
        Laid laid = Laid.of(
                "<table><tr><td style='border-bottom: 1pt solid #000'>Cell</td></tr></table>");

        assertTrue(laid.commands(0).stream().noneMatch(c -> c instanceof PaintCommand.StrokeRect),
                "one declared side must not paint all four");
        assertEquals(
                1L,
                laid.commands(0).stream().filter(c -> c instanceof PaintCommand.StrokeLine).count());
    }

    @Test
    void eachCellBorderSideKeepsItsOwnColour() {
        Laid laid = Laid.of(
                "<table><tr><td style='border-top: 1pt solid #ff0000;"
                        + " border-bottom: 1pt solid #0000ff'>Cell</td></tr></table>");

        long colours = laid.commands(0).stream()
                .filter(c -> c instanceof PaintCommand.SetStrokeColor)
                .distinct()
                .count();
        assertEquals(2L, colours, "the two sides should stroke in two colours");
    }

    // --- Lists -------------------------------------------------------------

    @Test
    void listItemsGetTheirMarkers() {
        Laid laid = Laid.of("<ol><li>One</li><li>Two</li></ol>");

        assertTrue(laid.draws("1."), "drawn: " + laid.drawnText());
        assertTrue(laid.draws("2."), "drawn: " + laid.drawnText());
    }

    @Test
    void aMarkerSitsToTheLeftOfItsItem() {
        Laid laid = Laid.of("<ul><li>Item</li></ul>");

        assertTrue(laid.text("Item").x() > laid.texts().get(0).x());
    }

    private static int indexOfFirst(List<PaintCommand> commands, Class<?> type) {
        for (int i = 0; i < commands.size(); i++) {
            if (type.isInstance(commands.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unused")
    private static float area(Rect rect) {
        return rect.width() * rect.height();
    }
}
