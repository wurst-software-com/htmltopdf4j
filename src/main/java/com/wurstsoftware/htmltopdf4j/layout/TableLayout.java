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
import com.wurstsoftware.htmltopdf4j.style.CssColor;
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

    static void layout(Layout layout, TableBox table, float left, float width) {
        int columnCount = table.columnCount();
        if (columnCount == 0 || table.rows().isEmpty()) {
            return;
        }
        Edges margin = Edges.margin(table.style(), width);
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
        float height = 0f;
        for (int i = 0; i < row.cells().size(); i++) {
            TableCell cell = row.cells().get(i);
            float width = spanWidth(columns, starts[i], cell.columnSpan());
            Edges padding = Edges.padding(cell.content().style(), width);
            float content = layout.measureChildren(
                    cell.content(), Math.max(1f, width - padding.horizontal()));
            height = Math.max(height, (content + padding.vertical()) / cell.rowSpan());
        }
        return Math.max(height, minimumRowHeight(row));
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
        float height = rowHeight(layout, row, columns, starts);

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

            layout.setY(top + padding.top() + border.top());
            layout.flowChildren(
                    cell.content().children(),
                    x + padding.left() + border.left(),
                    Math.max(1f, width - padding.horizontal() - border.horizontal()));
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
            decoration.add(new PaintCommand.SetStrokeColor(
                    CssColor.parse(style.raw("border-top-color")).orElse(style.color())));
            decoration.add(new PaintCommand.SetLineWidth(Math.max(border.top(), border.left())));
            decoration.add(new PaintCommand.StrokeRect(rect));
        }
        decoration.forEach(layout.currentPage()::add);
    }

    /** Whether a box holds nothing but a table, which the column measure treats as full width. */
    static boolean isTableOnly(BlockBox block) {
        return block.children().stream().allMatch(child -> child instanceof TableBox);
    }
}
