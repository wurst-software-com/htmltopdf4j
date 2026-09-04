package com.wurstsoftware.htmltopdf4j.box;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import java.util.List;

/** One {@code <tr>}, with the table section it belongs to. */
public record TableRow(Section section, ComputedStyle style, List<TableCell> cells) {

    public TableRow {
        cells = List.copyOf(cells);
    }

    /** Which part of the table a row belongs to; a header row repeats on every Page. */
    public enum Section {
        HEADER,
        BODY,
        FOOTER
    }

    public int columnCount() {
        return cells.stream().mapToInt(TableCell::columnSpan).sum();
    }
}
