package com.wurstsoftware.htmltopdf4j.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.paint.Color;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CascadeTest {

    /** Cascades a Document's own {@code <style>} blocks and returns the style of {@code #target}. */
    private static ComputedStyle styleOfTarget(String bodyHtml) {
        Document document = Jsoup.parse("<html><body>" + bodyHtml + "</body></html>");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        Element target = document.selectFirst("#target");
        return cascade.styleOf(target);
    }

    private static ComputedStyle styleOfTarget(String css, String bodyHtml) {
        return styleOfTarget("<style>" + css + "</style>" + bodyHtml);
    }

    // --- Selector matching --------------------------------------------------

    @Test
    void aTypeSelectorAppliesToEveryElementOfThatType() {
        ComputedStyle style = styleOfTarget("p { color: red }", "<p id=target>x</p>");
        assertEquals(Color.fromRgb255(255, 0, 0), style.color());
    }

    @Test
    void aClassSelectorAppliesOnlyToElementsCarryingTheClass() {
        Document document = Jsoup.parse("<style>.hot{color:red}</style><p class=hot id=a>x<p id=b>y");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        assertEquals(Color.fromRgb255(255, 0, 0), cascade.styleOf(document.selectFirst("#a")).color());
        assertEquals(Color.BLACK, cascade.styleOf(document.selectFirst("#b")).color());
    }

    @Test
    void aDescendantSelectorMatchesThroughInterveningElements() {
        ComputedStyle style = styleOfTarget(
                "div span { color: #00ff00 }", "<div><em><span id=target>x</span></em></div>");
        assertEquals(Color.fromRgb255(0, 255, 0), style.color());
    }

    @Test
    void aBrokenRuleIsDroppedAndTheRulesAroundItSurvive() {
        ComputedStyle style = styleOfTarget(
                "p { color: blue } p { color: ###### } em { }", "<p id=target>x</p>");
        assertEquals(Color.fromRgb255(0, 0, 255), style.color());
    }

    @Test
    void aSelectorTheMatcherCannotHandleMatchesNothingRatherThanFailingTheRender() {
        ComputedStyle style = styleOfTarget(
                "p::first-line { color: red } p { color: blue }", "<p id=target>x</p>");
        assertEquals(Color.fromRgb255(0, 0, 255), style.color());
    }

    // --- Specificity, source order and importance ---------------------------

    @Test
    void aMoreSpecificSelectorWinsRegardlessOfSourceOrder() {
        ComputedStyle style = styleOfTarget(
                "#target { color: red } p.hot { color: blue }", "<p class=hot id=target>x</p>");
        assertEquals(Color.fromRgb255(255, 0, 0), style.color());
    }

    @Test
    void equalSpecificityIsBrokenByTheLaterRule() {
        ComputedStyle style = styleOfTarget("p { color: red } p { color: blue }", "<p id=target>x</p>");
        assertEquals(Color.fromRgb255(0, 0, 255), style.color());
    }

    @Test
    void anImportantDeclarationBeatsAMoreSpecificNormalOne() {
        ComputedStyle style = styleOfTarget(
                "p { color: red !important } #target { color: blue }", "<p id=target>x</p>");
        assertEquals(Color.fromRgb255(255, 0, 0), style.color());
    }

    @Test
    void anInlineStyleBeatsAnyAuthorSelector() {
        ComputedStyle style = styleOfTarget(
                "#target { color: red }", "<p id=target style='color: blue'>x</p>");
        assertEquals(Color.fromRgb255(0, 0, 255), style.color());
    }

    @Test
    void anImportantAuthorRuleBeatsAnInlineStyle() {
        ComputedStyle style = styleOfTarget(
                "p { color: red !important }", "<p id=target style='color: blue'>x</p>");
        assertEquals(Color.fromRgb255(255, 0, 0), style.color());
    }

    // --- Origins ------------------------------------------------------------

    @Test
    void theUserAgentStylesheetSuppliesDefaultsNobodyDeclared() {
        ComputedStyle style = styleOfTarget("<p id=target>x</p>");
        assertEquals(Display.BLOCK, style.display());
    }

    @Test
    void anAuthorRuleOfTheSameSpecificityBeatsTheUserAgentOne() {
        ComputedStyle style = styleOfTarget("p { display: inline }", "<p id=target>x</p>");
        assertEquals(Display.INLINE, style.display());
    }

    @Test
    void anAuthorRuleOverridesTheUserAgentLinkColour() {
        ComputedStyle style = styleOfTarget("a { color: #123456 }", "<a id=target href='#'>x</a>");
        assertEquals(Color.fromRgb255(0x12, 0x34, 0x56), style.color());
        assertTrue(style.underline(), "the user-agent underline survives a colour-only override");
    }

    @Test
    void anAuthorRuleCanRemoveTheUserAgentUnderline() {
        ComputedStyle style = styleOfTarget("a { text-decoration: none }", "<a id=target href='#'>x</a>");
        assertFalse(style.underline());
    }

    // --- Shorthands ---------------------------------------------------------

    @Test
    void aShorthandExpandsSoALaterLonghandOverridesOnlyOnePart() {
        ComputedStyle style = styleOfTarget(
                "#target { border: 2px solid red; border-color: blue }", "<p id=target>x</p>");
        assertEquals(2f, style.length("border-top-width").orElseThrow().value());
        assertEquals(Color.fromRgb255(0, 0, 255), CssColor.parse(style.raw("border-top-color")).orElseThrow());
    }

    @Test
    void aLonghandDeclaredBeforeAShorthandLosesToIt() {
        ComputedStyle style = styleOfTarget(
                "#target { margin-top: 10px; margin: 4px }", "<p id=target>x</p>");
        assertEquals(4f, style.length("margin-top").orElseThrow().value());
    }

    // --- Inheritance and relative units ------------------------------------

    @Test
    void anInheritedPropertyReachesADescendantThatDeclaredNothing() {
        ComputedStyle style = styleOfTarget(
                "div { color: #ff00ff }", "<div><span id=target>x</span></div>");
        assertEquals(Color.fromRgb255(255, 0, 255), style.color());
    }

    @Test
    void aNonInheritedPropertyDoesNotReachADescendant() {
        ComputedStyle style = styleOfTarget(
                "div { background-color: red }", "<div><span id=target>x</span></div>");
        assertTrue(style.backgroundColor().isEmpty());
    }

    @Test
    void anEmFontSizeCompoundsThroughNesting() {
        ComputedStyle style = styleOfTarget(
                "body { font-size: 10px } div { font-size: 2em }",
                "<div><div id=target>x</div></div>");
        // 10px = 7.5pt, doubled twice.
        assertEquals(30f, style.fontSize(), 0.01f);
    }

    @Test
    void aRemFontSizeIsRelativeToTheRootRatherThanTheParent() {
        Document document = Jsoup.parse(
                "<html><head><style>html{font-size:20px}div{font-size:100px}"
                        + "#target{font-size:2rem}</style></head>"
                        + "<body><div><span id=target>x</span></div></body></html>");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        // 20px = 15pt, doubled — the 100px parent is irrelevant.
        assertEquals(30f, cascade.styleOf(document.selectFirst("#target")).fontSize(), 0.01f);
    }

    @Test
    void directionIsInheritedByDescendants() {
        ComputedStyle style = styleOfTarget(
                "div { direction: rtl }", "<div><span id=target>x</span></div>");
        assertTrue(style.rtl());
    }

    // --- Media and page rules ----------------------------------------------

    @Test
    void aPrintMediaBlockApplies() {
        ComputedStyle style = styleOfTarget(
                "@media print { p { color: red } }", "<p id=target>x</p>");
        assertEquals(Color.fromRgb255(255, 0, 0), style.color());
    }

    @Test
    void aScreenOnlyMediaBlockDoesNotApply() {
        ComputedStyle style = styleOfTarget(
                "@media screen { p { color: red } }", "<p id=target>x</p>");
        assertEquals(Color.BLACK, style.color());
    }

    @Test
    void aStyleElementRestrictedToScreenIsSkippedEntirely() {
        Document document = Jsoup.parse(
                "<style media=screen>p{color:red}</style><p id=target>x</p>");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        assertEquals(Color.BLACK, cascade.styleOf(document.selectFirst("#target")).color());
    }

    @Test
    void pageRulesSurviveOnTheStylesheetForLayoutToRead() {
        Document document = Jsoup.parse("<style>@page { margin: 2cm }</style><p id=target>x</p>");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        assertEquals(1, cascade.stylesheet().pageRules().size());
    }

    @Test
    void everyStyleElementInTheDocumentContributes() {
        Document document = Jsoup.parse(
                "<style>p{color:red}</style><style>p{font-size:30pt}</style><p id=target>x</p>");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        ComputedStyle style = cascade.styleOf(document.selectFirst("#target"));
        assertEquals(Color.fromRgb255(255, 0, 0), style.color());
        assertEquals(30f, style.fontSize(), 0.01f);
    }

    // --- Coverage of the whole tree ----------------------------------------

    @Test
    void everyElementInTheDocumentGetsAComputedStyle() {
        Document document = Jsoup.parse("<div><p><span>x</span></p></div>");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        for (Element element : document.getAllElements()) {
            assertTrue(cascade.styleOf(element) != null, "no style for <" + element.tagName() + ">");
        }
    }

    @Test
    void anElementFromAnotherDocumentGetsTheInitialStyle() {
        Document document = Jsoup.parse("<p>x</p>");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        Element stranger = Jsoup.parse("<p>y</p>").selectFirst("p");
        assertEquals(ComputedStyle.INITIAL_FONT_SIZE, cascade.styleOf(stranger).fontSize(), 0.01f);
    }

    @Test
    void aDocumentWithNoStylesheetAtAllStillCascadesTheUserAgentRules() {
        Document document = Jsoup.parse("<h1 id=target>x</h1>");
        Cascade cascade = Cascade.apply(document, Cascade.authorStylesheet(document));
        ComputedStyle style = cascade.styleOf(document.selectFirst("#target"));
        assertEquals(Display.BLOCK, style.display());
        assertEquals(ComputedStyle.INITIAL_FONT_SIZE * 2f, style.fontSize(), 0.01f);
        assertTrue(style.bold());
    }
}
