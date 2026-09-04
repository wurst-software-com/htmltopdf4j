package com.wurstsoftware.htmltopdf4j.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.paint.Color;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Parsing {@code linear-gradient()} and sampling the colour along it. */
class LinearGradientTest {

    private static final float TOLERANCE = 0.001f;

    private static LinearGradient parse(String value) {
        return LinearGradient.parse(value).orElseThrow(() -> new AssertionError("did not parse: " + value));
    }

    @Test
    void twoColoursWithNoDirectionRunDownThePage() {
        LinearGradient gradient = parse("linear-gradient(red, blue)");

        assertEquals(180f, gradient.angleDegrees());
        assertEquals(2, gradient.stops().size());
    }

    @Test
    void stopsWithNoPositionAreSpreadEvenly() {
        LinearGradient gradient = parse("linear-gradient(red, white, blue)");

        assertEquals(0f, gradient.stops().get(0).position(), TOLERANCE);
        assertEquals(0.5f, gradient.stops().get(1).position(), TOLERANCE);
        assertEquals(1f, gradient.stops().get(2).position(), TOLERANCE);
    }

    @Test
    void aDeclaredPercentagePositionsAStopExactly() {
        LinearGradient gradient = parse("linear-gradient(red 10%, blue 90%)");

        assertEquals(0.1f, gradient.stops().get(0).position(), TOLERANCE);
        assertEquals(0.9f, gradient.stops().get(1).position(), TOLERANCE);
    }

    @Test
    void aStopThatWouldMoveBackwardsIsHeldWhereTheLastOneEnded() {
        LinearGradient gradient = parse("linear-gradient(red 60%, blue 20%)");

        assertEquals(0.6f, gradient.stops().get(0).position(), TOLERANCE);
        assertEquals(0.6f, gradient.stops().get(1).position(), TOLERANCE);
    }

    @Test
    void anAngleIsReadInDegrees() {
        assertEquals(45f, parse("linear-gradient(45deg, red, blue)").angleDegrees(), TOLERANCE);
    }

    @Test
    void toRightIsNinetyDegreesBecauseZeroPointsUp() {
        assertEquals(90f, parse("linear-gradient(to right, red, blue)").angleDegrees(), TOLERANCE);
    }

    @Test
    void toTopIsZeroDegrees() {
        assertEquals(0f, parse("linear-gradient(to top, red, blue)").angleDegrees(), TOLERANCE);
    }

    @Test
    void aCornerDirectionReadsTheSameEitherWayRound() {
        assertEquals(
                parse("linear-gradient(to top right, red, blue)").angleDegrees(),
                parse("linear-gradient(to right top, red, blue)").angleDegrees(),
                TOLERANCE);
    }

    @Test
    void aColourFunctionKeepsItsOwnCommasAndSpaces() {
        LinearGradient gradient = parse("linear-gradient(rgb(255, 0, 0) 0%, rgb(0, 0, 255) 100%)");

        assertEquals(2, gradient.stops().size());
        assertEquals(1f, gradient.stops().get(0).color().r(), TOLERANCE);
        assertEquals(1f, gradient.stops().get(1).color().b(), TOLERANCE);
    }

    @Test
    void aValueThatIsNotAGradientDoesNotParse() {
        assertEquals(Optional.empty(), LinearGradient.parse("red"));
        assertEquals(Optional.empty(), LinearGradient.parse("url(x.png)"));
        assertEquals(Optional.empty(), LinearGradient.parse(null));
    }

    @Test
    void aGradientWithOnlyOneStopDoesNotParse() {
        assertEquals(Optional.empty(), LinearGradient.parse("linear-gradient(red)"));
    }

    @Test
    void anUnrecognisedColourStopRejectsTheWholeGradient() {
        // A partial gradient would paint bands the author never asked for; better
        // to fall back to no background image at all.
        assertEquals(Optional.empty(), LinearGradient.parse("linear-gradient(red, notacolour)"));
    }

    @Test
    void theColourAtAStopIsThatStopsColour() {
        LinearGradient gradient = parse("linear-gradient(#000000, #ffffff)");

        assertEquals(0f, gradient.colorAt(0f).r(), TOLERANCE);
        assertEquals(1f, gradient.colorAt(1f).r(), TOLERANCE);
    }

    @Test
    void theColourBetweenTwoStopsIsMixedInProportion() {
        LinearGradient gradient = parse("linear-gradient(#000000, #ffffff)");

        assertEquals(0.25f, gradient.colorAt(0.25f).g(), TOLERANCE);
    }

    @Test
    void samplingOutsideTheGradientLineClampsToTheEndStops() {
        LinearGradient gradient = parse("linear-gradient(#000000, #ffffff)");

        assertEquals(0f, gradient.colorAt(-2f).r(), TOLERANCE);
        assertEquals(1f, gradient.colorAt(4f).r(), TOLERANCE);
    }

    @Test
    void aMiddleStopIsHonouredWhenSampling() {
        LinearGradient gradient = parse("linear-gradient(#000000, #ff0000 50%, #ffffff)");

        assertEquals(1f, gradient.colorAt(0.5f).r(), TOLERANCE);
        assertEquals(0f, gradient.colorAt(0.5f).b(), TOLERANCE);
    }

    @Test
    void twoStopsAtTheSamePositionAreAHardEdgeRatherThanADivisionByZero() {
        LinearGradient gradient = parse("linear-gradient(#000000 50%, #ffffff 50%)");

        assertTrue(Float.isFinite(gradient.colorAt(0.5f).r()));
    }

    @Test
    void aGradientCannotBeBuiltWithFewerThanTwoStops() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LinearGradient(0f, List.of(new LinearGradient.Stop(Color.BLACK, 0f))));
    }
}
