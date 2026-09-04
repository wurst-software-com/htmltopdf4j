package com.wurstsoftware.htmltopdf4j.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.paint.Color;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Reading a computed value: inheritance, the explicit CSS-wide keywords, and
 * {@code var()} substitution, which happens at computed-value time and so has to
 * work for every property rather than only the ones with named accessors.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ComputedStyleTest {

    private static ComputedStyle styleOfTarget(String css, String bodyHtml) {
        Document document = Jsoup.parse("<html><body><style>" + css + "</style>" + bodyHtml + "</body></html>");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        return cascade.styleOf(document.selectFirst("#target"));
    }

    // --- Custom properties -------------------------------------------------

    @Test
    void aCustomPropertyIsSubstitutedIntoTheValueThatReadsIt() {
        ComputedStyle style = styleOfTarget(
                ":root { --brand: #ff0000 } #target { color: var(--brand) }", "<p id=target>x</p>");

        assertEquals(Color.fromRgb255(255, 0, 0), style.color());
    }

    @Test
    void aCustomPropertyInheritsEvenThoughItsNameIsNotOnTheInheritedList() {
        ComputedStyle style = styleOfTarget(
                ".theme { --brand: #00ff00 } #target { color: var(--brand) }",
                "<div class=theme><p id=target>x</p></div>");

        assertEquals(Color.fromRgb255(0, 255, 0), style.color());
    }

    @Test
    void aSubtreeCanRedefineACustomPropertyForItsDescendants() {
        ComputedStyle style = styleOfTarget(
                ":root { --brand: #ff0000 } .override { --brand: #0000ff } #target { color: var(--brand) }",
                "<div class=override><p id=target>x</p></div>");

        assertEquals(Color.fromRgb255(0, 0, 255), style.color());
    }

    @Test
    void aFallbackIsUsedWhenTheCustomPropertyIsNotSet() {
        ComputedStyle style = styleOfTarget(
                "#target { color: var(--missing, #ff0000) }", "<p id=target>x</p>");

        assertEquals(Color.fromRgb255(255, 0, 0), style.color());
    }

    @Test
    void aVariableThatResolvesToNothingMakesTheDeclarationInvalid() {
        // CSS calls this "invalid at computed-value time": the property behaves
        // as if it were never declared, rather than taking a garbage value.
        ComputedStyle style = styleOfTarget(
                "#target { letter-spacing: var(--missing) }", "<p id=target>x</p>");

        assertNull(style.value("letter-spacing"));
    }

    @Test
    void aCustomPropertyMayItselfBeBuiltFromAnotherOne() {
        ComputedStyle style = styleOfTarget(
                ":root { --base: #ff0000; --brand: var(--base) } #target { color: var(--brand) }",
                "<p id=target>x</p>");

        assertEquals(Color.fromRgb255(255, 0, 0), style.color());
    }

    @Test
    void aCircularCustomPropertyIsBoundedRatherThanLooping() {
        ComputedStyle style = styleOfTarget(
                ":root { --a: var(--b); --b: var(--a) } #target { letter-spacing: var(--a) }",
                "<p id=target>x</p>");

        // Substitution stops at a depth limit and the declaration comes out
        // invalid, which is the only sane end for a cycle — the alternative is a
        // stack overflow in the middle of a render.
        assertNull(style.value("letter-spacing"));
    }

    @Test
    void aVariableIsSubstitutedInThemiddleOfALongerValue() {
        ComputedStyle style = styleOfTarget(
                ":root { --gap: 4pt } #target { margin: var(--gap) 8pt }", "<p id=target>x</p>");

        assertEquals(Optional.of(4f), style.length("margin-top").map(length -> style.resolve(length, 0f)));
        assertEquals(Optional.of(8f), style.length("margin-right").map(length -> style.resolve(length, 0f)));
    }

    @Test
    void aVariableWorksForAPropertyWithANamedAccessorToo() {
        ComputedStyle style = styleOfTarget(
                ":root { --size: 30pt } #target { font-size: var(--size) }", "<p id=target>x</p>");

        assertEquals(30f, style.fontSize(), 0.01f);
    }

    // --- The CSS-wide keywords ---------------------------------------------

    @Test
    void inheritTakesTheParentsValueEvenForAPropertyThatDoesNotInherit() {
        ComputedStyle style = styleOfTarget(
                ".outer { margin-top: 20pt } #target { margin-top: inherit }",
                "<div class=outer><p id=target>x</p></div>");

        assertEquals("20pt", style.value("margin-top"));
    }

    @Test
    void initialThrowsAwayWhatWouldHaveBeenInherited() {
        ComputedStyle style = styleOfTarget(
                ".outer { letter-spacing: 2pt } #target { letter-spacing: initial }",
                "<div class=outer><p id=target>x</p></div>");

        assertNull(style.value("letter-spacing"));
    }

    @Test
    void unsetInheritsForAnInheritedProperty() {
        ComputedStyle style = styleOfTarget(
                ".outer { letter-spacing: 2pt } #target { letter-spacing: unset }",
                "<div class=outer><p id=target>x</p></div>");

        assertEquals("2pt", style.value("letter-spacing"));
    }

    @Test
    void unsetIsInitialForAPropertyThatDoesNotInherit() {
        ComputedStyle style = styleOfTarget(
                ".outer { margin-top: 20pt } #target { margin-top: unset }",
                "<div class=outer><p id=target>x</p></div>");

        assertNull(style.value("margin-top"));
    }

    // --- Inheritance -------------------------------------------------------

    @Test
    void anInheritedPropertyWithNoNamedAccessorStillReachesADescendant() {
        ComputedStyle style = styleOfTarget(
                ".outer { text-transform: uppercase }", "<div class=outer><p id=target>x</p></div>");

        assertEquals("uppercase", style.value("text-transform"));
    }

    @Test
    void aBoxPropertyIsNotInheritedBecauseAnInheritedMarginWouldBeAHardBugToSee() {
        ComputedStyle style = styleOfTarget(
                ".outer { padding-left: 20pt }", "<div class=outer><p id=target>x</p></div>");

        assertNull(style.value("padding-left"));
    }

    // --- The plain accessors -----------------------------------------------

    @Test
    void anInitialStyleHasTheInitialFontSizeAndNoDeclarations() {
        ComputedStyle style = ComputedStyle.initial();

        assertEquals(ComputedStyle.INITIAL_FONT_SIZE, style.fontSize(), 0.01f);
        assertEquals(Color.BLACK, style.color());
        assertFalse(style.bold());
        assertFalse(style.italic());
        assertNull(style.parent());
    }

    @Test
    void aStyleBuiltFromDeclarationsAloneHasNoParentToInheritFrom() {
        ComputedStyle style = ComputedStyle.of(java.util.Map.of("margin-top", "10pt"));

        assertEquals("10pt", style.value("margin-top"));
        assertNull(style.value("color"));
    }

    @Test
    void hasAndKeywordReadTheComputedValueRatherThanTheDeclaredOne() {
        ComputedStyle style = styleOfTarget(
                ":root { --w: auto } #target { width: var(--w) }", "<p id=target>x</p>");

        assertTrue(style.has("width"));
        assertTrue(style.isAuto("width"));
        assertTrue(style.keyword("width", "AUTO"), "keyword comparison is case-insensitive");
    }

    @Test
    void aFontWeightNumberAboveSixHundredIsBold() {
        assertTrue(ComputedStyle.of(java.util.Map.of("font-weight", "700")).bold());
        assertFalse(ComputedStyle.of(java.util.Map.of("font-weight", "300")).bold());
    }

    @Test
    void aUnitlessLineHeightIsAMultiplierAndALengthIsAbsolute() {
        assertTrue(ComputedStyle.of(java.util.Map.of("line-height", "1.5")).lineHeight().orElseThrow()
                instanceof ComputedStyle.LineHeight.Multiplier);
        assertTrue(ComputedStyle.of(java.util.Map.of("line-height", "18pt")).lineHeight().orElseThrow()
                instanceof ComputedStyle.LineHeight.Absolute);
        assertEquals(Optional.empty(), ComputedStyle.of(java.util.Map.of("line-height", "normal")).lineHeight());
    }

    @Test
    void aFontFamilyListIsSplitAndUnquoted() {
        ComputedStyle style = ComputedStyle.of(java.util.Map.of("font-family", "'Helvetica Neue', Arial, sans-serif"));

        assertEquals(java.util.List.of("Helvetica Neue", "Arial", "sans-serif"), style.fontFamily());
    }
}
