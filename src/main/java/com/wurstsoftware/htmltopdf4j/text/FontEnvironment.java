package com.wurstsoftware.htmltopdf4j.text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.fontbox.ttf.NameRecord;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;

/**
 * The Faces a render may draw with: a search path, the index scanned from it,
 * and the Faces already parsed out of it.
 *
 * <p>This replaces the reference's font database. There is no JDK API that gives
 * both a family name and the file it came from — {@code GraphicsEnvironment}
 * gives the names but not the bytes, which a Document needs in order to embed
 * and subset — so the font directories are scanned directly.
 *
 * <p>An environment scans its directories once, on first use, and caches every
 * Face it parses. A machine's installed fonts do not change while a render is
 * running, and re-scanning per render would dominate the render time. The scan
 * and the cache belong to the environment rather than to the process, so a
 * caller who wants a different search path — or none at all — makes its own
 * environment and hands it to {@code RenderOptions}. Callers who do not care
 * share {@link #shared()}, and pay for one scan between them.
 *
 * <p>An environment is safe to share between threads and between renders.
 */
public final class FontEnvironment {

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

    /**
     * The environment callers who ask for none get: the machine's own font
     * directories, scanned once for the life of the process.
     */
    private static final FontEnvironment SHARED = new FontEnvironment(null);

    /** The Faces parsed from this environment's files, keyed by absolute path. */
    private final Map<Path, EmbeddedFace> parsed = new ConcurrentHashMap<>();

    /** The directories to scan, or {@code null} for the machine's own. */
    private final List<Path> searchPath;

    private volatile Map<String, List<Entry>> index;
    private volatile Map<String, Entry> byName;

    private FontEnvironment(List<Path> searchPath) {
        this.searchPath = searchPath;
    }

    /** The environment a render uses when the caller names none. */
    public static FontEnvironment shared() {
        return SHARED;
    }

    /**
     * An environment that looks only in the given directories, each scanned to a
     * depth of six. Nothing installed on the machine is visible to it.
     */
    public static FontEnvironment of(List<Path> searchPath) {
        return new FontEnvironment(List.copyOf(Objects.requireNonNull(searchPath, "searchPath")));
    }

    /** An environment with no fonts at all: every family falls back to the default Face. */
    public static FontEnvironment empty() {
        return of(List.of());
    }

    /** Every font this environment can see, keyed by lower-case family name. */
    public Map<String, List<Entry>> index() {
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
    public Optional<Entry> local(String name) {
        ensureScanned();
        Entry named = byName.get(key(name));
        return named != null ? Optional.of(named) : find(name, false, false);
    }

    /**
     * The best font in this environment for a CSS family, or empty when nothing
     * matches. Generic families resolve through their candidate lists.
     */
    public Optional<Entry> find(String family, boolean bold, boolean italic) {
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

    /** The first of {@code families} this environment can see. */
    public Optional<Entry> findAny(List<String> families, boolean bold, boolean italic) {
        for (String family : families) {
            Optional<Entry> match = find(family, bold, italic);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /**
     * The Face an entry's file holds, parsed once per environment. A file the
     * shaper cannot read is simply not offered, the same as one that is absent.
     */
    public Optional<Face> open(Entry entry) {
        EmbeddedFace cached = parsed.get(entry.path());
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            EmbeddedFace face = EmbeddedFace.fromBytes(Files.readAllBytes(entry.path()), entry.family());
            parsed.put(entry.path(), face);
            return Optional.of(face);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Entry> best(List<Entry> entries, boolean bold, boolean italic) {
        return entries == null
                ? Optional.empty()
                : entries.stream().min((a, b) ->
                        Integer.compare(a.distanceTo(bold, italic), b.distanceTo(bold, italic)));
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("^['\"]|['\"]$", "").trim();
    }

    // --- Scanning -----------------------------------------------------------

    private void ensureScanned() {
        if (index == null) {
            synchronized (this) {
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

    private List<Path> directories() {
        if (searchPath != null) {
            return searchPath.stream().filter(Files::isDirectory).toList();
        }
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
                .filter(Objects::nonNull)
                .map(Path::of)
                .filter(Files::isDirectory)
                .toList();
    }

    private Map<String, List<Entry>> scan() {
        Map<String, List<Entry>> found = new HashMap<>();
        for (Path directory : directories()) {
            try (Stream<Path> files = Files.walk(directory, 6)) {
                files.filter(FontEnvironment::isFontFile).forEach(file -> add(found, file));
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
