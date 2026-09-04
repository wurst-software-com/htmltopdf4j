package com.wurstsoftware.htmltopdf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The one document in the suite big enough to notice how the engine scales.
 *
 * <p>1,391 rows and 22,166 cells of PhpSpreadsheet output, against a Parity
 * corpus whose largest table is 60 rows. Nothing here asserts behaviour the
 * Fixtures do not already cover; what it catches is the cost of that behaviour —
 * a column measurement that became quadratic, a cascade lookup that stopped
 * being cached, a Display list that keeps every Page live at once. See
 * {@code src/test/resources/scale/README.md}.
 *
 * <p>Tagged {@code scale} and excluded from {@code mvn test}: run it with
 * {@code mvn test -Pscale}, which also caps the heap, so holding the whole
 * document twice fails rather than merely costing.
 */
@Tag("scale")
class ScaleTest {

    private static String html;
    private static byte[] pdf;

    @BeforeAll
    static void render() {
        html = resource("/scale/reg-2-9-1.html");
        pdf = new Engine().renderHtml(html, RenderOptions.defaults());
    }

    private static String resource(String path) {
        try (var source = ScaleTest.class.getResourceAsStream(path)) {
            assertNotNull(source, path + " should be on the test classpath");
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The document truncated to its first {@code rows} {@code <tr>} elements. */
    private static String firstRows(int rows) {
        int cut = 0;
        for (int i = 0; i < rows; i++) {
            cut = html.indexOf("</tr>", cut);
            if (cut < 0) {
                throw new IllegalStateException("the document has fewer than " + rows + " rows");
            }
            cut += "</tr>".length();
        }
        return html.substring(0, cut) + "</table></body></html>";
    }

    @Test
    void theWholeSpreadsheetRendersToAPdfAThirdPartyParserAccepts() throws IOException {
        try (PDDocument parsed = Loader.loadPDF(pdf)) {
            assertTrue(parsed.getNumberOfPages() >= 10,
                    "1,391 rows should paginate widely, not collapse onto a few Pages");

            // Not landscape: the document asks for it through a Page it names
            // (`@page page0 { size: landscape }`, selected by `page: page0`) and
            // `@page` contributes margins only here — issue #34. What is
            // asserted is that every Page is the same sheet, because a document
            // that changes paper mid-run is broken either way.
            PDPage first = parsed.getPage(0);
            for (PDPage page : parsed.getPages()) {
                assertEquals(first.getMediaBox().getWidth(), page.getMediaBox().getWidth(), 0.5f);
                assertEquals(first.getMediaBox().getHeight(), page.getMediaBox().getHeight(), 0.5f);
            }
        }
    }

    @Test
    void everyRowSurvivesToTheLastPage() throws IOException {
        try (PDDocument parsed = Loader.loadPDF(pdf)) {
            // Whitespace is stripped before matching: the sixteen declared
            // columns are far wider than the sheet the document ends up on, so
            // cell text wraps mid-word and a student id arrives as `1000` /
            // `0554` / `03` on three lines.
            String text = new PDFTextStripper().getText(parsed).replaceAll("\\s", "");

            assertTrue(text.contains("AdvisingCompletedorIncompleteStudentList"),
                    "the title row should be on the first Page");
            assertTrue(text.contains("1000055403"),
                    "the first row's student id should be rendered");
            assertTrue(text.contains(lastStudentId()),
                    "the last row's student id should be rendered, not dropped when the "
                            + "Page count grew");
        }
    }

    private static String lastStudentId() {
        int last = html.lastIndexOf("@abc.com");
        return html.substring(html.lastIndexOf('>', last) + 1, last);
    }

    @Test
    void theCostPerRowDoesNotGrowWithTheNumberOfRows() {
        // A quadratic pass — measuring every column against every row, or
        // resolving a cell's style by scanning the stylesheet — costs ~10x more
        // per row over ten times the rows. Linear work costs the same or less,
        // since the short render pays proportionally more JIT warm-up. The bound
        // is loose enough that only a change of complexity trips it.
        Engine engine = new Engine();
        String tenth = firstRows(140);

        engine.renderHtml(tenth, RenderOptions.defaults()); // warm the JIT

        long shortNanos = timeOf(() -> engine.renderHtml(tenth, RenderOptions.defaults()));
        long fullNanos = timeOf(() -> engine.renderHtml(html, RenderOptions.defaults()));

        double growth = ((double) fullNanos / 1391) / ((double) shortNanos / 140);
        assertTrue(growth < 4.0,
                "cost per row grew " + String.format("%.1f", growth) + "x over ten times the "
                        + "rows, which is the shape of a quadratic pass, not a linear one");
    }

    private static long timeOf(Runnable render) {
        long start = System.nanoTime();
        render.run();
        return System.nanoTime() - start;
    }
}
