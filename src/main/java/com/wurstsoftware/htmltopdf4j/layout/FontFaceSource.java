package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.text.FontEnvironment;
import com.wurstsoftware.htmltopdf4j.text.Woff;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

/**
 * Reading the font program an {@code @font-face} rule's {@code src} names.
 *
 * <p>A {@code src} is a comma-separated list of alternatives in order of
 * preference — a {@code local()} family, a {@code url()} of a file, a
 * {@code data:} URI — and the first one that can be read wins, which is exactly
 * what the list is for. An alternative in a container this engine cannot open,
 * such as WOFF2, counts as unreadable, so the chain moves on rather than
 * stopping at a Face nothing downstream can parse.
 */
final class FontFaceSource {

    private FontFaceSource() {}

    /** The containers this engine can open, as an {@code src} format hint names them. */
    private static final java.util.Set<String> SUPPORTED_FORMATS = java.util.Set.of(
            "truetype", "opentype", "woff", "truetype-variations", "opentype-variations");

    /**
     * One alternative of a {@code src} list: the function that names the program,
     * and the {@code format()} hint that follows it, if any.
     */
    private record Alternative(String function, String format) {

        /**
         * Whether this alternative is worth reading. A hint naming a container
         * this engine cannot open — {@code woff2}, {@code svg}, {@code eot} —
         * means the chain moves on without fetching the bytes at all.
         */
        boolean isReadable() {
            return format == null || SUPPORTED_FORMATS.contains(format);
        }
    }

    /** The font program, or {@code null} when none of the alternatives can be read. */
    static byte[] read(String source, Path baseDirectory, FontEnvironment fonts) {
        for (Alternative alternative : split(source)) {
            if (!alternative.isReadable()) {
                continue;
            }
            // A hint is only a hint, so an alternative that survives it still has
            // its container decided by the bytes: WOFF1 is unwrapped, a bare SFNT
            // passes through, and anything else counts as unreadable.
            byte[] program = Woff.decode(readOne(alternative.function(), baseDirectory, fonts));
            if (program != null) {
                return program;
            }
        }
        return null;
    }

    private static byte[] readOne(String alternative, Path baseDirectory, FontEnvironment fonts) {
        String lower = alternative.toLowerCase(Locale.ROOT);
        if (lower.startsWith("local(")) {
            // `local()` names a face, not a family: `local("Arial Bold")` is a
            // full name, which the family index has no key for.
            return fonts.local(unquote(argumentOf(alternative)))
                    .map(entry -> readFile(entry.path()))
                    .orElse(null);
        }
        if (!lower.startsWith("url(")) {
            return null;
        }
        String target = unquote(argumentOf(alternative));
        if (target.toLowerCase(Locale.ROOT).startsWith("data:")) {
            return readDataUri(target);
        }
        if (target.toLowerCase(Locale.ROOT).startsWith("http://")
                || target.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return null;
        }
        Path path = baseDirectory != null ? baseDirectory.resolve(target) : Path.of(target);
        return readFile(path);
    }

    private static byte[] readFile(Path path) {
        try {
            return Files.isReadable(path) ? Files.readAllBytes(path) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readDataUri(String uri) {
        int comma = uri.indexOf(',');
        if (comma < 0) {
            return null;
        }
        String payload = uri.substring(comma + 1);
        if (!uri.substring(0, comma).toLowerCase(Locale.ROOT).contains(";base64")) {
            return payload.getBytes(StandardCharsets.ISO_8859_1);
        }
        try {
            return Base64.getMimeDecoder().decode(payload.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String argumentOf(String function) {
        int open = function.indexOf('(');
        int close = function.lastIndexOf(')');
        return open < 0 || close <= open ? "" : function.substring(open + 1, close).trim();
    }

    private static String unquote(String value) {
        return value.replaceAll("^['\"]|['\"]$", "").trim();
    }

    /** Splits the alternatives, ignoring the commas inside a {@code data:} URI or a format list. */
    private static java.util.List<Alternative> split(String source) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(source.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(source.substring(start));
        // `url(x) format("woff")` names one alternative: the function before the
        // hint says where the program is, the hint says whether to bother.
        return parts.stream().map(FontFaceSource::alternativeOf).toList();
    }

    private static Alternative alternativeOf(String part) {
        String[] halves = part.trim().split("\\s+format", 2);
        if (halves.length < 2) {
            return new Alternative(halves[0].trim(), null);
        }
        String hint = unquote(argumentOf("format" + halves[1])).toLowerCase(Locale.ROOT);
        return new Alternative(halves[0].trim(), hint.isEmpty() ? null : hint);
    }
}
