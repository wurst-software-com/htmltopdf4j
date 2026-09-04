/**
 * A pure-Java HTML-to-PDF rendering engine.
 *
 * <p>The public surface is deliberately small: {@link com.wurstsoftware.htmltopdf4j.Engine}
 * and the options it takes. Everything below it — the Cascade, the Box tree,
 * Layout, the Display list, the PDF writer — is internal, so the port is free to
 * change shape without breaking a caller.
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

    // Only the root package is API. The Box tree, the Display list, Layout and
    // the writer are all reachable from the tests, which are patched into this
    // module, but exporting them would freeze the internals of the port as a
    // contract with callers.
    exports com.wurstsoftware.htmltopdf4j;
}
