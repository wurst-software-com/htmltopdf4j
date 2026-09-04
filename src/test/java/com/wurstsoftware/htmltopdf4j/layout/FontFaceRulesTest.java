package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.style.Stylesheet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What an {@code @font-face} rule declares, before anything is read from disk. */
class FontFaceRulesTest {

    @Test
    void aRuleWithoutASourceDeclaresNothing() {
        assertEquals(List.of(), rulesOf("@font-face { font-family: Brand; }"));
    }

    @Test
    void theFamilyIsUnquoted() {
        assertEquals("Brand Text", only("@font-face { font-family: 'Brand Text'; src: url(b.ttf); }").family());
    }

    @Test
    void aRegularUprightFaceIsTheDefault() {
        FontFaceRules rule = only("@font-face { font-family: Brand; src: url(b.ttf); }");
        assertFalse(rule.bold());
        assertFalse(rule.italic());
    }

    @Test
    void theWeightAndStyleDescriptorsNameTheVariant() {
        FontFaceRules rule =
                only("@font-face { font-family: Brand; src: url(b.ttf); font-weight: bold; font-style: italic; }");
        assertTrue(rule.bold());
        assertTrue(rule.italic());
    }

    @Test
    void aNumericWeightIsBoldFromSixHundred() {
        assertTrue(only("@font-face { font-family: B; src: url(b.ttf); font-weight: 700; }").bold());
        assertFalse(only("@font-face { font-family: B; src: url(b.ttf); font-weight: 500; }").bold());
    }

    @Test
    void aWeightRangeIsTheVariantItStartsAt() {
        // `400 700` is a variable face that covers the regular as well as the
        // bold, so filing it as the family's bold would leave the regular to a
        // fallback family.
        assertFalse(only("@font-face { font-family: B; src: url(b.ttf); font-weight: 400 700; }").bold());
        assertTrue(only("@font-face { font-family: B; src: url(b.ttf); font-weight: 600 900; }").bold());
    }

    @Test
    void anObliqueFaceCountsAsItalic() {
        assertTrue(only("@font-face { font-family: B; src: url(b.ttf); font-style: oblique 14deg; }").italic());
    }

    private static FontFaceRules only(String css) {
        return rulesOf(css).get(0);
    }

    private static List<FontFaceRules> rulesOf(String css) {
        return FontFaceRules.of(Stylesheet.parse(css, 0));
    }
}
