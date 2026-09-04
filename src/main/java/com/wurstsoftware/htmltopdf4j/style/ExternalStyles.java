package com.wurstsoftware.htmltopdf4j.style;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stylesheets a Document links to rather than carries.
 *
 * <p>Nothing is fetched. A sheet is read from the file system only, only from
 * under the base directory the caller named, and only when the caller named one
 * — the same rule an {@code @font-face} {@code src} follows. A Document that
 * links to a sheet this engine will not read is styled by whatever it does
 * carry, rather than failing: an unstyled paragraph is a better outcome than a
 * render that throws.
 */
final class ExternalStyles {

    private ExternalStyles() {}

    /**
     * How deep a chain of {@code @import}s is followed. CSS sets no limit, but a
     * Document that needs eight is a Document with a mistake in it.
     */
    private static final int MAX_IMPORT_DEPTH = 8;

    /**
     * An {@code @import}, in either of its forms: {@code @import url("x.css")}
     * and {@code @import "x.css"}, each optionally followed by a media query.
     */
    private static final Pattern IMPORT = Pattern.compile(
            "@import\\s+(?:url\\(\\s*)?['\"]?([^'\")\\s;]+)['\"]?\\s*\\)?([^;]*);",
            Pattern.CASE_INSENSITIVE);

    /** The CSS of the sheet at {@code href}, with its imports inlined; empty when it is refused. */
    static String load(String href, Path baseDirectory) {
        return read(href, baseDirectory, new HashSet<>(), 0);
    }

    /**
     * A {@code <style>} block with its {@code @import}s followed. A block is
     * read from the Document rather than from disk, so the imports in it resolve
     * against the base directory itself.
     */
    static String inline(String css, Path baseDirectory) {
        return baseDirectory == null ? css : inlineImports(css, baseDirectory, baseDirectory, new HashSet<>(), 0);
    }

    private static String read(String href, Path baseDirectory, Set<Path> seen, int depth) {
        Path file = resolve(href, baseDirectory);
        if (file == null || depth > MAX_IMPORT_DEPTH || !seen.add(file)) {
            return "";
        }
        String css;
        try {
            css = Files.readString(file);
        } catch (IOException | RuntimeException e) {
            // A sheet that cannot be read leaves the Document with the styles it
            // carries, which is what a browser does with a 404 as well.
            return "";
        }
        return inlineImports(css, file.getParent(), baseDirectory, seen, depth);
    }

    /**
     * Replaces each {@code @import} with the sheet it names, in place: CSS
     * requires imports to come before any other rule, so inlining them there
     * keeps the cascade order the Document asked for.
     */
    private static String inlineImports(
            String css, Path sheetDirectory, Path baseDirectory, Set<Path> seen, int depth) {

        Matcher matcher = IMPORT.matcher(css);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String media = matcher.group(2);
            String imported = media != null && !media.isBlank() && !Cascade.appliesToPrint(media)
                    ? ""
                    : read(sheetDirectory.resolve(matcher.group(1)).toString(),
                            baseDirectory, seen, depth + 1);
            matcher.appendReplacement(out, Matcher.quoteReplacement(imported));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * The file a target names, or {@code null} when this engine will not read it:
     * a remote target, a target with no base directory to resolve against, or one
     * that climbs out of that directory.
     */
    private static Path resolve(String href, Path baseDirectory) {
        if (baseDirectory == null || href == null || href.isBlank()) {
            return null;
        }
        String target = href.trim();
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) {
            return null;
        }
        Path root = baseDirectory.toAbsolutePath().normalize();
        Path file = root.resolve(target).toAbsolutePath().normalize();
        return file.startsWith(root) && Files.isReadable(file) ? file : null;
    }
}
