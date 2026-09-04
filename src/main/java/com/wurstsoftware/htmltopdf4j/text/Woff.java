package com.wurstsoftware.htmltopdf4j.text;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Unwrapping a WOFF1 container back into the bare SFNT the rest of the stack reads.
 *
 * <p>WOFF1 is not a font format so much as a wrapper: the same tables an SFNT
 * holds, each optionally zlib-compressed, behind a directory that also records
 * the original length. Nothing else in this engine should have to know that a
 * Face arrived wrapped, so every font program passes through here on its way in
 * and comes out as an SFNT.
 *
 * <p>WOFF2 is a different format — it reorders and re-encodes {@code glyf}, and
 * compresses with Brotli, which the JDK does not carry — so it is refused rather
 * than half-read.
 */
public final class Woff {

    private static final int WOFF1 = 0x774F4646; // 'wOFF'
    private static final int WOFF2 = 0x774F4632; // 'wOF2'
    private static final int HEADER_LENGTH = 44;
    private static final int ENTRY_LENGTH = 20;

    private Woff() {}

    /**
     * The SFNT inside a font program: the program itself when it is already one,
     * or {@code null} when it is a container this engine cannot open.
     */
    public static byte[] decode(byte[] program) {
        if (program == null || program.length < 4) {
            return null;
        }
        int signature = ByteBuffer.wrap(program).getInt(0);
        if (signature == WOFF2) {
            return null;
        }
        if (signature != WOFF1) {
            return program;
        }
        try {
            return unwrap(program);
        } catch (RuntimeException e) {
            // A malformed container is a Face this engine does not have, which
            // the src chain handles; it is not a failed render.
            return null;
        }
    }

    /** One table as the container's directory describes it. */
    private record Entry(int tag, int offset, int compressedLength, int length) {}

    private static byte[] unwrap(byte[] woff) {
        ByteBuffer buffer = ByteBuffer.wrap(woff);
        int flavour = buffer.getInt(4);
        int count = buffer.getShort(12) & 0xFFFF;
        if (count == 0 || woff.length < HEADER_LENGTH + count * ENTRY_LENGTH) {
            return null;
        }

        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int at = HEADER_LENGTH + i * ENTRY_LENGTH;
            entries.add(new Entry(
                    buffer.getInt(at), buffer.getInt(at + 4), buffer.getInt(at + 8), buffer.getInt(at + 12)));
        }
        // The SFNT directory has to ascend by tag; a container's need not.
        entries.sort(Comparator.comparingLong(entry -> entry.tag() & 0xFFFFFFFFL));

        List<byte[]> tables = new ArrayList<>(count);
        for (Entry entry : entries) {
            byte[] table = tableOf(woff, entry);
            if (table == null) {
                return null;
            }
            tables.add(table);
        }
        return assemble(flavour, entries, tables);
    }

    private static byte[] tableOf(byte[] woff, Entry entry) {
        if (entry.offset() < 0
                || entry.compressedLength() < 0
                || entry.length() < 0
                || entry.offset() + entry.compressedLength() > woff.length) {
            return null;
        }
        if (entry.compressedLength() >= entry.length()) {
            // Stored verbatim: the format says so by the lengths, not by a flag.
            return java.util.Arrays.copyOfRange(
                    woff, entry.offset(), entry.offset() + entry.length());
        }
        return inflate(woff, entry.offset(), entry.compressedLength(), entry.length());
    }

    private static byte[] inflate(byte[] woff, int offset, int compressedLength, int length) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(woff, offset, compressedLength);
            byte[] table = new byte[length];
            int written = 0;
            while (written < length && !inflater.finished()) {
                int step = inflater.inflate(table, written, length - written);
                if (step == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    return null;
                }
                written += step;
            }
            return written == length ? table : null;
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static byte[] assemble(int flavour, List<Entry> entries, List<byte[]> tables) {
        int count = entries.size();
        int directory = 12 + count * 16;
        int total = directory;
        for (byte[] table : tables) {
            total += align(table.length);
        }

        ByteBuffer out = ByteBuffer.allocate(total);
        out.putInt(flavour);
        out.putShort((short) count);
        // The binary-search hints are derived, not carried: the largest power of
        // two that is at most the table count, times the 16-byte entry.
        int power = Integer.highestOneBit(count);
        out.putShort((short) (power * 16));
        out.putShort((short) Integer.numberOfTrailingZeros(power));
        out.putShort((short) ((count - power) * 16));

        int offset = directory;
        for (int i = 0; i < count; i++) {
            byte[] table = tables.get(i);
            out.position(12 + i * 16);
            out.putInt(entries.get(i).tag());
            out.putInt(checksum(table));
            out.putInt(offset);
            out.putInt(table.length);
            out.position(offset);
            out.put(table);
            offset += align(table.length);
        }
        return out.array();
    }

    private static int align(int length) {
        return (length + 3) & ~3;
    }

    /**
     * The table checksum, which is the sum of its big-endian words with the tail
     * zero-padded. The container carries one, but recomputing costs nothing and
     * keeps a mis-stated one from reaching a validator.
     */
    private static int checksum(byte[] table) {
        int sum = 0;
        for (int i = 0; i < table.length; i += 4) {
            int word = 0;
            for (int b = 0; b < 4; b++) {
                word = (word << 8) | (i + b < table.length ? table[i + b] & 0xFF : 0);
            }
            sum += word;
        }
        return sum;
    }
}
