package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * `vertical-align: baseline` in a table row, which is the initial value of a
 * cell: the first line of every cell sits on one line, however different the
 * cells' font sizes are.
 */
class TableBaselineTest {

    private static String row(String alignment) {
        return "<table><tr>"
                + "<td style='font-size:24pt; " + alignment + "'>BIG</td>"
                + "<td style='font-size:8pt; " + alignment + "'>small</td>"
                + "</tr>"
                + "<tr><td>NEXTROW</td><td>x</td></tr></table>";
    }

    @Test
    void cellsOfDifferentFontSizesShareOneBaseline() {
        Laid laid = Laid.of(row(""));

        assertEquals(laid.text("BIG").y(), laid.text("small").y(), 0.5f,
                "the small cell's first line drops onto the big cell's baseline");
    }

    @Test
    void topAlignmentIsUnaffected() {
        Laid baseline = Laid.of(row(""));
        Laid top = Laid.of(row("vertical-align: top"));

        assertTrue(top.text("small").y() > baseline.text("small").y(),
                "a top-aligned cell keeps its first line at the top of the row");
        assertEquals(baseline.text("BIG").y(), top.text("BIG").y(), 0.5f,
                "the tallest cell is where it always was");
    }

    @Test
    void bottomAlignmentIsUnaffected() {
        Laid bottom = Laid.of(row("vertical-align: bottom"));

        assertTrue(bottom.text("small").y() < bottom.text("BIG").y(),
                "a bottom-aligned small cell sits below the big cell's baseline");
    }

    @Test
    void aCellWithNoTextOfItsOwnFallsBackToTheTopOfTheRow() {
        Laid laid = Laid.of("<table><tr>"
                + "<td style='font-size:24pt'>BIG</td>"
                + "<td><div style='height:10pt; background:#ff0'></div></td>"
                + "</tr></table>");

        assertTrue(laid.fills().stream().anyMatch(rect -> rect.height() > 9f),
                "the cell with no text is still laid out");
    }

    @Test
    void theRowIsStillTallEnoughForTheCellsItShifted() {
        Laid laid = Laid.of(row(""));

        assertTrue(laid.text("NEXTROW").y() < laid.text("small").y() - 8f,
                "the next row starts below the shifted line, not on top of it");
    }
}
