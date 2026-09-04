package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.box.BlockBox;
import com.wurstsoftware.htmltopdf4j.box.BoxChild;
import com.wurstsoftware.htmltopdf4j.box.ImageBox;
import com.wurstsoftware.htmltopdf4j.box.LineBox;
import com.wurstsoftware.htmltopdf4j.box.TableBox;
import com.wurstsoftware.htmltopdf4j.box.TableCell;
import com.wurstsoftware.htmltopdf4j.box.TableRow;
import com.wurstsoftware.htmltopdf4j.paint.Color;
import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.style.Length;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Laying a table out into rows of cells.
 *
 * <p>Column widths come from a two-pass measure — each cell's unwrapped width,
 * then a proportional shrink onto the available width — which is the automatic
 * table layout algorithm in the shape browsers actually use. Declared
 * {@code <col>} widths short-circuit it.
 *
 * <p>A table taller than a Page is split between rows, never through one, and
 * its header rows are repeated at the top of each continuation. A single row too
 * tall for a whole Page is drawn anyway rather than looping forever.
 */
final class TableLayout {

    private TableLayout() {}

    /**
     * Which column each cell starts in, once the cells spanning down from
     * earlier rows have taken their columns.
     *
     * <p>A {@code rowspan} cell occupies its column in the rows below it, so the
     * cells in those rows shift right. Without this a table with a spanning cell
     * has every row after the first misaligned by one column.
     */
    private static List<int[]> columnStarts(TableBox table, int columnCount) {
        List<int[]> starts = new ArrayList<>(table.rows().size());
        int[] spannedFrom = new int[columnCount];
        for (TableRow row : table.rows()) {
            int[] rowStarts = new int[row.cells().size()];
            int column = 0;
            for (int i = 0; i < row.cells().size(); i++) {
                while (column < columnCount && spannedFrom[column] > 0) {
                    column++;
                }
                rowStarts[i] = Math.min(column, Math.max(0, columnCount - 1));
                column += row.cells().get(i).columnSpan();
            }
            starts.add(rowStarts);
            for (int i = 0; i < row.cells().size(); i++) {
                TableCell cell = row.cells().get(i);
                for (int c = rowStarts[i]; c < rowStarts[i] + cell.columnSpan() && c < columnCount; c++) {
                    spannedFrom[c] = cell.rowSpan();
                }
            }
            for (int c = 0; c < columnCount; c++) {
                spannedFrom[c] = Math.max(0, spannedFrom[c] - 1);
            }
        }
        return starts;
    }

    /**
     * How tall the whole table would be, header and all, without laying it out.
     *
     * <p>This is what lets a box wrapping a table be measured at all: without it
     * such a box measures as zero and anything that depends on its height — a
     * grid track, a cell, an inline-block, a Page-break decision — is wrong.
     */
    static float measure(Layout layout, TableBox table, float width) {
        int columnCount = table.columnCount();
        if (columnCount == 0 || table.rows().isEmpty()) {
            return 0f;
        }
        Edges margin = Edges.margin(table.style(), width);
        float[] columns = columnWidths(layout, table, columnCount, Math.max(1f, width - margin.horizontal()));
        List<int[]> starts = columnStarts(table, columnCount);

        float height = margin.vertical();
        for (int index = 0; index < table.rows().size(); index++) {
            height += rowHeight(layout, table.rows().get(index), columns, starts.get(index));
        }
        return height;
    }

    static void layout(Layout layout, TableBox table, float left, float width) {
        int columnCount = table.columnCount();
        if (columnCount == 0 || table.rows().isEmpty()) {
            return;
        }
        Edges margin = Edges.margin(table.style(), width);
        // A table never reaches `layoutBlock`, so it honours the declaration here.
        if (BreakInside.avoids(table.style())) {
            layout.ensureWhole(measure(layout, table, width));
        }
        layout.setY(layout.y() + margin.top());

        float available = Math.max(1f, width - margin.horizontal());
        float[] columns = columnWidths(layout, table, columnCount, available);
        List<int[]> starts = columnStarts(table, columnCount);
        List<TableRow> header = table.rows().stream()
                .filter(row -> row.section() == TableRow.Section.HEADER)
                .toList();

        // The header is drawn at the top of the table and again at the top of
        // every Page the table continues onto.
        boolean headerPending = !header.isEmpty();
        for (int index = 0; index < table.rows().size(); index++) {
            TableRow row = table.rows().get(index);
            if (row.section() == TableRow.Section.HEADER && !headerPending) {
                continue;
            }
            float height = rowHeight(layout, row, columns, starts.get(index));
            if (layout.y() + height > layout.contentBottom() && layout.y() > layout.contentTop()) {
                layout.breakPage();
                headerPending = !header.isEmpty();
            }
            if (headerPending) {
                for (int i = 0; i < table.rows().size(); i++) {
                    if (table.rows().get(i).section() == TableRow.Section.HEADER) {
                        drawRow(layout, table.rows().get(i), left + margin.left(), columns, starts.get(i));
                    }
                }
                headerPending = false;
                if (row.section() == TableRow.Section.HEADER) {
                    continue;
                }
            }
            drawRow(layout, row, left + margin.left(), columns, starts.get(index));
        }
        layout.setY(layout.y() + margin.bottom());
    }

    /**
     * Column widths: the declared ones when the table has them, otherwise each
     * column's widest cell, scaled to fit the available width.
     */
    private static float[] columnWidths(Layout layout, TableBox table, int count, float available) {
        float[] widths = new float[count];
        if (!table.columnWidths().isEmpty()) {
            float declared = 0f;
            for (int i = 0; i < count; i++) {
                widths[i] = i < table.columnWidths().size()
                        ? table.columnWidths().get(i) * Length.POINTS_PER_PIXEL
                        : 0f;
                declared += widths[i];
            }
            if (declared > 0f) {
                return scale(widths, available / declared);
            }
        }
        List<int[]> starts = columnStarts(table, count);
        for (int r = 0; r < table.rows().size(); r++) {
            TableRow row = table.rows().get(r);
            for (int i = 0; i < row.cells().size(); i++) {
                TableCell cell = row.cells().get(i);
                float wanted = layout.measureIntrinsicWidth(cell.content(), available);
                // A spanning cell contributes its share to each column it covers
                // rather than forcing the first one wide.
                float share = wanted / cell.columnSpan();
                int start = starts.get(r)[i];
                for (int c = 0; c < cell.columnSpan() && start + c < count; c++) {
                    widths[start + c] = Math.max(widths[start + c], share);
                }
            }
        }
        float total = 0f;
        for (float width : widths) {
            total += width;
        }
        if (total <= 0f) {
            java.util.Arrays.fill(widths, available / count);
            return widths;
        }
        return scale(widths, available / total);
    }

    private static float[] scale(float[] widths, float factor) {
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.max(1f, widths[i] * factor);
        }
        return widths;
    }

    /**
     * A row is as tall as its tallest cell. A cell spanning several rows
     * contributes only its share, so one tall spanning cell does not inflate the
     * first row it appears in.
     */
    private static float rowHeight(Layout layout, TableRow row, float[] columns, int[] starts) {
        return rowHeight(layout, row, columns, starts, baselineShifts(layout, row, columns, starts));
    }

    /** The same, for a caller that has already worked out the row's baseline shifts. */
    private static float rowHeight(
            Layout layout, TableRow row, float[] columns, int[] starts, Baselines baselines) {

        float height = 0f;
        for (int i = 0; i < row.cells().size(); i++) {
            TableCell cell = row.cells().get(i);
            float width = spanWidth(columns, starts[i], cell.columnSpan());
            Edges padding = Edges.padding(cell.content().style(), width);
            float content = layout.measureChildren(
                    cell.content(), Math.max(1f, width - padding.horizontal()));
            // A cell dropped onto the row's baseline needs the room it was
            // dropped by as well, or the row's last line falls out of the bottom.
            height = Math.max(height,
                    (content + padding.vertical() + baselines.shift(i)) / cell.rowSpan());
        }
        return Math.max(height, minimumRowHeight(row));
    }

    /**
     * How far each cell has to drop for its first line to sit on the row's
     * common baseline — the deepest first baseline among the cells aligned on
     * it. A cell aligned some other way, or with no text to align, does not
     * move and does not vote.
     */
    private static Baselines baselineShifts(Layout layout, TableRow row, float[] columns, int[] starts) {
        Baselines baselines = new Baselines(row.cells().size());
        for (int i = 0; i < row.cells().size(); i++) {
            TableCell cell = row.cells().get(i);
            ComputedStyle style = cell.content().style();
            // A cell says nothing about `vertical-align` far more often than it
            // says `baseline`, and both mean the row's baseline.
            if (!Alignment.of(style, "vertical-align").orElse(Alignment.BASELINE).isBaseline()) {
                continue;
            }
            float width = spanWidth(columns, starts[i], cell.columnSpan());
            Edges padding = Edges.padding(style, width);
            Edges border = Edges.borderWidths(style);
            float inner = Math.max(1f, width - padding.horizontal() - border.horizontal());
            float baseline = layout.firstBaseline(cell.content().children(), inner);
            if (!Float.isNaN(baseline)) {
                baselines.measured(i, padding.top() + border.top() + baseline);
            }
        }
        return baselines;
    }

    private static float minimumRowHeight(TableRow row) {
        return row.cells().isEmpty() ? 0f : row.cells().get(0).content().style().fontSize() * 1.4f;
    }

    private static float offset(float[] columns, int column) {
        float x = 0f;
        for (int i = 0; i < column && i < columns.length; i++) {
            x += columns[i];
        }
        return x;
    }

    private static float spanWidth(float[] columns, int start, int span) {
        float width = 0f;
        for (int i = start; i < start + span && i < columns.length; i++) {
            width += columns[i];
        }
        return width;
    }

    private static void drawRow(Layout layout, TableRow row, float left, float[] columns, int[] starts) {
        float top = layout.y();
        Baselines baselines = baselineShifts(layout, row, columns, starts);
        float height = rowHeight(layout, row, columns, starts, baselines);

        for (int i = 0; i < row.cells().size(); i++) {
            TableCell cell = row.cells().get(i);
            float x = left + offset(columns, starts[i]);
            float width = spanWidth(columns, starts[i], cell.columnSpan());
            // A cell spanning rows is as tall as all of them together.
            float cellHeight = height * cell.rowSpan();
            ComputedStyle style = cell.content().style();
            Edges padding = Edges.padding(style, width);
            Edges border = Edges.borderWidths(style);

            paintCellDecoration(layout, style, x, top, width, cellHeight, border);

            float inner = Math.max(1f, width - padding.horizontal() - border.horizontal());
            float free = cellHeight
                    - padding.vertical()
                    - border.vertical()
                    - layout.measureChildren(cell.content(), inner);
            layout.setY(top + padding.top() + border.top()
                    + baselines.offset(i, Alignment.of(style, "vertical-align"), free));
            layout.flowChildren(cell.content().children(), x + padding.left() + border.left(), inner);
        }
        layout.setY(top + height);
    }

    private static void paintCellDecoration(
            Layout layout, ComputedStyle style, float x, float top, float width, float height, Edges border) {

        Rect rect = new Rect(x, layout.toPdfY(top + height), width, height);
        List<PaintCommand> decoration = new ArrayList<>();
        Optional<Color> background = style.backgroundColor();
        background.ifPresent(color -> {
            decoration.add(new PaintCommand.SetFillColor(color));
            decoration.add(new PaintCommand.FillRect(rect));
        });
        com.wurstsoftware.htmltopdf4j.style.LinearGradient.parse(style.raw("background-image"))
                .ifPresent(gradient -> Layout.paintGradient(decoration, gradient, rect));
        if (border.top() + border.right() + border.bottom() + border.left() > 0f) {
            // The same per-side stroking blocks get: a cell declaring only
            // `border-bottom` should get one line under it, not a box round it.
            Layout.paintBorders(decoration, style, rect, border, 0f);
        }
        decoration.forEach(layout.currentPage()::add);
    }

    /** Whether a box holds nothing but a table, which the column measure treats as full width. */
    static boolean isTableOnly(BlockBox block) {
        return block.children().stream().allMatch(child -> child instanceof TableBox);
    }
}
