package com.wurstsoftware.htmltopdf4j.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The Fallback chain: which Face draws which part of a run.
 *
 * <p>Segmentation is the one place a missing glyph can turn into a blank in the
 * output, so the cases that matter are the ones where the primary cannot draw
 * something.
 */
class FaceChainTest {

    private static final Face HELVETICA = Standard14Face.HELVETICA;

    /** A system Face that can draw {@code text}, or nothing on a machine with none. */
    private static Optional<Face> faceCovering(String text) {
        for (Path path : TestFonts.available()) {
            try {
                EmbeddedFace face = EmbeddedFace.fromBytes(
                        Files.readAllBytes(path), path.getFileName().toString());
                if (face.canDisplayAll(text)) {
                    return Optional.of(face);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (RuntimeException e) {
                // A face this engine cannot parse is not a fallback candidate.
            }
        }
        return Optional.empty();
    }

    @Test
    void aChainOfOneNeverSegments() {
        FaceChain chain = FaceChain.of(HELVETICA);

        assertNull(chain.segment("anything"), "a chain with no fallbacks has nothing to segment into");
        assertEquals(HELVETICA, chain.at(0));
    }

    @Test
    void textThePrimaryCoversIsLeftWholeEvenWhenFallbacksExist() {
        FaceChain chain = new FaceChain(HELVETICA, List.of(HELVETICA));

        assertNull(chain.segment("plain ascii"));
    }

    @Test
    void anIndexPastZeroReachesTheFallbacks() {
        Face fallback = Standard14Face.HELVETICA;
        FaceChain chain = new FaceChain(HELVETICA, List.of(fallback));

        assertEquals(HELVETICA, chain.at(0));
        assertEquals(fallback, chain.at(1));
    }

    @Test
    void aCharacterCoveredNowhereStaysWithThePrimarySoItsNotdefShows() {
        // Blank output would tell the reader nothing; a .notdef box at least
        // says a glyph is missing.
        FaceChain chain = new FaceChain(HELVETICA, List.of(HELVETICA));
        List<FaceChain.Segment> segments = chain.segment("a中b");

        assertNotNull(segments);
        assertTrue(segments.stream().allMatch(segment -> segment.chainIndex() == 0));
    }

    @Test
    void measuringWithoutSegmentationIsJustThePrimarysMeasurement() {
        FaceChain chain = FaceChain.of(HELVETICA);

        assertEquals(HELVETICA.measure("Hello", 12f), chain.measure("Hello", 12f), 0.001f);
    }

    @Test
    void theAscentOfAnUnsegmentedRunIsThePrimarys() {
        FaceChain chain = FaceChain.of(HELVETICA);

        assertEquals(HELVETICA.ascent(12f), chain.ascent("Hello", 12f), 0.001f);
        assertEquals(HELVETICA.descent(12f), chain.descent("Hello", 12f), 0.001f);
    }

    @Test
    void aRunSplitsWhereTheFallbackTakesOver() {
        Face fallback = faceCovering("中").or(() -> faceCovering("α")).orElse(null);
        Assumptions.assumeTrue(fallback != null, "no system Face covers a non-WinAnsi character");
        String foreign = fallback.canDisplayAll("中") ? "中" : "α";
        Assumptions.assumeFalse(HELVETICA.canDisplayAll(foreign), "the primary already covers it");

        FaceChain chain = new FaceChain(HELVETICA, List.of(fallback));
        List<FaceChain.Segment> segments = chain.segment("ab" + foreign + "cd");

        assertNotNull(segments);
        assertEquals(List.of("ab", foreign, "cd"), segments.stream()
                .map(FaceChain.Segment::text).toList());
        assertEquals(List.of(0, 1, 0), segments.stream()
                .map(FaceChain.Segment::chainIndex).toList());
    }

    @Test
    void whitespaceJoinsTheRunAroundItRatherThanForcingAFontSwitch() {
        Face fallback = faceCovering("中").or(() -> faceCovering("α")).orElse(null);
        Assumptions.assumeTrue(fallback != null, "no system Face covers a non-WinAnsi character");
        String foreign = fallback.canDisplayAll("中") ? "中" : "α";
        Assumptions.assumeFalse(HELVETICA.canDisplayAll(foreign), "the primary already covers it");

        FaceChain chain = new FaceChain(HELVETICA, List.of(fallback));
        List<FaceChain.Segment> segments = chain.segment("ab " + foreign);

        assertNotNull(segments);
        assertEquals(2, segments.size(), "the space should not open a third segment");
        assertEquals("ab ", segments.get(0).text());
    }

    @Test
    void aSegmentedRunIsMeasuredWithTheFaceThatWillDrawEachPart() {
        Face fallback = faceCovering("中").or(() -> faceCovering("α")).orElse(null);
        Assumptions.assumeTrue(fallback != null, "no system Face covers a non-WinAnsi character");
        String foreign = fallback.canDisplayAll("中") ? "中" : "α";
        Assumptions.assumeFalse(HELVETICA.canDisplayAll(foreign), "the primary already covers it");

        FaceChain chain = new FaceChain(HELVETICA, List.of(fallback));

        assertEquals(
                HELVETICA.measure("ab", 12f) + fallback.measure(foreign, 12f),
                chain.measure("ab" + foreign, 12f),
                0.01f);
    }

    @Test
    void theFallbackListIsCopiedSoACallerCannotMutateTheChain() {
        List<Face> fallbacks = new java.util.ArrayList<>(List.of(HELVETICA));
        FaceChain chain = new FaceChain(HELVETICA, fallbacks);
        fallbacks.clear();

        assertEquals(1, chain.fallbacks().size());
    }
}
