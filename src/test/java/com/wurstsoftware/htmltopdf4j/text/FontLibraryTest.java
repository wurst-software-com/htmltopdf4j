package com.wurstsoftware.htmltopdf4j.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The installed-font scanner.
 *
 * <p>What is installed is a property of the machine, so these assert the
 * scanner's rules rather than the presence of any particular family, and skip
 * where a machine with no fonts could tell us nothing.
 */
class FontLibraryTest {

    private static Map<String, List<FontLibrary.Entry>> installed() {
        Map<String, List<FontLibrary.Entry>> index = FontLibrary.index();
        Assumptions.assumeFalse(index.isEmpty(), "no system fonts to scan");
        return index;
    }

    @Test
    void scanningIsDoneOnceAndTheSameIndexHandedBack() {
        assertTrue(FontLibrary.index() == FontLibrary.index(), "the index should be cached");
    }

    @Test
    void everyIndexedEntryPointsAtAReadableFontFile() {
        for (List<FontLibrary.Entry> entries : installed().values()) {
            for (FontLibrary.Entry entry : entries) {
                assertTrue(Files.isReadable(entry.path()), entry.path() + " is not readable");
                String name = entry.path().getFileName().toString().toLowerCase(Locale.ROOT);
                assertTrue(name.endsWith(".ttf") || name.endsWith(".otf"), name);
            }
        }
    }

    @Test
    void theIndexIsKeyedByLowerCaseFamilyName() {
        for (Map.Entry<String, List<FontLibrary.Entry>> keyed : installed().entrySet()) {
            assertEquals(keyed.getKey(), keyed.getKey().toLowerCase(Locale.ROOT));
            for (FontLibrary.Entry entry : keyed.getValue()) {
                assertEquals(keyed.getKey(), entry.family().toLowerCase(Locale.ROOT));
            }
        }
    }

    @Test
    void anInstalledFamilyIsFoundByItsOwnName() {
        FontLibrary.Entry any = installed().values().iterator().next().get(0);

        assertEquals(
                Optional.of(any.family()),
                FontLibrary.find(any.family(), any.bold(), any.italic()).map(FontLibrary.Entry::family));
    }

    @Test
    void aFamilyNameIsMatchedWithoutRegardToCaseOrQuotes() {
        FontLibrary.Entry any = installed().values().iterator().next().get(0);

        assertTrue(FontLibrary.find(any.family().toUpperCase(Locale.ROOT), false, false).isPresent());
        assertTrue(FontLibrary.find("'" + any.family() + "'", false, false).isPresent());
    }

    @Test
    void aFamilyThatIsNotInstalledIsNotFound() {
        assertEquals(Optional.empty(), FontLibrary.find("No Such Family At All", false, false));
    }

    @Test
    void aGenericFamilyResolvesThroughItsCandidateList() {
        installed();
        // At least one of serif, sans-serif and monospace should resolve on any
        // machine with fonts; which one depends on what is installed.
        assertTrue(
                FontLibrary.find("serif", false, false).isPresent()
                        || FontLibrary.find("sans-serif", false, false).isPresent()
                        || FontLibrary.find("monospace", false, false).isPresent(),
                "no generic family resolved despite fonts being installed");
    }

    @Test
    void theFirstInstalledFamilyOfAListWins() {
        FontLibrary.Entry any = installed().values().iterator().next().get(0);

        assertEquals(
                FontLibrary.find(any.family(), false, false),
                FontLibrary.findAny(List.of("No Such Family", any.family()), false, false));
    }

    @Test
    void aListWithNothingInstalledResolvesToNothing() {
        assertEquals(
                Optional.empty(),
                FontLibrary.findAny(List.of("No Such Family", "Nor This One"), false, false));
    }

    @Test
    void anEmptyFamilyListResolvesToNothing() {
        assertEquals(Optional.empty(), FontLibrary.findAny(List.of(), false, false));
    }

    @Test
    void aFamilyWithNoItalicStillResolvesToItsRegularRatherThanADifferentFamily() {
        Optional<List<FontLibrary.Entry>> upright = installed().values().stream()
                .filter(entries -> entries.stream().noneMatch(FontLibrary.Entry::italic))
                .findFirst();
        Assumptions.assumeTrue(upright.isPresent(), "every installed family has an italic");
        String family = upright.get().get(0).family();

        assertEquals(
                Optional.of(family),
                FontLibrary.find(family, false, true).map(FontLibrary.Entry::family));
    }

    @Test
    void aBoldRequestPrefersTheBoldFileWhenTheFamilyHasOne() {
        Optional<List<FontLibrary.Entry>> withBold = installed().values().stream()
                .filter(entries -> entries.stream().anyMatch(FontLibrary.Entry::bold)
                        && entries.stream().anyMatch(entry -> !entry.bold()))
                .findFirst();
        Assumptions.assumeTrue(withBold.isPresent(), "no installed family has both weights");
        String family = withBold.get().get(0).family();

        assertTrue(FontLibrary.find(family, true, false).orElseThrow().bold());
        assertTrue(!FontLibrary.find(family, false, false).orElseThrow().bold());
    }

    @Test
    void localMatchesAFullFontNameAndNotOnlyAFamily() {
        // `local()` in an @font-face names a face, not a family: "Arial Bold"
        // is a full name that no family index would ever have a key for.
        java.util.Map<String, java.util.List<FontLibrary.Entry>> index = FontLibrary.index();
        org.junit.jupiter.api.Assumptions.assumeFalse(index.isEmpty(), "no system fonts installed");

        for (java.util.List<FontLibrary.Entry> entries : index.values()) {
            for (FontLibrary.Entry entry : entries) {
                for (String name : entry.names()) {
                    assertTrue(FontLibrary.local(name).isPresent(), name + " should resolve");
                }
            }
        }
    }

    @Test
    void localFallsBackToTheFamilyIndexForAPlainFamilyName() {
        java.util.Map<String, java.util.List<FontLibrary.Entry>> index = FontLibrary.index();
        org.junit.jupiter.api.Assumptions.assumeFalse(index.isEmpty(), "no system fonts installed");
        String family = index.values().iterator().next().get(0).family();

        assertTrue(FontLibrary.local(family).isPresent());
    }

    @Test
    void localFindsNothingForANameNoFaceCarries() {
        assertTrue(FontLibrary.local("No Such Face Anywhere At All").isEmpty());
    }
}
