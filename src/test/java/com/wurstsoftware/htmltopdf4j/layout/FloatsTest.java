package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The bands a float leaves behind, and what they do to the lines beside them. */
class FloatsTest {

    @Test
    void withNoFloatsALineKeepsTheFullWidthItWasOffered() {
        Floats floats = new Floats();

        assertEquals(0f, floats.leftEdge(0, 0f, 12f, 0f));
        assertEquals(500f, floats.rightEdge(0, 0f, 12f, 500f));
    }

    @Test
    void aLeftFloatPushesTheStartOfTheLinesBesideIt() {
        Floats floats = new Floats();
        floats.add(0, 100f, 200f, 80f, true);

        assertEquals(80f, floats.leftEdge(0, 120f, 12f, 0f));
    }

    @Test
    void aRightFloatPullsInTheEndOfTheLinesBesideIt() {
        Floats floats = new Floats();
        floats.add(0, 100f, 200f, 420f, false);

        assertEquals(420f, floats.rightEdge(0, 120f, 12f, 500f));
    }

    @Test
    void aLineAboveAFloatIsUnaffectedByIt() {
        Floats floats = new Floats();
        floats.add(0, 100f, 200f, 80f, true);

        assertEquals(0f, floats.leftEdge(0, 50f, 12f, 0f));
    }

    @Test
    void aLineBelowAFloatIsUnaffectedByIt() {
        Floats floats = new Floats();
        floats.add(0, 100f, 200f, 80f, true);

        assertEquals(0f, floats.leftEdge(0, 250f, 12f, 0f));
    }

    @Test
    void aLineThatOnlyPartlyOverlapsAFloatIsStillNarrowed() {
        Floats floats = new Floats();
        floats.add(0, 100f, 200f, 80f, true);

        // The line's top is above the float but its bottom reaches into it, so
        // it would collide: the whole line has to move in.
        assertEquals(80f, floats.leftEdge(0, 95f, 12f, 0f));
    }

    @Test
    void twoFloatsOnTheSameSideStackInwards() {
        Floats floats = new Floats();
        floats.add(0, 0f, 100f, 60f, true);
        floats.add(0, 0f, 100f, 130f, true);

        assertEquals(130f, floats.leftEdge(0, 10f, 12f, 0f));
    }

    @Test
    void aFloatOnAnotherPageIsIgnored() {
        Floats floats = new Floats();
        floats.add(0, 100f, 200f, 80f, true);

        assertEquals(0f, floats.leftEdge(1, 120f, 12f, 0f));
    }

    @Test
    void clearingASideDropsPastTheFloatsOnIt() {
        Floats floats = new Floats();
        floats.add(0, 50f, 180f, 80f, true);

        assertEquals(180f, floats.clearance(0, 60f, true, false));
    }

    @Test
    void clearingTheOtherSideDoesNotMove() {
        Floats floats = new Floats();
        floats.add(0, 50f, 180f, 80f, true);

        assertEquals(60f, floats.clearance(0, 60f, false, true));
    }

    @Test
    void clearingWalksPastAFloatThatOnlyStartsWhereTheLastOneEnded() {
        Floats floats = new Floats();
        floats.add(0, 50f, 180f, 80f, true);
        floats.add(0, 180f, 260f, 90f, false);

        // Clearing both sides has to iterate: dropping past the left float lands
        // exactly on the top of the right one.
        assertEquals(260f, floats.clearance(0, 60f, true, true));
    }

    @Test
    void clearingBelowEveryFloatStaysPut() {
        Floats floats = new Floats();
        floats.add(0, 50f, 180f, 80f, true);

        assertEquals(300f, floats.clearance(0, 300f, true, true));
    }

    @Test
    void retainingAPageForgetsTheFloatsOfTheOnesBeforeIt() {
        Floats floats = new Floats();
        floats.add(0, 100f, 200f, 80f, true);
        floats.add(1, 100f, 200f, 90f, true);

        floats.retain(1);

        assertEquals(0f, floats.leftEdge(0, 120f, 12f, 0f));
        assertEquals(90f, floats.leftEdge(1, 120f, 12f, 0f));
    }
}
