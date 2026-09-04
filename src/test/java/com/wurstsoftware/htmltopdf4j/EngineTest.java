package com.wurstsoftware.htmltopdf4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * The render seam: HTML in, PDF bytes out.
 *
 * <p>These are the only tests that go all the way through the pipeline without
 * a Fixture, and they check the contract the seam publishes rather than what
 * lands on the Page — that is Parity's job.
 */
class EngineTest {

    private static final Engine ENGINE = new Engine();

    private static String text(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    @Test
    void aDocumentRendersToAPdf() {
        byte[] pdf = ENGINE.renderHtml("<p>Hello</p>");

        assertTrue(text(pdf).startsWith("%PDF-"));
        assertTrue(text(pdf).trim().endsWith("%%EOF"));
    }

    @Test
    void theDefaultOptionsRenderTheSameAsPassingThemExplicitly() {
        assertEquals(
                ENGINE.renderHtml("<p>Hello</p>").length,
                ENGINE.renderHtml("<p>Hello</p>", RenderOptions.defaults()).length);
    }

    @Test
    void aDocumentWithNothingToPaintIsRejectedRatherThanProducingABlankPdf() {
        assertThrows(EmptyDocumentException.class, () -> ENGINE.renderHtml(""));
        assertThrows(EmptyDocumentException.class, () -> ENGINE.renderHtml("<html></html>"));
    }

    @Test
    void nullHtmlIsAProgrammingErrorNotARenderFailure() {
        assertThrows(IllegalArgumentException.class, () -> ENGINE.renderHtml(null));
    }

    @Test
    void anEmptyDocumentExceptionIsARenderException() {
        // Callers that only want "the render failed" should be able to catch one type.
        assertTrue(RenderException.class.isAssignableFrom(EmptyDocumentException.class));
    }

    @Test
    void malformedHtmlIsRepairedRatherThanRefused() {
        byte[] pdf = ENGINE.renderHtml("<p>unclosed <b>bold");

        assertTrue(text(pdf).startsWith("%PDF-"));
    }

    @Test
    void thePaperChoiceReachesTheMediaBox() {
        byte[] a4 = ENGINE.renderHtml("<p>Hello</p>");
        byte[] letter = ENGINE.renderHtml(
                "<p>Hello</p>", RenderOptions.builder().paper(Paper.LETTER).build());

        assertTrue(text(a4).contains("842"), "expected the A4 height in the MediaBox");
        assertTrue(text(letter).contains("792"), "expected the Letter height in the MediaBox");
    }

    @Test
    void theSameInputRendersToTheSameBytesTwice() {
        assertArrayEquals(ENGINE.renderHtml("<p>Hello</p>"), ENGINE.renderHtml("<p>Hello</p>"));
    }

    @Test
    void oneEngineAndOneOptionsValueDriveManyConcurrentRenders() throws Exception {
        // The reference engine's options doubled as per-render scratch space;
        // this is the test that would have caught that.
        RenderOptions options = RenderOptions.defaults();
        String html = "<p>" + "word ".repeat(400) + "</p>";
        byte[] expected = ENGINE.renderHtml(html, options);

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<byte[]>> renders = java.util.Collections.nCopies(
                    32, () -> ENGINE.renderHtml(html, options));
            for (Future<byte[]> future : pool.invokeAll(renders)) {
                assertArrayEquals(expected, future.get());
            }
        }
    }

    @Test
    void anOptionsValueRoundTripsThroughItsBuilder() {
        RenderOptions options = RenderOptions.builder()
                .paper(Paper.LETTER)
                .margins(10f, 20f, 30f, 40f)
                .build();
        RenderOptions copy = options.toBuilder().build();

        assertEquals(options.paper(), copy.paper());
        assertEquals(options.marginTop(), copy.marginTop());
        assertEquals(options.marginRight(), copy.marginRight());
        assertEquals(options.marginBottom(), copy.marginBottom());
        assertEquals(options.marginLeft(), copy.marginLeft());
        assertEquals(options.pageSize(), copy.pageSize());
    }
}
