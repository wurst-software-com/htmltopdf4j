package com.wurstsoftware.htmltopdf4j.box;

/**
 * One {@code <td>} or {@code <th>}. Its content is a block box, so a cell holds
 * anything a block holds — paragraphs, lists, nested tables — rather than only
 * text.
 */
public record TableCell(BlockBox content, int columnSpan, int rowSpan, boolean header) {

    public TableCell {
        columnSpan = Math.max(1, columnSpan);
        rowSpan = Math.max(1, rowSpan);
    }
}
