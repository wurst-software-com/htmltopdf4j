package com.wurstsoftware.htmltopdf4j.layout;

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
 * what the list is for.
 */
final class FontFaceSource {

    private FontFaceSource() {}

    /** The font program, or {@code null} when none of the alternatives can be read. */
    static byte[] read(String source, Path baseDirectory) {
        for (String alternative : split(source)) {
            byte[] program = readOne(alternative.trim(), baseDirectory);
            if (program != null) {
                return program;
            }
        }
        return null;
    }

    private static byte[] readOne(String alternative, Path baseDirectory) {
        String lower = alternative.toLowerCase(Locale.ROOT);
        if (lower.startsWith("local(")) {
            String family = unquote(argumentOf(alternative));
            return com.wurstsoftware.htmltopdf4j.text.FontLibrary.find(family, false, false)
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
    private static java.util.List<String> split(String source) {
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
        // `url(x) format("woff")` names one alternative; the format hint is not a
        // source and only the function before it is read.
        return parts.stream().map(part -> part.trim().split("\\s+format", 2)[0]).toList();
    }
}
