package com.wurstsoftware.htmltopdf4j.text;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

/**
 * A minimal WOFF1 encoder, so the tests need no checked-in binary.
 *
 * <p>The engine only ever reads WOFF, so the only way to test that reading is to
 * write one. Wrapping a system font keeps the payload real: a hand-built stub
 * would not survive the parser the decoded bytes are handed to.
 */
public final class WoffFixture {

    private WoffFixture() {}

    /** One table as an SFNT directory names it. */
    public record Table(String tag, byte[] data) {}

    /** A WOFF1 container holding this SFNT, with every table deflated where that helps. */
    public static byte[] wrap(byte[] sfnt) {
        return wrap(sfnt, true);
    }

    /** As {@link #wrap(byte[])}, but {@code compress} false stores every table verbatim. */
    public static byte[] wrap(byte[] sfnt, boolean compress) {
        List<Table> tables = tablesOf(sfnt);
        int directory = 44 + tables.size() * 20;
        List<byte[]> payloads = new ArrayList<>();
        for (Table table : tables) {
            byte[] deflated = compress ? deflate(table.data()) : table.data();
            payloads.add(deflated.length < table.data().length ? deflated : table.data());
        }

        ByteBuffer out = ByteBuffer.allocate(directory + payloads.stream()
                .mapToInt(payload -> align(payload.length))
                .sum());
        out.put("wOFF".getBytes(StandardCharsets.US_ASCII));
        out.putInt(ByteBuffer.wrap(sfnt).getInt(0));
        out.putInt(out.capacity());
        out.putShort((short) tables.size());
        out.putShort((short) 0);
        out.putInt(sfnt.length);
        out.putInt(0);
        out.putShort((short) 0);
        out.putShort((short) 0);
        out.putInt(0);
        out.putInt(0);
        out.putInt(0);

        int offset = directory;
        for (int i = 0; i < tables.size(); i++) {
            out.position(44 + i * 20);
            out.put(tables.get(i).tag().getBytes(StandardCharsets.US_ASCII));
            out.putInt(offset);
            out.putInt(payloads.get(i).length);
            out.putInt(tables.get(i).data().length);
            out.putInt(0);
            out.position(offset);
            out.put(payloads.get(i));
            offset += align(payloads.get(i).length);
        }
        return out.array();
    }

    private static int align(int length) {
        return (length + 3) & ~3;
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return out.toByteArray();
    }

    /** The tables an SFNT's directory names, in directory order. */
    public static List<Table> tablesOf(byte[] sfnt) {
        ByteBuffer buffer = ByteBuffer.wrap(sfnt);
        int count = buffer.getShort(4) & 0xFFFF;
        List<Table> tables = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int entry = 12 + i * 16;
            byte[] tag = new byte[4];
            buffer.position(entry);
            buffer.get(tag);
            int offset = buffer.getInt(entry + 8);
            int length = buffer.getInt(entry + 12);
            tables.add(new Table(
                    new String(tag, StandardCharsets.US_ASCII),
                    java.util.Arrays.copyOfRange(sfnt, offset, offset + length)));
        }
        return tables;
    }
}
