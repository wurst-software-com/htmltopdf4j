package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.box.BlockBox;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Laying out a {@code display: grid} container's children into tracks.
 *
 * <p>Column tracks come from {@code grid-template-columns}: fixed lengths and
 * percentages are resolved first, and what is left is divided among the
 * {@code fr} tracks in proportion to their fractions. Items are placed
 * row-major into the next free cell unless {@code grid-area} or a line-based
 * {@code grid-column} / {@code grid-row} pins them somewhere.
 *
 * <p>Row heights come from {@code grid-template-rows} where it declares them and
 * from the content otherwise. An {@code fr} row needs something to be a fraction
 * of, so the fractions share the container's declared {@code height}; without one
 * there is nothing to divide and the row is content-sized like an {@code auto}
 * one. Items shorter than their row are placed within it by {@code align-items}
 * and {@code align-self}.
 */
final class GridLayout {

    private GridLayout() {}

    /**
     * One declared track. A track is one of exactly three things, so it is three
     * types rather than three fields: encoding the choice in a
     * {@code points}/{@code fraction}/{@code automatic} triple leaves states
     * that mean nothing and forces every reader to know which combination is
     * which.
     */
    private sealed interface Track {

        /** A track of a size the declaration already fixed. */
        record Definite(float points) implements Track {}

        /** A track claiming a share of whatever the definite ones leave over. */
        record Fractional(float fraction) implements Track {}

        /** A track sized by what it holds. */
        record Automatic() implements Track {}

        Track AUTO = new Automatic();

        static Track of(String token, ComputedStyle style, float basis) {
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (value.endsWith("fr")) {
                try {
                    return new Fractional(Float.parseFloat(value.substring(0, value.length() - 2)));
                } catch (NumberFormatException e) {
                    return AUTO;
                }
            }
            if (value.equals("auto") || value.equals("min-content") || value.equals("max-content")) {
                return AUTO;
            }
            return com.wurstsoftware.htmltopdf4j.style.Length.parse(value)
                    .map(length -> (Track) new Definite(style.resolve(length, basis)))
                    .orElse(AUTO);
        }
    }

    /** Where one item sits in the grid. */
    private record Placement(int column, int columnSpan, int row, int rowSpan) {}

    static void layout(Layout layout, BlockBox container, float left, float width) {
        List<BlockBox> items = FlexLayout.itemsOf(container);
        if (items.isEmpty()) {
            return;
        }
        ComputedStyle style = container.style();
        float columnGap = length(style, "column-gap", width);
        float rowGap = length(style, "row-gap", width);

        Map<String, int[]> areas = areasOf(style);
        List<Track> declared = tracks(style, "grid-template-columns", width);
        int columnCount = Math.max(1, declared.isEmpty() ? impliedColumns(items, areas) : declared.size());
        float[] columns = distribute(columnTracks(declared, columnCount), width, columnGap);

        List<Placement> placements = place(items, columnCount, areas);
        int rowCount = placements.stream().mapToInt(p -> p.row() + p.rowSpan()).max().orElse(1);

        // A row's automatic height is the tallest of the items that start in it,
        // which is not known until they are measured.
        float[] content = new float[rowCount];
        float[] measured = new float[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Placement placement = placements.get(i);
            float itemWidth = span(columns, placement.column(), placement.columnSpan(), columnGap);
            measured[i] = layout.measureChildren(items.get(i), itemWidth);
            content[placement.row()] = Math.max(content[placement.row()], measured[i]);
        }
        float height = length(style, "height", 0f);
        float[] rows = distribute(
                rowTracks(tracks(style, "grid-template-rows", width), rowCount, content, height),
                height, rowGap);

        float top = layout.y();
        float[] rowTops = new float[rowCount + 1];
        rowTops[0] = top;
        for (int row = 0; row < rowCount; row++) {
            rowTops[row + 1] = rowTops[row] + rows[row] + rowGap;
        }

        String containerAlignment = style.raw("align-items");
        for (int i = 0; i < items.size(); i++) {
            Placement placement = placements.get(i);
            float x = left + offset(columns, placement.column(), columnGap);
            float itemWidth = span(columns, placement.column(), placement.columnSpan(), columnGap);
            float track = span(rows, placement.row(), placement.rowSpan(), rowGap);
            float free = track - measured[i];
            // `align-self` decides for one item, `align-items` for the rest.
            String alignment = items.get(i).style().raw("align-self");
            layout.setY(rowTops[placement.row()]
                    + VerticalAlign.offset(alignment != null ? alignment : containerAlignment, free));
            layout.flowItem(items.get(i), x, width, itemWidth);
        }
        layout.setY(Math.max(top, rowTops[rowCount] - rowGap));
    }

    /**
     * Track sizes: every definite track takes what it declared, and the
     * fractional ones share what is left of {@code available} after the definite
     * ones and the gaps are paid for.
     *
     * <p>Both axes end up here. They differ only in what an automatic track
     * means, which each caller settles before calling.
     */
    private static float[] distribute(List<Track> tracks, float available, float gap) {
        float[] sizes = new float[tracks.size()];
        float definite = 0f;
        float fractions = 0f;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i) instanceof Track.Definite track) {
                sizes[i] = track.points();
                definite += track.points();
            } else if (tracks.get(i) instanceof Track.Fractional track) {
                fractions += track.fraction();
            }
        }
        if (fractions <= 0f) {
            return sizes;
        }
        float free = Math.max(0f, available - definite - gap * (tracks.size() - 1));
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i) instanceof Track.Fractional track) {
                sizes[i] += free * (track.fraction() / fractions);
            }
        }
        return sizes;
    }

    /**
     * The column tracks, padded to {@code count}. A column has the container's
     * width to share, so an automatic column is a fraction of it.
     */
    private static List<Track> columnTracks(List<Track> declared, int count) {
        List<Track> tracks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Track track = i < declared.size() ? declared.get(i) : Track.AUTO;
            tracks.add(track instanceof Track.Automatic ? new Track.Fractional(1f) : track);
        }
        return tracks;
    }

    /**
     * The row tracks, padded to {@code count}. An automatic row is as tall as
     * what it holds; so is a fractional one when the container declares no
     * height, because a fraction needs something to be a fraction of.
     */
    private static List<Track> rowTracks(List<Track> declared, int count, float[] content, float height) {
        List<Track> tracks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Track track = i < declared.size() ? declared.get(i) : Track.AUTO;
            boolean sizeless = track instanceof Track.Automatic
                    || (track instanceof Track.Fractional && height <= 0f);
            tracks.add(sizeless ? new Track.Definite(content[i]) : track);
        }
        return tracks;
    }

    private static List<Track> tracks(ComputedStyle style, String property, float basis) {
        String value = style.raw(property);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Track> tracks = new ArrayList<>();
        for (String token : expandRepeat(value).split("\\s+")) {
            if (!token.isBlank()) {
                tracks.add(Track.of(token, style, basis));
            }
        }
        return tracks;
    }

    /** Expands {@code repeat(n, ...)} by writing its track list out n times. */
    private static String expandRepeat(String value) {
        int open = value.toLowerCase(Locale.ROOT).indexOf("repeat(");
        if (open < 0) {
            return value;
        }
        int close = value.indexOf(')', open);
        if (close < 0) {
            return value;
        }
        String[] arguments = value.substring(open + 7, close).split(",", 2);
        if (arguments.length < 2) {
            return value;
        }
        int times;
        try {
            times = Math.clamp(Integer.parseInt(arguments[0].trim()), 0, 64);
        } catch (NumberFormatException e) {
            // auto-fill and auto-fit need a viewport to count against.
            return value.substring(0, open) + arguments[1].trim() + value.substring(close + 1);
        }
        return value.substring(0, open)
                + (arguments[1].trim() + " ").repeat(times)
                + expandRepeat(value.substring(close + 1));
    }

    /**
     * Places items row-major into the next free cell, honouring an explicit
     * {@code grid-area}, {@code grid-column} or {@code grid-row}.
     */
    private static List<Placement> place(List<BlockBox> items, int columnCount, Map<String, int[]> areas) {
        List<Placement> placements = new ArrayList<>(items.size());
        boolean[][] occupied = new boolean[items.size() * 2 + 2][columnCount];
        int cursorRow = 0;
        int cursorColumn = 0;

        for (BlockBox item : items) {
            ComputedStyle style = item.style();
            int[] area = areas.get(String.valueOf(style.raw("grid-area", "")).trim());
            Placement placement;
            if (area != null) {
                placement = new Placement(area[1], area[3] - area[1], area[0], area[2] - area[0]);
            } else {
                int columnSpan = Math.clamp(spanOf(style, "grid-column"), 1, columnCount);
                int rowSpan = Math.max(1, spanOf(style, "grid-row"));
                Integer column = lineOf(style, "grid-column", columnCount);
                if (column != null) {
                    cursorColumn = column;
                }
                while (cursorColumn + columnSpan > columnCount || taken(occupied, cursorRow, cursorColumn, columnSpan)) {
                    cursorColumn++;
                    if (cursorColumn + columnSpan > columnCount) {
                        cursorColumn = 0;
                        cursorRow++;
                    }
                }
                placement = new Placement(cursorColumn, columnSpan, cursorRow, rowSpan);
                cursorColumn += columnSpan;
            }
            mark(occupied, placement);
            placements.add(placement);
        }
        return placements;
    }

    private static boolean taken(boolean[][] occupied, int row, int column, int span) {
        if (row >= occupied.length) {
            return false;
        }
        for (int i = column; i < column + span && i < occupied[row].length; i++) {
            if (occupied[row][i]) {
                return true;
            }
        }
        return false;
    }

    private static void mark(boolean[][] occupied, Placement placement) {
        for (int row = placement.row(); row < placement.row() + placement.rowSpan() && row < occupied.length; row++) {
            for (int column = placement.column();
                    column < placement.column() + placement.columnSpan() && column < occupied[row].length;
                    column++) {
                occupied[row][column] = true;
            }
        }
    }

    /** {@code grid-column: span 2} and {@code grid-column: 1 / 3} both describe a span. */
    private static int spanOf(ComputedStyle style, String property) {
        String value = style.raw(property + "-span");
        if (value != null) {
            return parse(value, 1);
        }
        String start = style.raw(property + "-start");
        String end = style.raw(property + "-end");
        if (start != null && start.trim().toLowerCase(Locale.ROOT).startsWith("span")) {
            return parse(start.trim().substring(4), 1);
        }
        if (end != null && end.trim().toLowerCase(Locale.ROOT).startsWith("span")) {
            return parse(end.trim().substring(4), 1);
        }
        if (start != null && end != null) {
            return Math.max(1, parse(end, 2) - parse(start, 1));
        }
        return 1;
    }

    /** The zero-based column a line-based placement starts at, or {@code null} for automatic. */
    private static Integer lineOf(ComputedStyle style, String property, int count) {
        String start = style.raw(property + "-start");
        if (start == null || start.trim().toLowerCase(Locale.ROOT).startsWith("span")) {
            return null;
        }
        int line = parse(start, 1);
        // A negative line counts back from the end, as CSS defines it.
        return Math.clamp(line > 0 ? line - 1 : count + line, 0, Math.max(0, count - 1));
    }

    private static int parse(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }

    /**
     * The named rectangles of {@code grid-template-areas}, as
     * {@code [rowStart, columnStart, rowEnd, columnEnd]}.
     */
    private static Map<String, int[]> areasOf(ComputedStyle style) {
        String value = style.raw("grid-template-areas");
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        Map<String, int[]> areas = new LinkedHashMap<>();
        int row = 0;
        for (String line : value.split("[\"']")) {
            if (line.isBlank()) {
                continue;
            }
            String[] names = line.trim().split("\\s+");
            for (int column = 0; column < names.length; column++) {
                String name = names[column];
                if (name.equals(".")) {
                    continue;
                }
                int[] rectangle = areas.get(name);
                if (rectangle == null) {
                    areas.put(name, new int[] {row, column, row + 1, column + 1});
                } else {
                    rectangle[2] = Math.max(rectangle[2], row + 1);
                    rectangle[3] = Math.max(rectangle[3], column + 1);
                }
            }
            row++;
        }
        return areas;
    }

    /** A grid with no declared columns is as wide as its named areas, or one column. */
    private static int impliedColumns(List<BlockBox> items, Map<String, int[]> areas) {
        return areas.values().stream().mapToInt(rectangle -> rectangle[3]).max().orElse(1);
    }

    private static float span(float[] columns, int start, int span, float gap) {
        float width = 0f;
        for (int i = start; i < start + span && i < columns.length; i++) {
            width += columns[i] + (i > start ? gap : 0f);
        }
        return Math.max(1f, width);
    }

    private static float offset(float[] columns, int column, float gap) {
        float x = 0f;
        for (int i = 0; i < column && i < columns.length; i++) {
            x += columns[i] + gap;
        }
        return x;
    }

    private static float length(ComputedStyle style, String property, float basis) {
        return style.length(property).map(value -> style.resolve(value, basis)).orElse(0f);
    }
}
