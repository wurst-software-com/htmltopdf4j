package com.wurstsoftware.htmltopdf4j.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LengthTest {

    private static final float EM = 16f;
    private static final float ROOT_EM = 12f;
    private static final float BASIS = 400f;

    @ParameterizedTest
    @CsvSource({
        "12pt,   12",
        "16px,   12",
        "1in,    72",
        "1pc,    12",
        "2.54cm, 72",
        "25.4mm, 72",
        "0,      0",
        "1em,    16",
        "2rem,   24",
        "1ex,    8",
        "50%,    200",
        "'  10pt  ', 10",
        "-4pt,   -4"
    })
    void resolvesEveryUnitToPoints(String input, float expected) {
        Length length = Length.parse(input).orElseThrow(() -> new AssertionError("did not parse: " + input));
        assertEquals(expected, length.resolve(EM, ROOT_EM, BASIS), 0.01f, input);
    }

    /** A unitless number is only a length when it is zero; otherwise it is not a length at all. */
    @ParameterizedTest
    @ValueSource(strings = {"12", "auto", "", "  ", "abc", "pt", "12qq"})
    void rejectsWhatIsNotALength(String input) {
        assertTrue(Length.parse(input).isEmpty(), input);
    }

    @Test
    void percentagesStayUnresolvedUntilTheContainerIsKnown() {
        Length half = Length.parse("50%").orElseThrow();
        assertTrue(half.isRelativeToContainer());
        assertEquals(150f, half.resolve(EM, ROOT_EM, 300f), 0.01f);
        assertEquals(50f, half.resolve(EM, ROOT_EM, 100f), 0.01f);
    }

    @ParameterizedTest
    @CsvSource({
        "'calc(10pt + 2pt)',    12",
        "'calc(20pt - 8pt)',    12",
        "'calc(1in - 60pt)',    12",
        "'calc(100%)',          400",
        "'calc(6pt * 2)',       12",
        "'calc(24pt / 2)',      12",
        "'calc(2em)',           32"
    })
    void evaluatesTheCalcExpressionsStylesheetsActuallyUse(String input, float expected) {
        Length length = Length.parse(input).orElseThrow(() -> new AssertionError("did not parse: " + input));
        assertEquals(expected, length.resolve(EM, ROOT_EM, BASIS), 0.01f, input);
    }

    /**
     * A calc mixing a percentage with a length cannot be one Length, and two
     * different relative units cannot either. Dropping the declaration leaves the
     * cascaded or initial value in place, which is safer than a wrong number.
     */
    @ParameterizedTest
    @ValueSource(strings = {"calc(100% - 2rem)", "calc(1em + 1%)", "calc(min(1pt, 2pt))", "calc(1pt +"})
    void refusesCalcItCannotRepresent(String input) {
        assertTrue(Length.parse(input).isEmpty(), input);
    }
}
