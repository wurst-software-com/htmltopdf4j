package com.wurstsoftware.htmltopdf4j.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.paint.Color;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CssColorTest {

    @ParameterizedTest
    @CsvSource({
        "'#f00',          255, 0,   0",
        "'#FF0000',       255, 0,   0",
        "'#0000ff',       0,   0,   255",
        "'rgb(1, 2, 3)',  1,   2,   3",
        "'rgb(0 128 255)',0,   128, 255",
        "'rgb(100%, 0%, 0%)', 255, 0, 0",
        "'hsl(0, 100%, 50%)', 255, 0, 0",
        "'hsl(120, 100%, 50%)', 0, 255, 0",
        "'hsl(240deg, 100%, 50%)', 0, 0, 255",
        "'hsl(0, 0%, 50%)', 128, 128, 128",
        "red,             255, 0,   0",
        "REBECCA,         -1,  -1,  -1",
        "whitesmoke,      245, 245, 245",
        "'  Blue  ',      0,   0,   255"
    })
    void parsesEveryColourSyntax(String input, int r, int g, int b) {
        Optional<Color> parsed = CssColor.parse(input);
        if (r < 0) {
            assertTrue(parsed.isEmpty(), input + " is not a colour");
            return;
        }
        assertTrue(parsed.isPresent(), input + " should parse");
        assertEquals(r / 255f, parsed.get().r(), 0.004f, input + " red");
        assertEquals(g / 255f, parsed.get().g(), 0.004f, input + " green");
        assertEquals(b / 255f, parsed.get().b(), 0.004f, input + " blue");
    }

    /**
     * A fully transparent colour is not a colour that draws nothing — it is the
     * absence of a Paint command, which is what the empty result means.
     */
    @ParameterizedTest
    @ValueSource(strings = {"transparent", "rgba(255, 0, 0, 0)", "#ff000000", "hsla(0, 100%, 50%, 0)", "none"})
    void transparentProducesNoColourAtAll(String input) {
        assertTrue(CssColor.parse(input).isEmpty(), input);
    }

    /** Alpha flattens against the opaque sheet, since there is no transparency group to join. */
    @Test
    void partialAlphaCompositesAgainstWhite() {
        Color half = CssColor.parse("rgba(0, 0, 0, 0.5)").orElseThrow();
        assertEquals(0.5f, half.r(), 0.01f);
        assertEquals(0.5f, half.g(), 0.01f);
        assertEquals(0.5f, half.b(), 0.01f);

        assertEquals(Color.BLACK, CssColor.parse("rgba(0, 0, 0, 1)").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "#12345", "rgb(1, 2)", "hsl(a, b, c)", "notacolour", "rgb()"})
    void unparseableValuesLeaveTheCascadeAlone(String input) {
        assertTrue(CssColor.parse(input).isEmpty(), input);
    }

    @Test
    void hueWrapsAroundTheColourWheel() {
        assertEquals(CssColor.parse("hsl(0, 100%, 50%)"), CssColor.parse("hsl(360, 100%, 50%)"));
        assertEquals(CssColor.parse("hsl(0, 100%, 50%)"), CssColor.parse("hsl(-360, 100%, 50%)"));
    }
}
