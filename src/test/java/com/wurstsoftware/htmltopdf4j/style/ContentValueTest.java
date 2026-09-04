package com.wurstsoftware.htmltopdf4j.style;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** The text a {@code content} property generates. */
class ContentValueTest {

    private static final UnaryOperator<String> ATTRIBUTES =
            Map.of("href", "https://example.com", "data-label", "Note")::get;
    private static final UnaryOperator<String> COUNTERS = Map.of("page", "7", "pages", "12")::get;

    private static String of(String value) {
        return ContentValue.of(value, ATTRIBUTES, COUNTERS);
    }

    @Test
    void aQuotedStringIsItsOwnContents() {
        assertEquals("Chapter ", of("\"Chapter \""));
    }

    @Test
    void singleQuotesWorkTheSameAsDouble() {
        assertEquals("Chapter ", of("'Chapter '"));
    }

    @Test
    void piecesAreConcatenatedInOrder() {
        assertEquals("Page 7 of 12", of("\"Page \" counter(page) \" of \" counter(pages)"));
    }

    @Test
    void anAttributeReferenceReadsTheElementsAttribute() {
        assertEquals("https://example.com", of("attr(href)"));
    }

    @Test
    void anUnknownAttributeGeneratesNothingAtAll() {
        // Not "Link: " with the reference dropped — a half-built label reads as a
        // rendering bug to whoever gets the PDF.
        assertEquals("", of("\"Link: \" attr(nosuchattribute)"));
    }

    @Test
    void anUnknownCounterGeneratesNothingAtAll() {
        assertEquals("", of("\"Item \" counter(nosuchcounter)"));
    }

    @Test
    void noneGeneratesNothing() {
        assertEquals("", of("none"));
        assertEquals("", of("NONE"));
    }

    @Test
    void normalGeneratesNothing() {
        assertEquals("", of("normal"));
    }

    @Test
    void aNullOrBlankValueGeneratesNothing() {
        assertEquals("", of(null));
        assertEquals("", of("   "));
    }

    @Test
    void aFunctionThisEngineDoesNotImplementGeneratesNothing() {
        assertEquals("", of("open-quote"));
        assertEquals("", of("url(bullet.png)"));
    }

    @Test
    void anUnterminatedStringGeneratesNothing() {
        assertEquals("", of("\"unclosed"));
    }

    @Test
    void aHexEscapeBecomesTheCodePointItNames() {
        assertEquals("\u201C", of("\"\\201C\""));
    }

    @Test
    void aSpaceAfterAHexEscapeTerminatesItRatherThanBeingText() {
        assertEquals("\u201Cx", of("\"\\201C x\""));
    }

    @Test
    void anEscapedQuoteStaysInsideTheString() {
        assertEquals("say \"hi\"", of("\"say \\\"hi\\\"\""));
    }

    @Test
    void aResolverThatKnowsNothingMakesEveryReferenceGenerateNothing() {
        assertEquals("", ContentValue.of("attr(href)", ContentValue.NONE, ContentValue.NONE));
        assertEquals("plain", ContentValue.of("\"plain\"", ContentValue.NONE, ContentValue.NONE));
    }
}
