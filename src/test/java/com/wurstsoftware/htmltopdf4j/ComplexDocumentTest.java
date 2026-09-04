package com.wurstsoftware.htmltopdf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * One realistic document — an invoice — taken end to end through the seam.
 *
 * <p>The Fixtures each isolate one feature, which is what makes a Parity failure
 * readable. This does the opposite: it puts a gradient masthead, a grid with
 * spanning cells, two tables with spans and per-side borders, justified prose, a
 * float, a link, a forced page break and an absolutely-positioned box in one
 * document, so the stages are exercised against each other rather than one at a
 * time. It asserts on the finished PDF, read back with a third-party parser,
 * because that is what a caller actually receives.
 */
class ComplexDocumentTest {

    private static byte[] pdf;
    private static String extractedText;

    @BeforeAll
    static void render() throws IOException {
        pdf = new Engine().renderHtml(document(), RenderOptions.defaults());
        try (PDDocument parsed = Loader.loadPDF(pdf)) {
            extractedText = new PDFTextStripper().getText(parsed);
        }
    }

    private static String document() {
        try (var source = ComplexDocumentTest.class.getResourceAsStream("/documents/invoice.html")) {
            assertNotNull(source, "the invoice document should be on the test classpath");
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theWholeDocumentRendersToAPdfAThirdPartyParserAccepts() throws IOException {
        try (PDDocument parsed = Loader.loadPDF(pdf)) {
            assertTrue(parsed.getNumberOfPages() >= 3,
                    "an invoice with a forced page break should run to several pages");
        }
    }

    @Test
    void everyPageIsTheDeclaredPaperSize() throws IOException {
        // `@page { margin: … }` changes the margins, not the sheet: A4 is
        // 595.28 × 841.89pt and every page should still be exactly that.
        try (PDDocument parsed = Loader.loadPDF(pdf)) {
            for (PDPage page : parsed.getPages()) {
                assertEquals(595.28f, page.getMediaBox().getWidth(), 0.5f);
                assertEquals(841.89f, page.getMediaBox().getHeight(), 0.5f);
            }
        }
    }

    @Test
    void theContentOfEverySectionIsDrawn() {
        for (String phrase : List.of(
                "Wurst Software GmbH",          // the gradient masthead
                "Nordlicht Verlag AG",          // a grid cell
                "TOTAL DUE",                    // the grid cell spanning two rows
                "Layout engine port",           // a table body row
                "Total due",                    // the table footer
                "Payment window",               // the float
                "Prices exclude VAT",           // the ordered list
                "Appendix A",                   // past the forced page break
                "PAID")) {                      // the absolutely-positioned box
            assertTrue(extractedText.contains(phrase), "the PDF should contain `" + phrase + "`");
        }
    }

    @Test
    void aForcedPageBreakStartsTheAppendixOnAPageOfItsOwn() throws IOException {
        try (PDDocument parsed = Loader.loadPDF(pdf)) {
            int appendix = pageContaining(parsed, "Appendix A");
            int lineItems = pageContaining(parsed, "Layout engine port");

            assertTrue(appendix > lineItems, "`page-break-before: always` should push the appendix on");
            assertTrue(textOf(parsed, appendix).stripLeading().startsWith("Appendix A"),
                    "the appendix should be the first thing on its page");
        }
    }

    @Test
    void theTermsLinkBecomesAnAnnotationOnItsPage() throws IOException {
        try (PDDocument parsed = Loader.loadPDF(pdf)) {
            List<String> targets = new java.util.ArrayList<>();
            for (PDPage page : parsed.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink link
                            && link.getAction() instanceof PDActionURI uri) {
                        targets.add(uri.getURI());
                    }
                }
            }
            assertEquals(List.of("https://example.invalid/terms"), targets);
        }
    }

    @Test
    void theSameDocumentRendersToTheSameBytesTwice() {
        // A document this large is the strongest check that nothing in the
        // pipeline leaks iteration order into the output.
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                pdf, new Engine().renderHtml(document(), RenderOptions.defaults()));
    }

    private static int pageContaining(PDDocument parsed, String phrase) throws IOException {
        for (int page = 1; page <= parsed.getNumberOfPages(); page++) {
            if (textOf(parsed, page).contains(phrase)) {
                return page;
            }
        }
        throw new AssertionError("no page contains `" + phrase + "`");
    }

    private static String textOf(PDDocument parsed, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(parsed);
    }
}
