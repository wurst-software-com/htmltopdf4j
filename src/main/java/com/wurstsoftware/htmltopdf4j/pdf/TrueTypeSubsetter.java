package com.wurstsoftware.htmltopdf4j.pdf;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A minimal retain-GID TrueType subsetter.
 *
 * <p>Given a font program and the glyph ids a Document actually uses, it rebuilds
 * {@code glyf} and {@code loca} so only those glyphs — plus {@code .notdef} and,
 * transitively, the components of any composite glyph — carry outline data.
 * Every other table is copied verbatim.
 *
 * <p>Glyph ids are deliberately <em>not</em> renumbered. That is the point: the
 * cmap, the PDF {@code /W} widths, the {@code /ToUnicode} CMap and
 * {@code /CIDToGIDMap /Identity} all stay valid against the subset without being
 * rewritten, which is why the writer is hand-ported instead of delegating to
 * FontBox's own subsetter (ADR 0001).
 *
 * <p>Only {@code glyf}-based outlines are handled. A CFF/OpenType-CFF program has
 * no {@code glyf} table and yields {@code null}, meaning "embed the whole thing".
 */
public final class TrueTypeSubsetter {

    private static final int TAG_HEAD = tag("head");
    private static final int TAG_LOCA = tag("loca");
    private static final int TAG_GLYF = tag("glyf");
    private static final int TAG_MAXP = tag("maxp");
    private static final int TAG_DSIG = tag("DSIG");

    private static final int SFNT_HEADER_SIZE = 12;
    private static final int TABLE_RECORD_SIZE = 16;
    private static final int HEAD_MIN_SIZE = 54;

    /** Where {@code head} keeps {@code indexToLocFormat}: 0 = short offsets, 1 = long. */
    private static final int HEAD_INDEX_TO_LOC_FORMAT = 50;

    private static final int HEAD_CHECKSUM_ADJUSTMENT = 8;

    /** The constant a font's total checksum is subtracted from, per the sfnt spec. */
    private static final int CHECKSUM_MAGIC = 0xB1B0AFBA;

    private TrueTypeSubsetter() {}

    /**
     * Subsets {@code fontData} to {@code usedGlyphIds}.
     *
     * @return a standalone single-font sfnt, or {@code null} if the program
     *     cannot be subset, in which case the caller embeds it whole
     */
    public static byte[] subset(byte[] fontData, SortedSet<Integer> usedGlyphIds) {
        try {
            return trySubset(fontData, usedGlyphIds);
        } catch (RuntimeException e) {
            // A malformed or merely unusual program is not a render failure:
            // embedding the full font is always correct, just larger.
            return null;
        }
    }

    private static byte[] trySubset(byte[] font, SortedSet<Integer> usedGlyphIds) {
        ByteBuffer data = ByteBuffer.wrap(font).order(ByteOrder.BIG_ENDIAN);
        int sfntStart = sfntStart(data);
        List<TableRecord> records = readTableDirectory(data, sfntStart);
        if (records.isEmpty()) {
            return null;
        }

        TableRecord head = find(records, TAG_HEAD);
        TableRecord loca = find(records, TAG_LOCA);
        TableRecord glyf = find(records, TAG_GLYF);
        TableRecord maxp = find(records, TAG_MAXP);
        if (head == null || loca == null || glyf == null || maxp == null || head.length < HEAD_MIN_SIZE) {
            return null;
        }

        int glyphCount = Short.toUnsignedInt(data.getShort(maxp.offset + 4));
        if (glyphCount == 0) {
            return null;
        }

        boolean longOffsets = data.getShort(head.offset + HEAD_INDEX_TO_LOC_FORMAT) != 0;
        int[] offsets = readLoca(data, loca, glyphCount, longOffsets);
        if (offsets == null) {
            return null;
        }

        byte[] glyfData = slice(font, glyf);
        TreeSet<Integer> keep = closure(glyfData, offsets, glyphCount, usedGlyphIds);

        Rebuilt rebuilt = rebuildGlyfAndLoca(glyfData, offsets, glyphCount, keep);

        byte[] newHead = slice(font, head);
        // Long loca offsets, and a zeroed checksum adjustment that `assemble`
        // recomputes once the whole file exists.
        newHead[HEAD_INDEX_TO_LOC_FORMAT] = 0;
        newHead[HEAD_INDEX_TO_LOC_FORMAT + 1] = 1;
        writeInt(newHead, HEAD_CHECKSUM_ADJUSTMENT, 0);

        TreeMap<Integer, byte[]> tables = new TreeMap<>();
        for (TableRecord record : records) {
            if (record.tag == TAG_DSIG) {
                continue; // the signature no longer describes this program
            }
            byte[] bytes;
            if (record.tag == TAG_GLYF) {
                bytes = rebuilt.glyf();
            } else if (record.tag == TAG_LOCA) {
                bytes = rebuilt.loca();
            } else if (record.tag == TAG_HEAD) {
                bytes = newHead;
            } else {
                bytes = slice(font, record);
            }
            tables.put(record.tag, bytes);
        }
        return tables.isEmpty() ? null : assemble(tables);
    }

    /**
     * The set of glyphs whose outlines must survive: the used ids, {@code .notdef},
     * and the components every composite among them refers to, transitively.
     */
    private static TreeSet<Integer> closure(
            byte[] glyf, int[] offsets, int glyphCount, SortedSet<Integer> usedGlyphIds) {
        TreeSet<Integer> keep = new TreeSet<>();
        keep.add(0);
        for (int gid : usedGlyphIds) {
            if (gid >= 0 && gid < glyphCount) {
                keep.add(gid);
            }
        }
        Deque<Integer> pending = new ArrayDeque<>(keep);
        while (!pending.isEmpty()) {
            int gid = pending.pop();
            for (int component : componentsOf(glyf, offsets, gid)) {
                if (component < glyphCount && keep.add(component)) {
                    pending.push(component);
                }
            }
        }
        return keep;
    }

    private record Rebuilt(byte[] glyf, byte[] loca) {}

    private static Rebuilt rebuildGlyfAndLoca(
            byte[] glyf, int[] offsets, int glyphCount, TreeSet<Integer> keep) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] newOffsets = new int[glyphCount + 1];
        for (int gid = 0; gid < glyphCount; gid++) {
            newOffsets[gid] = out.size();
            if (!keep.contains(gid)) {
                continue; // a dropped glyph becomes a zero-length loca entry
            }
            int start = offsets[gid];
            int end = offsets[gid + 1];
            if (end > start) {
                out.write(glyf, start, end - start);
                while (out.size() % 4 != 0) {
                    out.write(0);
                }
            }
        }
        newOffsets[glyphCount] = out.size();

        byte[] loca = new byte[newOffsets.length * 4];
        for (int i = 0; i < newOffsets.length; i++) {
            writeInt(loca, i * 4, newOffsets[i]);
        }
        return new Rebuilt(out.toByteArray(), loca);
    }

    /** The glyph ids a composite glyph is built from; empty for a simple or blank glyph. */
    private static List<Integer> componentsOf(byte[] glyf, int[] offsets, int gid) {
        int start = offsets[gid];
        int end = offsets[gid + 1];
        if (end - start < 10 || end > glyf.length) {
            return List.of();
        }
        ByteBuffer glyph = ByteBuffer.wrap(glyf, start, end - start).order(ByteOrder.BIG_ENDIAN).slice();
        if (glyph.getShort(0) >= 0) {
            return List.of(); // a non-negative contour count means a simple glyph
        }

        final int ARG_1_AND_2_ARE_WORDS = 0x0001;
        final int WE_HAVE_A_SCALE = 0x0008;
        final int MORE_COMPONENTS = 0x0020;
        final int WE_HAVE_AN_X_AND_Y_SCALE = 0x0040;
        final int WE_HAVE_A_TWO_BY_TWO = 0x0080;

        List<Integer> components = new ArrayList<>(2);
        int position = 10;
        while (position + 4 <= glyph.limit()) {
            int flags = Short.toUnsignedInt(glyph.getShort(position));
            components.add(Short.toUnsignedInt(glyph.getShort(position + 2)));
            position += 4;
            position += (flags & ARG_1_AND_2_ARE_WORDS) != 0 ? 4 : 2;
            if ((flags & WE_HAVE_A_SCALE) != 0) {
                position += 2;
            } else if ((flags & WE_HAVE_AN_X_AND_Y_SCALE) != 0) {
                position += 4;
            } else if ((flags & WE_HAVE_A_TWO_BY_TWO) != 0) {
                position += 8;
            }
            if ((flags & MORE_COMPONENTS) == 0) {
                break;
            }
        }
        return components;
    }

    /**
     * Assembles a single-font sfnt from tables already ordered by tag, computing
     * the directory, the per-table checksums, and {@code head}'s checksum
     * adjustment over the finished file.
     */
    private static byte[] assemble(TreeMap<Integer, byte[]> tables) {
        int tableCount = tables.size();
        int entrySelector = 31 - Integer.numberOfLeadingZeros(tableCount);
        int searchRange = (1 << entrySelector) * 16;
        int rangeShift = tableCount * 16 - searchRange;

        int directorySize = SFNT_HEADER_SIZE + TABLE_RECORD_SIZE * tableCount;
        int total = directorySize;
        for (byte[] bytes : tables.values()) {
            total += align4(bytes.length);
        }

        byte[] out = new byte[total];
        ByteBuffer buffer = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(0x00010000); // TrueType outlines
        buffer.putShort((short) tableCount);
        buffer.putShort((short) searchRange);
        buffer.putShort((short) entrySelector);
        buffer.putShort((short) rangeShift);

        int dataOffset = directorySize;
        int headOffset = -1;
        for (var entry : tables.entrySet()) {
            byte[] bytes = entry.getValue();
            if (entry.getKey() == TAG_HEAD) {
                headOffset = dataOffset;
            }
            buffer.putInt(entry.getKey());
            buffer.putInt(checksum(bytes, 0, bytes.length));
            buffer.putInt(dataOffset);
            buffer.putInt(bytes.length);
            System.arraycopy(bytes, 0, out, dataOffset, bytes.length);
            dataOffset += align4(bytes.length);
        }

        if (headOffset >= 0) {
            writeInt(
                    out,
                    headOffset + HEAD_CHECKSUM_ADJUSTMENT,
                    CHECKSUM_MAGIC - checksum(out, 0, out.length));
        }
        return out;
    }

    private static int[] readLoca(ByteBuffer data, TableRecord loca, int glyphCount, boolean longOffsets) {
        int count = glyphCount + 1;
        if (loca.length < (long) count * (longOffsets ? 4 : 2)) {
            return null;
        }
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = longOffsets
                    ? data.getInt(loca.offset + i * 4)
                    : Short.toUnsignedInt(data.getShort(loca.offset + i * 2)) * 2;
        }
        // A non-monotonic loca would make every later slice nonsense; refuse it
        // rather than emit a font that only some readers reject.
        for (int i = 1; i < count; i++) {
            if (offsets[i] < offsets[i - 1]) {
                return null;
            }
        }
        return offsets;
    }

    private record TableRecord(int tag, int offset, int length) {}

    private static List<TableRecord> readTableDirectory(ByteBuffer data, int sfntStart) {
        int tableCount = Short.toUnsignedInt(data.getShort(sfntStart + 4));
        List<TableRecord> records = new ArrayList<>(tableCount);
        for (int i = 0; i < tableCount; i++) {
            int base = sfntStart + SFNT_HEADER_SIZE + i * TABLE_RECORD_SIZE;
            if (base + TABLE_RECORD_SIZE > data.limit()) {
                return List.of();
            }
            int offset = data.getInt(base + 8);
            int length = data.getInt(base + 12);
            if (offset < 0 || length < 0 || offset + length > data.limit()) {
                return List.of();
            }
            records.add(new TableRecord(data.getInt(base), offset, length));
        }
        return records;
    }

    /** Where the sfnt begins: 0, or the first face of a {@code ttcf} collection. */
    private static int sfntStart(ByteBuffer data) {
        return data.getInt(0) == tag("ttcf") ? data.getInt(12) : 0;
    }

    private static TableRecord find(List<TableRecord> records, int tag) {
        for (TableRecord record : records) {
            if (record.tag == tag) {
                return record;
            }
        }
        return null;
    }

    private static byte[] slice(byte[] font, TableRecord record) {
        byte[] bytes = new byte[record.length];
        System.arraycopy(font, record.offset, bytes, 0, record.length);
        return bytes;
    }

    /** The sfnt checksum: big-endian 32-bit words summed with wraparound, zero-padded. */
    private static int checksum(byte[] data, int from, int length) {
        int sum = 0;
        for (int i = from; i < from + length; i += 4) {
            int word = 0;
            for (int j = 0; j < 4; j++) {
                int index = i + j;
                word = (word << 8) | (index < from + length ? data[index] & 0xFF : 0);
            }
            sum += word;
        }
        return sum;
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static int align4(int n) {
        return (n + 3) & ~3;
    }

    private static int tag(String name) {
        return (name.charAt(0) << 24) | (name.charAt(1) << 16) | (name.charAt(2) << 8) | name.charAt(3);
    }
}
