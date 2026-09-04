package com.wurstsoftware.htmltopdf4j.pdf;

import com.wurstsoftware.htmltopdf4j.text.CidLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code /ToUnicode} CMap: what makes text in a Type0/Identity-H font
 * selectable and searchable rather than a picture of words.
 *
 * <p>Without it a reader sees only glyph ids, which mean nothing outside the
 * font. A ligature glyph maps back to all of the characters it stands for, so
 * copying "office" out of a PDF yields "office" and not "of&#xFB03;ce".
 */
final class ToUnicodeCMap {

    /** The spec caps a {@code beginbfchar} block at 100 entries. */
    private static final int MAX_ENTRIES_PER_BLOCK = 100;

    private static final String PROLOGUE =
            """
            /CIDInit /ProcSet findresource begin
            12 dict begin
            begincmap
            /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
            /CMapName /Adobe-Identity-UCS def
            /CMapType 2 def
            1 begincodespacerange
            <0000> <FFFF>
            endcodespacerange
            """;

    private static final String EPILOGUE =
            """
            endcmap
            CMapName currentdict /CMap defineresource pop
            end
            end
            """;

    private ToUnicodeCMap() {}

    static byte[] of(CidLayout cid) {
        List<Map.Entry<Integer, String>> entries = new ArrayList<>(cid.toUnicode().entrySet());
        StringBuilder cmap = new StringBuilder(PROLOGUE);

        for (int from = 0; from < entries.size(); from += MAX_ENTRIES_PER_BLOCK) {
            List<Map.Entry<Integer, String>> block =
                    entries.subList(from, Math.min(from + MAX_ENTRIES_PER_BLOCK, entries.size()));
            cmap.append(block.size()).append(" beginbfchar\n");
            for (Map.Entry<Integer, String> entry : block) {
                cmap.append('<')
                        .append(String.format(Locale.ROOT, "%04X", entry.getKey()))
                        .append("> <")
                        .append(PdfSyntax.utf16BeHex(entry.getValue()))
                        .append(">\n");
            }
            cmap.append("endbfchar\n");
        }

        return cmap.append(EPILOGUE).toString().getBytes(StandardCharsets.US_ASCII);
    }
}
