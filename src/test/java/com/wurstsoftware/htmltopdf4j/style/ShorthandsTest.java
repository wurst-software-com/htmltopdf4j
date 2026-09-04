package com.wurstsoftware.htmltopdf4j.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Shorthand expansion, which happens during the Cascade so that a later
 * longhand can beat an earlier shorthand and the other way round.
 */
class ShorthandsTest {

    private static Map<String, String> expand(String property, String value) {
        return Shorthands.expand(property, value);
    }

    // --- The one-to-four value pattern -------------------------------------

    @Test
    void oneValueAppliesToEverySide() {
        Map<String, String> longhands = expand("margin", "4pt");

        assertEquals("4pt", longhands.get("margin-top"));
        assertEquals("4pt", longhands.get("margin-right"));
        assertEquals("4pt", longhands.get("margin-bottom"));
        assertEquals("4pt", longhands.get("margin-left"));
    }

    @Test
    void twoValuesAreVerticalThenHorizontal() {
        Map<String, String> longhands = expand("padding", "1pt 2pt");

        assertEquals("1pt", longhands.get("padding-top"));
        assertEquals("2pt", longhands.get("padding-right"));
        assertEquals("1pt", longhands.get("padding-bottom"));
        assertEquals("2pt", longhands.get("padding-left"));
    }

    @Test
    void threeValuesAreTopHorizontalBottom() {
        Map<String, String> longhands = expand("margin", "1pt 2pt 3pt");

        assertEquals("1pt", longhands.get("margin-top"));
        assertEquals("2pt", longhands.get("margin-right"));
        assertEquals("3pt", longhands.get("margin-bottom"));
        assertEquals("2pt", longhands.get("margin-left"));
    }

    @Test
    void fourValuesRunClockwiseFromTheTop() {
        Map<String, String> longhands = expand("margin", "1pt 2pt 3pt 4pt");

        assertEquals("1pt", longhands.get("margin-top"));
        assertEquals("2pt", longhands.get("margin-right"));
        assertEquals("3pt", longhands.get("margin-bottom"));
        assertEquals("4pt", longhands.get("margin-left"));
    }

    @Test
    void moreThanFourValuesIsInvalidAndExpandsToNothing() {
        assertTrue(expand("margin", "1pt 2pt 3pt 4pt 5pt").isEmpty());
    }

    // --- Borders -----------------------------------------------------------

    @Test
    void aBorderIsSplitIntoWidthStyleAndColourOnEverySide() {
        Map<String, String> longhands = expand("border", "2pt solid red");

        assertEquals("2pt", longhands.get("border-top-width"));
        assertEquals("solid", longhands.get("border-left-style"));
        assertEquals("red", longhands.get("border-bottom-color"));
    }

    @Test
    void theBorderPartsMayComeInAnyOrder() {
        assertEquals(expand("border", "2pt solid red"), expand("border", "red 2pt solid"));
    }

    @Test
    void aSingleSideShorthandTouchesOnlyThatSide() {
        Map<String, String> longhands = expand("border-left", "1pt dashed #000");

        assertEquals("dashed", longhands.get("border-left-style"));
        assertFalse(longhands.containsKey("border-right-style"));
    }

    @Test
    void aBorderWithNoWidthStillGetsTheDefaultMediumWidth() {
        assertEquals("3px", expand("border", "solid red").get("border-top-width"));
    }

    @Test
    void borderNoneGetsNoWidthAtAll() {
        assertFalse(expand("border", "none").containsKey("border-top-width"));
    }

    @Test
    void borderRadiusRunsClockwiseFromTheTopLeft() {
        Map<String, String> longhands = expand("border-radius", "1pt 2pt 3pt 4pt");

        assertEquals("1pt", longhands.get("border-top-left-radius"));
        assertEquals("2pt", longhands.get("border-top-right-radius"));
        assertEquals("3pt", longhands.get("border-bottom-right-radius"));
        assertEquals("4pt", longhands.get("border-bottom-left-radius"));
    }

    @Test
    void oneRadiusRoundsEveryCorner() {
        Map<String, String> longhands = expand("border-radius", "6pt");

        assertEquals(4, longhands.size());
        assertTrue(longhands.values().stream().allMatch("6pt"::equals));
    }

    @Test
    void anEllipticalRadiusUsesItsHorizontalHalf() {
        assertEquals("6pt", expand("border-radius", "6pt / 3pt").get("border-top-left-radius"));
    }

    // --- background --------------------------------------------------------

    @Test
    void aBackgroundColourAloneBecomesTheColourLonghand() {
        assertEquals("#ff0000", expand("background", "#ff0000").get("background-color"));
    }

    @Test
    void aBackgroundWithAnImageKeepsTheWholeValueForThePainter() {
        Map<String, String> longhands = expand("background", "url(paper.png) no-repeat center");

        assertEquals("url(paper.png) no-repeat center", longhands.get("background-image"));
    }

    @Test
    void aGradientBackgroundIsAnImageNotAColour() {
        Map<String, String> longhands = expand("background", "linear-gradient(red, blue)");

        assertTrue(longhands.containsKey("background-image"));
        assertFalse(longhands.containsKey("background-color"));
    }

    @Test
    void theColourIsPickedOutOfALongerBackgroundValue() {
        assertEquals("#eee", expand("background", "#eee no-repeat").get("background-color"));
    }

    // --- font --------------------------------------------------------------

    @Test
    void theFontShorthandSplitsIntoSizeAndFamily() {
        Map<String, String> longhands = expand("font", "12pt Georgia");

        assertEquals("12pt", longhands.get("font-size"));
        assertEquals("Georgia", longhands.get("font-family"));
    }

    @Test
    void styleAndWeightBeforeTheSizeAreRecognised() {
        Map<String, String> longhands = expand("font", "italic bold 12pt Georgia");

        assertEquals("italic", longhands.get("font-style"));
        assertEquals("bold", longhands.get("font-weight"));
        assertEquals("12pt", longhands.get("font-size"));
    }

    @Test
    void aSlashCarriesTheLineHeight() {
        Map<String, String> longhands = expand("font", "12pt/1.5 Georgia");

        assertEquals("12pt", longhands.get("font-size"));
        assertEquals("1.5", longhands.get("line-height"));
        assertEquals("Georgia", longhands.get("font-family"));
    }

    @Test
    void aFontValueWithNoSizeIsNotTheShorthandAndExpandsToNothing() {
        // `font: inherit` and the system keywords land here; expanding them into
        // a bogus family would be worse than leaving them alone.
        assertTrue(expand("font", "caption").isEmpty());
    }

    // --- flex --------------------------------------------------------------

    @Test
    void aBareGrowFactorGetsABasisOfZeroNotAuto() {
        Map<String, String> longhands = expand("flex", "1");

        assertEquals("1", longhands.get("flex-grow"));
        assertEquals("1", longhands.get("flex-shrink"));
        assertEquals("0", longhands.get("flex-basis"));
    }

    @Test
    void flexNoneIsRigid() {
        Map<String, String> longhands = expand("flex", "none");

        assertEquals("0", longhands.get("flex-grow"));
        assertEquals("0", longhands.get("flex-shrink"));
        assertEquals("auto", longhands.get("flex-basis"));
    }

    @Test
    void flexAutoGrowsAndShrinksFromItsContent() {
        Map<String, String> longhands = expand("flex", "auto");

        assertEquals("1", longhands.get("flex-grow"));
        assertEquals("auto", longhands.get("flex-basis"));
    }

    @Test
    void allThreeFlexValuesAreTakenInOrder() {
        Map<String, String> longhands = expand("flex", "2 3 100pt");

        assertEquals("2", longhands.get("flex-grow"));
        assertEquals("3", longhands.get("flex-shrink"));
        assertEquals("100pt", longhands.get("flex-basis"));
    }

    // --- gap, list-style, grid placement -----------------------------------

    @Test
    void oneGapAppliesToBothAxes() {
        Map<String, String> longhands = expand("gap", "8pt");

        assertEquals("8pt", longhands.get("row-gap"));
        assertEquals("8pt", longhands.get("column-gap"));
    }

    @Test
    void twoGapsAreRowThenColumn() {
        Map<String, String> longhands = expand("gap", "8pt 16pt");

        assertEquals("8pt", longhands.get("row-gap"));
        assertEquals("16pt", longhands.get("column-gap"));
    }

    @Test
    void theListStyleShorthandSortsItsPartsByWhatTheyLookLike() {
        Map<String, String> longhands = expand("list-style", "square inside");

        assertEquals("square", longhands.get("list-style-type"));
        assertEquals("inside", longhands.get("list-style-position"));
    }

    @Test
    void aGridPlacementSplitsOnItsSlash() {
        Map<String, String> longhands = expand("grid-column", "1 / 3");

        assertEquals("1", longhands.get("grid-column-start"));
        assertEquals("3", longhands.get("grid-column-end"));
    }

    @Test
    void aGridPlacementWithNoSlashIsAStartOnly() {
        Map<String, String> longhands = expand("grid-row", "2");

        assertEquals("2", longhands.get("grid-row-start"));
        assertFalse(longhands.containsKey("grid-row-end"));
    }

    // --- Everything else ---------------------------------------------------

    @Test
    void aPropertyThatIsNotAShorthandPassesStraightThrough() {
        assertEquals(Map.of("color", "red"), expand("color", "red"));
    }
}
