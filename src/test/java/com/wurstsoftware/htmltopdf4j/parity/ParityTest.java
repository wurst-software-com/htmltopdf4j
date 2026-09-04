package com.wurstsoftware.htmltopdf4j.parity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.wurstsoftware.htmltopdf4j.Engine;
import com.wurstsoftware.htmltopdf4j.Paper;
import com.wurstsoftware.htmltopdf4j.RenderOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parity: every Fixture rendered by this engine, checked against the
 * Expectation the reference engine's output satisfied.
 *
 * <p>One test case per Fixture, so a failure names the Fixture and the
 * Expectation it missed rather than stopping the whole suite at the first one.
 */
class ParityTest {

    private static final Path FIXTURE_DIRECTORY = Path.of("src/test/resources/fixtures");

    static List<Fixture> fixtures() {
        return Fixture.ALL;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void meetsItsExpectation(Fixture fixture) {
        List<String> failures = check(fixture);
        if (KnownFailures.contains(fixture.id())) {
            assertTrue(!failures.isEmpty(),
                    fixture.id() + " now meets its Expectation — remove it from the known-failures ledger");
            Assumptions.abort(fixture.id() + " is a known failure: " + String.join("; ", failures));
        }
        if (!failures.isEmpty()) {
            fail(fixture.id() + ":\n  " + String.join("\n  ", failures));
        }
    }

    /** Every Expectation this Fixture misses, so one run reports all of them. */
    private static List<String> check(Fixture fixture) {
        List<String> failures = new ArrayList<>();
        RenderedPdf pdf;
        try {
            pdf = RenderedPdf.of(render(fixture));
        } catch (RuntimeException e) {
            return List.of("render threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (!pdf.isWellFormed()) {
            return List.of("output is not a well-formed PDF");
        }

        Expectation.PdfAssertions expected = Expectation.load(fixture).pdfAssertions();
        for (String operator : expected.mustContainOperators()) {
            if (!pdf.content().contains(operator) && !pdf.raw().contains(operator)) {
                failures.add("missing PDF operator `" + operator + "`");
            }
        }
        for (String text : expected.mustContainText()) {
            if (!pdf.content().contains(text)) {
                failures.add("rendered content is missing text `" + text + "`");
            }
        }
        if (expected.minSizeBytes() != null && pdf.sizeBytes() < expected.minSizeBytes()) {
            failures.add("size " + pdf.sizeBytes() + " is below the minimum " + expected.minSizeBytes());
        }
        if (expected.maxSizeBytes() != null && pdf.sizeBytes() > expected.maxSizeBytes()) {
            failures.add("size " + pdf.sizeBytes() + " is above the maximum " + expected.maxSizeBytes());
        }
        if (expected.minPages() != null && pdf.pageCount() < expected.minPages()) {
            failures.add(pdf.pageCount() + " page(s), fewer than the minimum " + expected.minPages());
        }
        return failures;
    }

    /**
     * Renders with Chromium's page geometry, which is what the Expectations were
     * recorded against. Margins come from each Fixture's own {@code @page} rule.
     */
    private static byte[] render(Fixture fixture) {
        return new Engine().renderHtml(
                fixture.html(),
                RenderOptions.builder()
                        .paper(Paper.LETTER)
                        .baseDirectory(FIXTURE_DIRECTORY.resolve(fixture.layer()))
                        .build());
    }

    @Test
    void everyFixtureHasAnExpectation() {
        Fixture.ALL.forEach(Expectation::load);
    }
}
