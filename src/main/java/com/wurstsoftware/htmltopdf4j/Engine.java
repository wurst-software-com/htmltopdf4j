package com.wurstsoftware.htmltopdf4j;

import com.wurstsoftware.htmltopdf4j.box.BoxTree;
import com.wurstsoftware.htmltopdf4j.box.BoxTreeBuilder;
import com.wurstsoftware.htmltopdf4j.layout.Layout;
import com.wurstsoftware.htmltopdf4j.layout.LayoutResult;
import com.wurstsoftware.htmltopdf4j.pdf.PdfDocumentWriter;
import com.wurstsoftware.htmltopdf4j.style.Cascade;
import com.wurstsoftware.htmltopdf4j.style.Stylesheet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * The render seam: HTML in, PDF bytes out.
 *
 * <p>An {@code Engine} holds no per-render state, so one instance is safe to
 * share across threads and to keep for the life of an application. Everything a
 * render needs — its Faces, its images, its links — lives in objects created
 * inside {@link #renderHtml}, which is the whole reason the caller's
 * {@link RenderOptions} is immutable and separate from the internal render
 * context.
 *
 * <p>The pipeline is: parse the Document, cascade its styles, generate the Box
 * tree, flow it onto Pages, and write the PDF. Each stage is independently
 * testable, and the only stage that touches bytes is the last one.
 */
public final class Engine {

    public Engine() {}

    /** Renders an HTML Document to PDF with the default options. */
    public byte[] renderHtml(String html) {
        return renderHtml(html, RenderOptions.defaults());
    }

    /**
     * Renders an HTML Document to PDF.
     *
     * @throws EmptyDocumentException when the Document would put nothing on a Page
     * @throws RenderException when the Document cannot be rendered at all
     */
    public byte[] renderHtml(String html, RenderOptions options) {
        if (html == null) {
            throw new IllegalArgumentException("html");
        }
        Document document = Jsoup.parse(html);
        Stylesheet stylesheet = Cascade.authorStylesheet(document);
        Cascade cascade = Cascade.apply(document, stylesheet);
        BoxTree tree = BoxTreeBuilder.build(document, cascade);
        if (!tree.hasContent()) {
            throw new EmptyDocumentException();
        }
        LayoutResult laid = Layout.layout(tree, stylesheet, options);
        return PdfDocumentWriter.write(laid.pages(), laid.context());
    }
}
