package com.wurstsoftware.htmltopdf4j.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.FaceSource;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.text.FontLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Resolving a {@code font-family} to a Face chain, and who has to fake bold. */
class FaceRegistryTest {

    private static FaceRegistry registry() {
        return new FaceRegistry(FaceSource.HELVETICA);
    }

    private static ComputedStyle style(Map<String, String> declarations) {
        return ComputedStyle.of(declarations);
    }

    /** An installed family that ships both a regular and a bold file. */
    private static Optional<String> familyWithRealBold() {
        return FontLibrary.index().values().stream()
                .filter(entries -> entries.stream().anyMatch(FontLibrary.Entry::bold)
                        && entries.stream().anyMatch(entry -> !entry.bold()))
                .map(entries -> entries.get(0).family())
                .findFirst();
    }

    @Test
    void aStyleWithNoFamilyGetsTheDefaultChain() {
        assertEquals(0, registry().indexFor(style(Map.of())));
    }

    @Test
    void theSameRequestIsResolvedOnceAndShared() {
        FaceRegistry faces = registry();
        ComputedStyle style = style(Map.of("font-family", "Nonesuch"));

        assertEquals(faces.indexFor(style), faces.indexFor(style));
    }

    @Test
    void differentWeightsOfOneFamilyAreDifferentChains() {
        FaceRegistry faces = registry();

        assertTrue(faces.indexFor(style(Map.of("font-family", "Nonesuch")))
                != faces.indexFor(style(Map.of("font-family", "Nonesuch", "font-weight", "bold"))));
    }

    @Test
    void aFamilyThatIsNotInstalledFallsBackToTheDefaultFace() {
        FaceRegistry faces = registry();
        int index = faces.indexFor(style(Map.of("font-family", "No Such Family At All")));

        assertEquals(faces.defaultFace(), faces.chain(index).primary());
    }

    @Test
    void everyChainCarriesFallbacksSoAMissingGlyphHasSomewhereToGo() {
        FaceRegistry faces = registry();
        int index = faces.indexFor(style(Map.of("font-family", "No Such Family At All")));

        Assumptions.assumeFalse(FontLibrary.index().isEmpty(), "no system fonts to fall back to");
        assertFalse(faces.chain(index).fallbacks().isEmpty());
    }

    @Test
    void boldTextInAFamilyWithNoBoldFaceHasToBeFaked() {
        FaceRegistry faces = registry();
        int index = faces.indexFor(style(Map.of("font-weight", "bold")));

        assertTrue(faces.syntheticBold(index), "the default Helvetica has no bold to embed");
    }

    @Test
    void boldTextInAFamilyThatHasARealBoldFaceIsNotFakedOnTop() {
        // Emboldening a Face that is already bold draws it twice as heavy.
        String family = familyWithRealBold().orElse(null);
        Assumptions.assumeTrue(family != null, "no installed family ships both weights");

        FaceRegistry faces = registry();
        int index = faces.indexFor(style(Map.of("font-family", family, "font-weight", "bold")));

        Assumptions.assumeTrue(faces.chain(index).primary().bold(), "the bold file was not readable");
        assertFalse(faces.syntheticBold(index));
    }

    @Test
    void textThatIsNotBoldIsNeverFaked() {
        FaceRegistry faces = registry();

        assertFalse(faces.syntheticBold(faces.indexFor(style(Map.of()))));
        assertFalse(faces.syntheticBold(faces.indexFor(style(Map.of("font-family", "Nonesuch")))));
    }

    @Test
    void aDeclaredFontFaceBeatsAnInstalledFamilyOfTheSameName() throws IOException {
        String family = FontLibrary.index().values().stream()
                .map(entries -> entries.get(0))
                .findFirst()
                .map(FontLibrary.Entry::family)
                .orElse(null);
        Assumptions.assumeTrue(family != null, "no system fonts installed");
        byte[] program = Files.readAllBytes(
                FontLibrary.find(family, false, false).orElseThrow().path());

        FaceRegistry faces = registry();
        faces.declare("Brand", program);
        int declared = faces.indexFor(style(Map.of("font-family", "Brand")));

        assertEquals("Brand", faces.chain(declared).primary().name());
    }

    @Test
    void aFontFaceProgramTheShaperCannotReadLeavesTheFamilyUnresolved() {
        FaceRegistry faces = registry();
        faces.declare("Broken", new byte[] {1, 2, 3});

        int index = faces.indexFor(style(Map.of("font-family", "Broken")));
        assertEquals(faces.defaultFace(), faces.chain(index).primary());
    }

    @Test
    void theChainListIsACopySoACallerCannotReachIntoTheRegistry() {
        FaceRegistry faces = registry();
        List<?> chains = faces.chains();
        faces.indexFor(style(Map.of("font-family", "Nonesuch")));

        assertEquals(1, chains.size(), "the returned list should not grow behind the caller's back");
    }
}
