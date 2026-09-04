package com.wurstsoftware.htmltopdf4j.pdf;

import com.wurstsoftware.htmltopdf4j.PdfWriteException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * The byte-level half of the writer: indirect objects, streams, the cross
 * reference table and the trailer. It knows nothing about what it is writing.
 */
final class PdfObjectWriter {

    private static final byte[] HEADER = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 * 1024);

    /** Byte offset of each object by id; index 0 is the free head and stays unused. */
    private final List<Integer> offsets = new ArrayList<>(List.of(0));

    PdfObjectWriter() {
        bytes.writeBytes(HEADER);
    }

    /** Writes an indirect object whose body is a dictionary or other direct object. */
    void object(int id, String body) {
        startObject(id);
        write(body);
        write("\nendobj\n");
    }

    /** Writes a Flate-compressed stream object. */
    void streamObject(int id, byte[] stream) {
        byte[] compressed = deflate(stream);
        startObject(id);
        write("<< /Length " + compressed.length + " /Filter /FlateDecode >>\nstream\n");
        bytes.writeBytes(compressed);
        write("endstream\nendobj\n");
    }

    /**
     * Writes a stream object with a caller-supplied dictionary. {@code body} is
     * written verbatim, so the caller decides the filtering; {@code dict} must
     * not contain {@code /Length}, which is appended here.
     */
    void streamWithDictionary(int id, String dict, byte[] body) {
        startObject(id);
        write("<< " + dict + " /Length " + body.length + " >>\nstream\n");
        bytes.writeBytes(body);
        write("\nendstream\nendobj\n");
    }

    /**
     * Writes a font program stream. PDF requires {@code /Length1} — the
     * uncompressed length — on an embedded TrueType {@code FontFile2}.
     */
    void fontFileObject(int id, byte[] program) {
        byte[] compressed = deflate(program);
        startObject(id);
        write("<< /Length " + compressed.length + " /Length1 " + program.length
                + " /Filter /FlateDecode >>\nstream\n");
        bytes.writeBytes(compressed);
        write("endstream\nendobj\n");
    }

    /** Writes the cross reference table and trailer, and returns the finished document. */
    byte[] finish(int rootId, int objectCount) {
        int xrefOffset = bytes.size();
        write("xref\n0 " + (objectCount + 1) + "\n");
        write("0000000000 65535 f \n");
        for (int id = 1; id <= objectCount; id++) {
            int offset = id < offsets.size() ? offsets.get(id) : 0;
            write(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset));
        }
        write("trailer\n<< /Size " + (objectCount + 1) + " /Root " + rootId + " 0 R >>\n"
                + "startxref\n" + xrefOffset + "\n%%EOF\n");
        return bytes.toByteArray();
    }

    private void startObject(int id) {
        while (offsets.size() <= id) {
            offsets.add(0);
        }
        offsets.set(id, bytes.size());
        write(id + " 0 obj\n");
    }

    private void write(String text) {
        // Everything written through here is PDF syntax the writer built itself:
        // names, numbers, and strings already escaped or hex-encoded upstream.
        bytes.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 3));
        try (DeflaterOutputStream stream = new DeflaterOutputStream(out, deflater)) {
            stream.write(data);
        } catch (IOException e) {
            throw new PdfWriteException("failed to compress a PDF stream", e);
        } finally {
            deflater.end();
        }
        return out.toByteArray();
    }
}
