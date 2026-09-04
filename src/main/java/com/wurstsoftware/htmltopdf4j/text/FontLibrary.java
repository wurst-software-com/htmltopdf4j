package com.wurstsoftware.htmltopdf4j.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.fontbox.ttf.NameRecord;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;

/**
 * The Faces installed on this machine, indexed by family, weight and slope.
 *
 * <p>This replaces the reference's font database. There is no JDK API that gives
 * both a family name and the file it came from — {@code GraphicsEnvironment}
 * gives the names but not the bytes, which a Document needs in order to embed
 * and subset — so the font directories are scanned directly.
 *
 * <p>The scan reads only each font's {@code name} table, and happens once for
 * the life of the process. A machine's installed fonts do not change while a
 * render is running, and re-scanning per render would dominate the render time.
 */
public final class FontLibrary {

    /** The families a generic CSS family resolves to, in order of preference. */
    private static final Map<String, List<String>> GENERIC_FAMILIES = Map.of(
            "serif", List.of("dejavu serif", "liberation serif", "times new roman", "noto serif", "freeserif"),
            "sans-serif", List.of("dejavu sans", "liberation sans", "arial", "helvetica", "noto sans", "freesans"),
            "monospace", List.of("dejavu sans mono", "liberation mono", "courier new", "noto sans mono", "freemono"),
            "cursive", List.of("comic sans ms", "dejavu sans"),
            "fantasy", List.of("impact", "dejavu sans"),
            "system-ui", List.of("dejavu sans", "liberation sans", "arial"),
            "ui-sans-serif", List.of("dejavu sans", "liberation sans", "arial"),
            "ui-serif", List.of("dejavu serif", "liberation serif"),
            "ui-monospace", List.of("dejavu sans mono", "liberation mono"),
            "emoji", List.of("noto color emoji", "noto emoji", "symbola"));

    /**
     * One installed font file, and the family and style its name table declares.
     *
     * @param names the face's own names — its full name and its PostScript name
     *     — which are what an {@code @font-face} {@code local()} refers to
     */
    public record Entry(Path path, String family, boolean bold, boolean italic, List<String> names) {

        public Entry {
            names = List.copyOf(names);
        }

        /**
         * How badly this entry misses a requested style. Lower is better, so a
         * family that has no italic still resolves to its regular rather than to
         * a different family.
         */
        int distanceTo(boolean wantBold, boolean wantItalic) {
            return (bold == wantBold ? 0 : 2) + (italic == wantItalic ? 0 : 1);
        }
    }

    private static volatile Map<String, List<Entry>> index;
    private static volatile Map<String, Entry> byName;

    private FontLibrary() {}

    /** Every installed font, keyed by lower-case family name. */
    public static Map<String, List<Entry>> index() {
        ensureScanned();
        return index;
    }

    /**
     * The Face an {@code @font-face} {@code local()} names.
     *
     * <p>{@code local()} names a face, not a family — {@code local("Arial Bold")}
     * is a full name that a family index has no key for — so the face's own
     * names are tried first and the family index is the fallback.
     */
    public static Optional<Entry> local(String name) {
        ensureScanned();
        String key = key(name);
        Entry named = byName.get(key);
        return named != null ? Optional.of(named) : find(name, false, false);
    }

    private static void ensureScanned() {
        if (index == null) {
            synchronized (FontLibrary.class) {
                if (index == null) {
                    Map<String, List<Entry>> scanned = scan();
                    byName = nameIndex(scanned);
                    index = scanned;
                }
            }
        }
    }

    private static Map<String, Entry> nameIndex(Map<String, List<Entry>> families) {
        Map<String, Entry> named = new HashMap<>();
        for (List<Entry> entries : families.values()) {
            for (Entry entry : entries) {
                for (String name : entry.names()) {
                    named.putIfAbsent(key(name), entry);
                }
            }
        }
        return Map.copyOf(named);
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("^['\"]|['\"]$", "").trim();
    }

    /**
     * The best installed font for a CSS family, or empty when nothing matches.
     * Generic families resolve through their candidate lists.
     */
    public static Optional<Entry> find(String family, boolean bold, boolean italic) {
        String key = key(family);
        List<String> candidates = GENERIC_FAMILIES.getOrDefault(key, List.of(key));
        for (String candidate : candidates) {
            Optional<Entry> match = best(index().get(candidate), bold, italic);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /** The first of {@code families} that is installed. */
    public static Optional<Entry> findAny(List<String> families, boolean bold, boolean italic) {
        for (String family : families) {
            Optional<Entry> match = find(family, bold, italic);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static Optional<Entry> best(List<Entry> entries, boolean bold, boolean italic) {
        return entries == null
                ? Optional.empty()
                : entries.stream().min((a, b) ->
                        Integer.compare(a.distanceTo(bold, italic), b.distanceTo(bold, italic)));
    }

    // --- Scanning -----------------------------------------------------------

    private static List<Path> fontDirectories() {
        String home = System.getProperty("user.home", "");
        return Stream.of(
                        "/usr/share/fonts",
                        "/usr/local/share/fonts",
                        "/usr/share/X11/fonts",
                        home + "/.fonts",
                        home + "/.local/share/fonts",
                        "/System/Library/Fonts",
                        "/Library/Fonts",
                        home + "/Library/Fonts",
                        System.getenv("WINDIR") == null ? null : System.getenv("WINDIR") + "\\Fonts")
                .filter(java.util.Objects::nonNull)
                .map(Path::of)
                .filter(Files::isDirectory)
                .toList();
    }

    private static Map<String, List<Entry>> scan() {
        Map<String, List<Entry>> found = new HashMap<>();
        for (Path directory : fontDirectories()) {
            try (Stream<Path> files = Files.walk(directory, 6)) {
                files.filter(FontLibrary::isFontFile).forEach(file -> add(found, file));
            } catch (IOException | RuntimeException e) {
                // An unreadable font directory means fewer Faces, not a failed
                // render: the default Face always remains available.
            }
        }
        return Map.copyOf(found);
    }

    private static boolean isFontFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".ttf") || name.endsWith(".otf")) && Files.isRegularFile(path);
    }

    /**
     * The face's own names, which are what a {@code local()} can refer to: the
     * full names and PostScript names its {@code name} table declares, in
     * whatever languages it declares them.
     *
     * <p>A family plus its subfamily is added as well, because that is the name
     * a stylesheet author writes — {@code local("Arial Bold")} — even for a face
     * whose declared full name reads differently.
     */
    private static List<String> namesOf(TrueTypeFont font) throws IOException {
        List<String> names = new ArrayList<>();
        for (NameRecord record : font.getNaming().getNameRecords()) {
            if (record.getNameId() == NameRecord.NAME_FULL_FONT_NAME
                    || record.getNameId() == NameRecord.NAME_POSTSCRIPT_NAME) {
                add(names, record.getString());
            }
        }
        String family = font.getNaming().getFontFamily();
        String subFamily = font.getNaming().getFontSubFamily();
        if (family != null && subFamily != null) {
            add(names, family.trim() + " " + subFamily.trim());
        }
        return names;
    }

    private static void add(List<String> names, String name) {
        if (name == null) {
            return;
        }
        String trimmed = name.trim();
        if (!trimmed.isBlank() && !names.contains(trimmed)) {
            names.add(trimmed);
        }
    }

    private static void add(Map<String, List<Entry>> found, Path path) {
        try (TrueTypeFont font = new TTFParser(true).parse(
                new org.apache.pdfbox.io.RandomAccessReadBufferedFile(path.toFile()))) {
            String family = font.getNaming().getFontFamily();
            String subFamily = font.getNaming().getFontSubFamily();
            if (family == null || family.isBlank()) {
                return;
            }
            String style = subFamily == null ? "" : subFamily.toLowerCase(Locale.ROOT);
            Entry entry = new Entry(
                    path,
                    family.trim(),
                    style.contains("bold") || font.getHeader().getMacStyle() % 2 == 1,
                    style.contains("italic") || style.contains("oblique"),
                    namesOf(font));
            found.computeIfAbsent(family.trim().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(entry);
        } catch (IOException | RuntimeException e) {
            // A font this parser cannot read is simply not offered.
        }
    }
}
