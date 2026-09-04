package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Flex items sitting on one baseline.
 *
 * <p>{@code align-items: baseline} lines up the first lines of the items on a
 * flex line, not their tops, so items set in different sizes read as one row of
 * text. It is the same shift a table row applies to its cells, measured with the
 * same {@link Layout#firstBaseline} — see {@link TableBaselineTest}.
 */
class FlexBaselineTest {

    private static Laid flex(String containerStyle, String firstItemStyle, String secondItemStyle) {
        return Laid.of("<div style='display:flex; " + containerStyle + "'>"
                + "<div style='width:150pt; " + firstItemStyle + "'>SMALL</div>"
                + "<div style='width:150pt; " + secondItemStyle + "'>BIG</div>"
                + "</div>"
                + "<p>AFTER</p>");
    }

    @Test
    void twoItemsOfDifferentSizesShareOneBaseline() {
        Laid laid = flex("align-items:baseline", "font-size:10pt", "font-size:30pt");

        assertEquals(laid.text("BIG").y(), laid.text("SMALL").y(), 0.5f,
                "both items should draw their first line on the same baseline");
    }

    @Test
    void aStartAlignedLineDoesNotShareABaseline() {
        Laid laid = flex("align-items:flex-start", "font-size:10pt", "font-size:30pt");

        assertTrue(laid.text("SMALL").y() > laid.text("BIG").y() + 5f,
                "start alignment lines the tops up, so the small text sits higher");
    }

    @Test
    void theLineIsTallEnoughForWhatItShifted() {
        // The first item's baseline is the deeper one, so it is the tall second
        // item that drops — past the foot of the line it would have made.
        float shifted = flex("align-items:baseline", "padding-top:40pt; font-size:10pt", "font-size:30pt")
                .text("AFTER").y();
        float unshifted = flex("align-items:flex-start", "padding-top:40pt; font-size:10pt", "font-size:30pt")
                .text("AFTER").y();

        assertTrue(shifted < unshifted - 5f,
                "what follows the container should clear the items the line dropped");
    }

    @Test
    void anItemWithNoTextDoesNotVoteOnTheBaseline() {
        Laid withEmpty = Laid.of("<div style='display:flex; align-items:baseline'>"
                + "<div style='width:100pt; height:80pt'></div>"
                + "<div style='width:150pt; font-size:10pt'>SMALL</div>"
                + "<div style='width:150pt; font-size:30pt'>BIG</div>"
                + "</div>");

        assertEquals(withEmpty.text("BIG").y(), withEmpty.text("SMALL").y(), 0.5f,
                "an item with nothing to align neither moves nor moves the others");
    }

    @Test
    void alignSelfBaselineWorksAgainstAContainerThatSaysOtherwise() {
        Laid laid = flex("align-items:flex-start", "font-size:10pt; align-self:baseline",
                "font-size:30pt; align-self:baseline");

        assertEquals(laid.text("BIG").y(), laid.text("SMALL").y(), 0.5f,
                "align-self should be able to ask for the baseline on its own");
    }
}
