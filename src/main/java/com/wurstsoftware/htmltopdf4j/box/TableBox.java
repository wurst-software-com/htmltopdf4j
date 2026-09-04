package com.wurstsoftware.htmltopdf4j.box;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import java.util.List;

/**
 * A table, in document order among the flow content around it.
 *
 * <p>Rows are flattened out of the {@code <thead>}/{@code <tbody>}/{@code <tfoot>}
 * subtree at generation time, each keeping the section it came from, so Layout
 * can repeat header rows on each Page without walking the tree again.
 *
 * @param columnWidths declared {@code <col>} widths in points; empty means every
 *     column is sized automatically
 */
public record TableBox(ComputedStyle style, List<TableRow> rows, List<Float> columnWidths, String anchor)
        implements BoxChild {

    public TableBox {
        rows = List.copyOf(rows);
        columnWidths = List.copyOf(columnWidths);
    }

    @Override
    public boolean hasContent() {
        return rows.stream().anyMatch(row -> !row.cells().isEmpty());
    }

    /** The widest row's cell count, counting column spans. */
    public int columnCount() {
        return rows.stream().mapToInt(TableRow::columnCount).max().orElse(0);
    }
}
