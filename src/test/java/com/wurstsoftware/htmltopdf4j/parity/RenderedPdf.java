package com.wurstsoftware.htmltopdf4j.parity;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A rendered PDF, in the two forms the Expectations are stated against: the raw
 * bytes, and every FlateDecode stream inflated and concatenated.
 *
 * <p>The engine compresses its content streams, so text and operator assertions
 * would see nothing but deflate output without this.
 */
public record RenderedPdf(byte[] bytes, String content, String raw, int pageCount) {

    public static RenderedPdf of(byte[] pdf) {
        String raw = new String(pdf, StandardCharsets.ISO_8859_1);
        return new RenderedPdf(pdf, inflateStreams(pdf, raw), raw, countPages(raw));
    }

    public boolean isWellFormed() {
        return raw.startsWith("%PDF") && raw.contains("%%EOF");
    }

    public int sizeBytes() {
        return bytes.length;
    }

    /**
     * Concatenates every inflated stream. A stream that does not inflate is
     * skipped rather than failing: an image XObject holding JPEG bytes is not
     * deflate data and has nothing to contribute to a text assertion.
     */
    private static String inflateStreams(byte[] pdf, String raw) {
        StringBuilder out = new StringBuilder();
        int search = 0;
        while (true) {
            int marker = raw.indexOf("stream", search);
            if (marker < 0) {
                break;
            }
            int start = marker + "stream".length();
            // Skip the end-of-line that must follow the keyword.
            if (start < pdf.length && pdf[start] == '\r') {
                start++;
            }
            if (start < pdf.length && pdf[start] == '\n') {
                start++;
            }
            int end = raw.indexOf("endstream", start);
            if (end < 0) {
                break;
            }
            inflate(pdf, start, end - start).ifPresent(out::append);
            search = end + "endstream".length();
        }
        return out.toString();
    }

    private static java.util.Optional<String> inflate(byte[] pdf, int offset, int length) {
        if (length <= 0) {
            return java.util.Optional.empty();
        }
        Inflater inflater = new Inflater();
        inflater.setInput(pdf, offset, length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(length * 4);
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int produced = inflater.inflate(buffer);
                if (produced == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                }
                out.write(buffer, 0, produced);
            }
        } catch (DataFormatException e) {
            return java.util.Optional.empty();
        } finally {
            inflater.end();
        }
        return out.size() == 0
                ? java.util.Optional.empty()
                : java.util.Optional.of(out.toString(StandardCharsets.ISO_8859_1));
    }

    /**
     * Counts page objects. {@code /Type /Page} also prefixes {@code /Type /Pages},
     * the tree node, which must not be counted as a sheet.
     */
    private static int countPages(String raw) {
        int count = 0;
        int search = 0;
        while (true) {
            int found = raw.indexOf("/Type /Page", search);
            if (found < 0) {
                break;
            }
            int after = found + "/Type /Page".length();
            if (after >= raw.length() || raw.charAt(after) != 's') {
                count++;
            }
            search = found + 1;
        }
        return Math.max(count, 1);
    }
}
