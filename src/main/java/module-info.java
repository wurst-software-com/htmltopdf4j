/**
 * A pure-Java HTML-to-PDF rendering engine.
 *
 * <p>The public surface is deliberately small: {@link com.wurstsoftware.htmltopdf4j.Engine}
 * and the options it takes. Everything below it — the Cascade, the Box tree,
 * Layout, the PDF writer — is exported because a caller may want to drive one
 * stage on its own, but the render seam is the supported way in.
 *
 * <p>{@code java.desktop} is required, and not optional: the shaper is AWT's
 * {@code Font.layoutGlyphVector} and images are decoded through ImageIO.
 */
module com.wurstsoftware.htmltopdf4j {

    requires java.desktop;
    requires org.jsoup;
    requires com.helger.css;
    // ph-css returns ph-commons collection types from its parse tree, so the
    // module has to be readable even though none of it reaches this API.
    requires com.helger.commons;
    requires org.apache.fontbox;
    requires org.apache.pdfbox.io;

    exports com.wurstsoftware.htmltopdf4j;
    exports com.wurstsoftware.htmltopdf4j.box;
    exports com.wurstsoftware.htmltopdf4j.image;
    exports com.wurstsoftware.htmltopdf4j.layout;
    exports com.wurstsoftware.htmltopdf4j.paint;
    exports com.wurstsoftware.htmltopdf4j.pdf;
    exports com.wurstsoftware.htmltopdf4j.render;
    exports com.wurstsoftware.htmltopdf4j.style;
    exports com.wurstsoftware.htmltopdf4j.text;
}
