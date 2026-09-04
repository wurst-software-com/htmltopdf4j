package com.wurstsoftware.htmltopdf4j.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The fonts a render may draw with: the scan, the lookup, and the search path.
 *
 * <p>What is installed is a property of the machine, so the ones that go through
 * {@link FontEnvironment#shared()} assert the scanner's rules rather than the
 * presence of any particular family, and skip where a machine with no fonts
 * could tell us nothing. The ones that name their own search path do not have to
 * skip: they carry the fonts they need.
 */
class FontEnvironmentTest {

    private static Map<String, List<FontEnvironment.Entry>> installed() {
        Map<String, List<FontEnvironment.Entry>> index = FontEnvironment.shared().index();
        Assumptions.assumeFalse(index.isEmpty(), "no system fonts to scan");
        return index;
    }

    @Test
    void scanningIsDoneOnceAndTheSameIndexHandedBack() {
        assertTrue(
                FontEnvironment.shared().index() == FontEnvironment.shared().index(),
                "the index should be cached");
    }

    @Test
    void everyIndexedEntryPointsAtAReadableFontFile() {
        for (List<FontEnvironment.Entry> entries : installed().values()) {
            for (FontEnvironment.Entry entry : entries) {
                assertTrue(Files.isReadable(entry.path()), entry.path() + " is not readable");
                String name = entry.path().getFileName().toString().toLowerCase(Locale.ROOT);
                assertTrue(name.endsWith(".ttf") || name.endsWith(".otf"), name);
            }
        }
    }

    @Test
    void theIndexIsKeyedByLowerCaseFamilyName() {
        for (Map.Entry<String, List<FontEnvironment.Entry>> keyed : installed().entrySet()) {
            assertEquals(keyed.getKey(), keyed.getKey().toLowerCase(Locale.ROOT));
            for (FontEnvironment.Entry entry : keyed.getValue()) {
                assertEquals(keyed.getKey(), entry.family().toLowerCase(Locale.ROOT));
            }
        }
    }

    @Test
    void anInstalledFamilyIsFoundByItsOwnName() {
        FontEnvironment.Entry any = installed().values().iterator().next().get(0);

        assertEquals(
                Optional.of(any.family()),
                FontEnvironment.shared()
                        .find(any.family(), any.bold(), any.italic())
                        .map(FontEnvironment.Entry::family));
    }

    @Test
    void aFamilyNameIsMatchedWithoutRegardToCaseOrQuotes() {
        FontEnvironment.Entry any = installed().values().iterator().next().get(0);

        assertTrue(FontEnvironment.shared().find(any.family().toUpperCase(Locale.ROOT), false, false).isPresent());
        assertTrue(FontEnvironment.shared().find("'" + any.family() + "'", false, false).isPresent());
    }

    @Test
    void aFamilyThatIsNotInstalledIsNotFound() {
        assertEquals(Optional.empty(), FontEnvironment.shared().find("No Such Family At All", false, false));
    }

    @Test
    void aGenericFamilyResolvesThroughItsCandidateList() {
        installed();
        // At least one of serif, sans-serif and monospace should resolve on any
        // machine with fonts; which one depends on what is installed.
        assertTrue(
                FontEnvironment.shared().find("serif", false, false).isPresent()
                        || FontEnvironment.shared().find("sans-serif", false, false).isPresent()
                        || FontEnvironment.shared().find("monospace", false, false).isPresent(),
                "no generic family resolved despite fonts being installed");
    }

    @Test
    void theFirstInstalledFamilyOfAListWins() {
        FontEnvironment.Entry any = installed().values().iterator().next().get(0);

        assertEquals(
                FontEnvironment.shared().find(any.family(), false, false),
                FontEnvironment.shared().findAny(List.of("No Such Family", any.family()), false, false));
    }

    @Test
    void aListWithNothingInstalledResolvesToNothing() {
        assertEquals(
                Optional.empty(),
                FontEnvironment.shared().findAny(List.of("No Such Family", "Nor This One"), false, false));
    }

    @Test
    void anEmptyFamilyListResolvesToNothing() {
        assertEquals(Optional.empty(), FontEnvironment.shared().findAny(List.of(), false, false));
    }

    @Test
    void aFamilyWithNoItalicStillResolvesToItsRegularRatherThanADifferentFamily() {
        Optional<List<FontEnvironment.Entry>> upright = installed().values().stream()
                .filter(entries -> entries.stream().noneMatch(FontEnvironment.Entry::italic))
                .findFirst();
        Assumptions.assumeTrue(upright.isPresent(), "every installed family has an italic");
        String family = upright.get().get(0).family();

        assertEquals(
                Optional.of(family),
                FontEnvironment.shared().find(family, false, true).map(FontEnvironment.Entry::family));
    }

    @Test
    void aBoldRequestPrefersTheBoldFileWhenTheFamilyHasOne() {
        Optional<List<FontEnvironment.Entry>> withBold = installed().values().stream()
                .filter(entries -> entries.stream().anyMatch(FontEnvironment.Entry::bold)
                        && entries.stream().anyMatch(entry -> !entry.bold()))
                .findFirst();
        Assumptions.assumeTrue(withBold.isPresent(), "no installed family has both weights");
        String family = withBold.get().get(0).family();

        assertTrue(FontEnvironment.shared().find(family, true, false).orElseThrow().bold());
        assertTrue(!FontEnvironment.shared().find(family, false, false).orElseThrow().bold());
    }

    @Test
    void localMatchesAFullFontNameAndNotOnlyAFamily() {
        // `local()` in an @font-face names a face, not a family: "Arial Bold"
        // is a full name that no family index would ever have a key for.
        java.util.Map<String, java.util.List<FontEnvironment.Entry>> index = FontEnvironment.shared().index();
        org.junit.jupiter.api.Assumptions.assumeFalse(index.isEmpty(), "no system fonts installed");

        for (java.util.List<FontEnvironment.Entry> entries : index.values()) {
            for (FontEnvironment.Entry entry : entries) {
                for (String name : entry.names()) {
                    assertTrue(FontEnvironment.shared().local(name).isPresent(), name + " should resolve");
                }
            }
        }
    }

    @Test
    void localFallsBackToTheFamilyIndexForAPlainFamilyName() {
        java.util.Map<String, java.util.List<FontEnvironment.Entry>> index = FontEnvironment.shared().index();
        org.junit.jupiter.api.Assumptions.assumeFalse(index.isEmpty(), "no system fonts installed");
        String family = index.values().iterator().next().get(0).family();

        assertTrue(FontEnvironment.shared().local(family).isPresent());
    }

    @Test
    void localFindsNothingForANameNoFaceCarries() {
        assertTrue(FontEnvironment.shared().local("No Such Face Anywhere At All").isEmpty());
    }

    // --- Environments of one's own ------------------------------------------

    /** The four DejaVu faces the Fixtures carry, which no machine has to have. */
    private static final Path FIXTURE_FONTS = Path.of("src/test/resources/fixtures/features/fonts");

    @Test
    void anEnvironmentSeesOnlyWhatItsOwnSearchPathHolds() {
        FontEnvironment fixtures = FontEnvironment.of(List.of(FIXTURE_FONTS));

        assertEquals(
                java.util.Set.of("dejavu serif", "dejavu sans mono"),
                fixtures.index().keySet(),
                "only the families in the search path should be indexed");
        assertTrue(fixtures.find("DejaVu Serif", true, false).orElseThrow().bold());
        assertTrue(fixtures.find("Arial", false, false).isEmpty(), "nothing installed should leak in");
    }

    @Test
    void anEmptyEnvironmentFindsNothingAtAll() {
        FontEnvironment empty = FontEnvironment.empty();

        assertTrue(empty.index().isEmpty());
        assertTrue(empty.find("serif", false, false).isEmpty(), "not even a generic family");
        assertTrue(empty.local("DejaVu Serif").isEmpty());
    }

    @Test
    void oneEnvironmentDoesNotDisturbAnother() {
        // The point of owning the scan: a render against no fonts at all leaves
        // every other environment in the JVM exactly as it was.
        Map<String, List<FontEnvironment.Entry>> before = FontEnvironment.shared().index();

        assertTrue(FontEnvironment.empty().index().isEmpty());
        assertTrue(FontEnvironment.of(List.of(FIXTURE_FONTS)).index().size() == 2);

        assertTrue(FontEnvironment.shared().index() == before, "the shared scan should be untouched");
    }

    @Test
    void aFaceIsParsedOncePerEnvironment() {
        FontEnvironment fixtures = FontEnvironment.of(List.of(FIXTURE_FONTS));
        FontEnvironment.Entry serif = fixtures.find("DejaVu Serif", false, false).orElseThrow();

        assertTrue(fixtures.open(serif).orElseThrow() == fixtures.open(serif).orElseThrow(),
                "the parsed Face should be cached");
        assertTrue(fixtures.open(serif).orElseThrow() != FontEnvironment.of(List.of(FIXTURE_FONTS))
                        .open(serif).orElseThrow(),
                "and cached per environment, not per process");
    }

    @Test
    void aDirectoryThatIsNotThereIsSimplySkipped() {
        FontEnvironment missing = FontEnvironment.of(List.of(Path.of("no/such/font/directory"), FIXTURE_FONTS));

        assertTrue(missing.find("DejaVu Sans Mono", false, false).isPresent());
    }
}
