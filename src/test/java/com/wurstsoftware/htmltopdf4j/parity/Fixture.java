package com.wurstsoftware.htmltopdf4j.parity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * An HTML input held in the test corpus to exercise a named area of the Coverage
 * matrix.
 *
 * <p>The list below is the corpus. Forty-one of them are ported verbatim; the
 * rest are this port's own, and each says in its Expectation's description why
 * it had no reference Expectation to port.
 * Adding a Fixture means
 * dropping the HTML into {@code src/test/resources/fixtures/<layer>/}, adding
 * its Expectation, and naming it here.
 */
public record Fixture(String layer, String name) {

    public static final List<Fixture> ALL = List.of(
            new Fixture("features", "typography"),
            new Fixture("features", "text-decoration"),
            new Fixture("features", "colors"),
            new Fixture("features", "gradients"),
            new Fixture("features", "background-image"),
            new Fixture("features", "box-model"),
            new Fixture("features", "borders"),
            new Fixture("features", "lists"),
            new Fixture("features", "tables"),
            new Fixture("features", "rowspan"),
            new Fixture("features", "images"),
            new Fixture("features", "flexbox"),
            new Fixture("features", "flex-item"),
            new Fixture("features", "grid"),
            new Fixture("features", "grid-rows"),
            new Fixture("features", "grid-2d"),
            new Fixture("features", "grid-areas"),
            new Fixture("features", "floats"),
            new Fixture("features", "positioning"),
            new Fixture("features", "line-height"),
            new Fixture("features", "fixed-per-page"),
            new Fixture("features", "paged-media"),
            new Fixture("features", "font-family"),
            new Fixture("features", "font-face"),
            new Fixture("features", "sizing"),
            new Fixture("features", "pct-sizing"),
            new Fixture("features", "custom-properties"),
            new Fixture("features", "calc"),
            new Fixture("features", "text-polish"),
            new Fixture("features", "generated-content"),
            new Fixture("features", "inline-block"),
            new Fixture("features", "links"),
            new Fixture("features", "flex-wrap"),
            new Fixture("features", "rtl"),
            new Fixture("features", "z-index"),
            new Fixture("features", "inline-images"),
            new Fixture("features", "rich-cells"),
            new Fixture("features", "break-inside"),
            new Fixture("features", "float-break"),
            new Fixture("features", "inline-decoration"),
            new Fixture("features", "flex-align"),
            new Fixture("features", "grid-stretch"),
            new Fixture("features", "cell-baseline"),
            new Fixture("features", "external-css"),
            new Fixture("combined", "invoice"),
            new Fixture("edge-cases", "unicode"),
            new Fixture("edge-cases", "long-table"),
            new Fixture("edge-cases", "page-breaks"));

    /** The Fixture's HTML. */
    public String html() {
        String resource = "/fixtures/" + layer + "/" + name + ".html";
        try (InputStream stream = Fixture.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("missing Fixture " + this + " at " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read Fixture " + resource, e);
        }
    }

    /** The identifier used in test names, failure messages and the ledger. */
    public String id() {
        return layer + "/" + name;
    }

    @Override
    public String toString() {
        return id();
    }
}
