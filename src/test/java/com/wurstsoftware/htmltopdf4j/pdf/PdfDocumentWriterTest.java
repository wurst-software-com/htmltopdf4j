package com.wurstsoftware.htmltopdf4j.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.PageSize;
import com.wurstsoftware.htmltopdf4j.layout.AnchorMark;
import com.wurstsoftware.htmltopdf4j.layout.LinkArea;
import com.wurstsoftware.htmltopdf4j.layout.Page;
import com.wurstsoftware.htmltopdf4j.paint.Color;
import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import com.wurstsoftware.htmltopdf4j.render.RenderContext;
import com.wurstsoftware.htmltopdf4j.text.EmbeddedFace;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import com.wurstsoftware.htmltopdf4j.text.Standard14Face;
import com.wurstsoftware.htmltopdf4j.text.TestFonts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * The writer is tested through an independent PDF reader rather than by matching
 * strings in the output. A file that string-matches but does not parse is not a
 * PDF, and the render seam cannot see the difference; this is one of the few
 * places the spec keeps an in-module test for exactly that reason.
 */
class PdfDocumentWriterTest {

    private static final PageSize A4 = PageSize.A4;

    @Test
    void writesAParseableDocumentWithSelectableText() throws IOException {
        Page page = new Page();
        page.add(new PaintCommand.SetFillColor(Color.BLACK));
        page.add(new PaintCommand.Text("Hello, World", 48f, 700f, 12f, 0, false, 0f));

        byte[] pdf = PdfDocumentWriter.write(List.of(page), helveticaContext());

        assertTrue(startsWith(pdf, "%PDF-1.7"), "a PDF declares its version first");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(A4.width(), document.getPage(0).getMediaBox().getWidth(), 0.01f);
            assertTrue(new PDFTextStripper().getText(document).contains("Hello, World"));
        }
    }

    @Test
    void paginatesEveryPageIntoTheTree() throws IOException {
        List<Page> pages = List.of(textPage("First"), textPage("Second"), textPage("Third"));

        try (PDDocument document = Loader.loadPDF(PdfDocumentWriter.write(pages, helveticaContext()))) {
            assertEquals(3, document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("First") && text.contains("Second") && text.contains("Third"));
        }
    }

    /**
     * WinAnsi carries Latin-1 and the CP1252 specials, so a curly quote or a
     * bullet survives into a standard-14 Face instead of becoming a question mark.
     */
    @Test
    void standard14TextKeepsWinAnsiCharacters() throws IOException {
        byte[] pdf = PdfDocumentWriter.write(List.of(textPage("café — “quoted” •")), helveticaContext());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("café"), "Latin-1 survives: " + text);
            assertTrue(text.contains("“quoted”"), "CP1252 curly quotes survive: " + text);
        }
    }

    @Test
    void parenthesesAndBackslashesAreEscaped() throws IOException {
        byte[] pdf = PdfDocumentWriter.write(List.of(textPage("A (test) \\ value")), helveticaContext());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertTrue(new PDFTextStripper().getText(document).contains("A (test) \\ value"));
        }
    }

    @Test
    void embedsAndSubsetsAFaceAsAType0Composite() throws IOException {
        Path path = TestFonts.available().get(0);
        EmbeddedFace face = EmbeddedFace.fromBytes(Files.readAllBytes(path), path.getFileName().toString());
        RenderContext context =
                new RenderContext(A4, List.of(FaceChain.of(face)), List.of(), List.of());

        byte[] pdf = PdfDocumentWriter.write(List.of(textPage("Hamburgefonstiv")), context);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            var resources = document.getPage(0).getResources();
            var fontName = resources.getFontNames().iterator().next();
            var font = resources.getFont(fontName);
            assertEquals("Type0", font.getCOSObject().getNameAsString("Subtype"));
            assertTrue(
                    font.getName().matches("[A-Z]{6}\\+.+"),
                    "a subset font carries the six-letter tag readers look for, got " + font.getName());
            // Identity-H plus /ToUnicode is what keeps the text extractable.
            assertTrue(new PDFTextStripper().getText(document).contains("Hamburgefonstiv"));
        }
        assertTrue(pdf.length < Files.size(path), "the embedded subset is smaller than the whole face");
    }

    @Test
    void writesExternalAndFragmentLinksAndDropsDeadOnes() throws IOException {
        Page first = new Page();
        first.add(new PaintCommand.Text("Chapter One", 48f, 780f, 20f, 0, false, 0f));
        first.addAnchor(new AnchorMark("top", 1, "Chapter One", 780f));

        Page second = textPage("Links");
        second.addLink(new LinkArea(new Rect(48f, 600f, 90f, 12f), 1));
        second.addLink(new LinkArea(new Rect(48f, 580f, 90f, 12f), 2));
        second.addLink(new LinkArea(new Rect(48f, 560f, 90f, 12f), 3));

        RenderContext context = new RenderContext(
                A4,
                List.of(FaceChain.of(Standard14Face.HELVETICA)),
                List.of("https://example.com/x", "#top", "#missing"),
                List.of());

        try (PDDocument document = Loader.loadPDF(PdfDocumentWriter.write(List.of(first, second), context))) {
            List<PDAnnotation> annotations = document.getPage(1).getAnnotations();
            assertEquals(2, annotations.size(), "the dead #missing fragment gets no annotation");

            PDAnnotationLink external = (PDAnnotationLink) annotations.get(0);
            assertEquals("https://example.com/x", ((PDActionURI) external.getAction()).getURI());

            PDAnnotationLink internal = (PDAnnotationLink) annotations.get(1);
            assertNotNull(internal.getDestination(), "a live fragment resolves to a destination");
        }
    }

    @Test
    void nestsHeadingsIntoAnOutlineTree() throws IOException {
        Page page = new Page();
        page.add(new PaintCommand.Text("Chapter One", 48f, 780f, 20f, 0, false, 0f));
        page.addAnchor(new AnchorMark("one", 1, "Chapter One", 780f));
        // A skipped level must not break the nesting: h3 still lands under h1.
        page.addAnchor(new AnchorMark(null, 3, "Sección Única", 700f));
        page.addAnchor(new AnchorMark(null, 1, "Chapter Two", 600f));

        try (PDDocument document = Loader.loadPDF(PdfDocumentWriter.write(List.of(page), helveticaContext()))) {
            var outline = document.getDocumentCatalog().getDocumentOutline();
            assertNotNull(outline, "headings produce a bookmark tree");

            List<PDOutlineItem> top = new java.util.ArrayList<>();
            outline.children().forEach(top::add);
            assertEquals(2, top.size(), "two h1s are top-level");
            assertEquals("Chapter One", top.get(0).getTitle());

            PDOutlineItem nested = top.get(0).getFirstChild();
            assertNotNull(nested, "the h3 nests under the preceding h1");
            // Non-ASCII titles go out as UTF-16BE and must come back intact.
            assertEquals("Sección Única", nested.getTitle());
        }
    }

    @Test
    void writesClipAndColourOperators() {
        Page page = new Page();
        page.add(new PaintCommand.PushClipRect(new Rect(10f, 20f, 30f, 40f)));
        page.add(new PaintCommand.SetFillColor(Color.fromRgb255(255, 0, 0)));
        page.add(new PaintCommand.SetStrokeColor(Color.fromRgb255(0, 0, 255)));
        page.add(new PaintCommand.PopClip());

        FontPlans plans = FontPlans.plan(
                FontPlans.discover(List.of(page), helveticaContext()), 3, 10);
        String content = new String(
                ContentStream.of(page, helveticaContext(), plans), java.nio.charset.StandardCharsets.ISO_8859_1);

        assertTrue(content.contains("q\n10.00 20.00 30.00 40.00 re W n\n"), content);
        assertTrue(content.contains("1.0000 0.0000 0.0000 rg\n"), content);
        assertTrue(content.contains("0.0000 0.0000 1.0000 RG\n"), content);
        assertTrue(content.contains("Q\n"), content);
    }

    @Test
    void subsetTagsAreStableAcrossRuns() {
        var glyphs = new java.util.TreeSet<>(List.of(3, 40, 41, 72));
        assertEquals(PdfSyntax.subsetTag(glyphs), PdfSyntax.subsetTag(glyphs));
        assertTrue(PdfSyntax.subsetTag(glyphs).matches("[A-Z]{6}"));
    }

    @Test
    void encodesTextStringsForOutlineTitles() {
        assertEquals("(plain)", PdfSyntax.textString("plain"));
        assertTrue(PdfSyntax.textString("Sección").startsWith("<FEFF"));
        assertEquals("0041", PdfSyntax.utf16BeHex("A"));
        assertEquals("4E16", PdfSyntax.utf16BeHex("世"));
        // An astral scalar becomes a UTF-16 surrogate pair.
        assertEquals("D800DF48", PdfSyntax.utf16BeHex(new String(Character.toChars(0x10348))));
    }

    private static Page textPage(String text) {
        Page page = new Page();
        page.add(new PaintCommand.SetFillColor(Color.BLACK));
        page.add(new PaintCommand.Text(text, 48f, 700f, 12f, 0, false, 0f));
        return page;
    }

    private static RenderContext helveticaContext() {
        return RenderContext.of(A4, Standard14Face.HELVETICA);
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        return new String(bytes, 0, Math.min(prefix.length(), bytes.length),
                java.nio.charset.StandardCharsets.ISO_8859_1).equals(prefix);
    }
}
