package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.box.BlockBox;
import com.wurstsoftware.htmltopdf4j.box.BoxChild;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.style.Display;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Laying out a {@code display: flex} container's children as flex items.
 *
 * <p>Items are sized from their {@code flex-basis} (or their content when that
 * is {@code auto}), then grown or shrunk to fill the container, then placed
 * along the main axis by {@code justify-content}. That is the flexbox algorithm
 * in the shape that matters for a paged Document; what is deliberately not here
 * is baseline alignment and the intrinsic-size resolution a live layout needs to
 * do for resizable viewports, neither of which a fixed Page ever exercises.
 */
final class FlexLayout {

    private FlexLayout() {}

    /** Which way a container's main axis runs. */
    private record Direction(boolean row, boolean reversed) {

        static Direction of(ComputedStyle style) {
            return switch (style.raw("flex-direction", "row").trim().toLowerCase(Locale.ROOT)) {
                case "column" -> new Direction(false, false);
                case "column-reverse" -> new Direction(false, true);
                case "row-reverse" -> new Direction(true, true);
                default -> new Direction(true, false);
            };
        }
    }

    /** One flex item, with the sizes the algorithm needs to resolve it. */
    private record Item(BlockBox box, float base, float grow, float shrink, float outerMargin) {

        float resolved(float extra) {
            return Math.max(0f, base + extra);
        }
    }

    static void layout(Layout layout, BlockBox container, float left, float width) {
        List<BlockBox> children = itemsOf(container);
        if (children.isEmpty()) {
            return;
        }
        ComputedStyle style = container.style();
        Direction direction = Direction.of(style);
        float columnGap = gap(style, "column-gap", width);
        float rowGap = gap(style, "row-gap", width);

        if (!direction.row()) {
            layoutColumn(layout, children, left, width, rowGap, direction.reversed());
            return;
        }

        boolean wraps = !style.raw("flex-wrap", "nowrap").trim().toLowerCase(Locale.ROOT).equals("nowrap");
        List<Item> items = children.stream().map(child -> item(layout, child, width)).toList();
        for (List<Item> line : lines(items, width, columnGap, wraps)) {
            layoutLine(layout, line, style, left, width, columnGap, direction.reversed());
            layout.setY(layout.y() + rowGap);
        }
        layout.setY(layout.y() - rowGap);
    }

    /** A column container stacks its items, which is what block flow already does. */
    private static void layoutColumn(
            Layout layout, List<BlockBox> children, float left, float width, float gap, boolean reversed) {

        List<BlockBox> order = new ArrayList<>(children);
        if (reversed) {
            java.util.Collections.reverse(order);
        }
        for (BlockBox child : order) {
            layout.flowItem(child, left, width, null);
            layout.setY(layout.y() + gap);
        }
        layout.setY(layout.y() - gap);
    }

    /** Breaks items onto flex lines, one line unless the container wraps. */
    private static List<List<Item>> lines(List<Item> items, float width, float gap, boolean wraps) {
        if (!wraps) {
            return List.of(items);
        }
        List<List<Item>> lines = new ArrayList<>();
        List<Item> current = new ArrayList<>();
        float used = 0f;
        for (Item item : items) {
            float needed = item.base() + item.outerMargin();
            if (!current.isEmpty() && used + gap + needed > width) {
                lines.add(current);
                current = new ArrayList<>();
                used = 0f;
            }
            used += (current.isEmpty() ? 0f : gap) + needed;
            current.add(item);
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }

    /**
     * Sizes one flex line's items and places them along the main axis.
     *
     * <p>Free space goes to the items in proportion to {@code flex-grow}; a
     * shortfall is taken away in proportion to {@code flex-shrink}. An item with
     * neither keeps its base size, which is what makes a fixed sidebar beside a
     * growing body work.
     */
    private static void layoutLine(
            Layout layout,
            List<Item> line,
            ComputedStyle container,
            float left,
            float width,
            float gap,
            boolean reversed) {

        float used = 0f;
        float grow = 0f;
        float shrink = 0f;
        for (Item item : line) {
            used += item.base() + item.outerMargin();
            grow += item.grow();
            shrink += item.shrink() * item.base();
        }
        float free = width - used - gap * (line.size() - 1);

        float[] sizes = new float[line.size()];
        for (int i = 0; i < line.size(); i++) {
            Item item = line.get(i);
            float extra = 0f;
            if (free > 0f && grow > 0f) {
                extra = free * (item.grow() / grow);
            } else if (free < 0f && shrink > 0f) {
                extra = free * (item.shrink() * item.base() / shrink);
            }
            sizes[i] = item.resolved(extra);
        }

        float total = gap * (line.size() - 1);
        for (int i = 0; i < sizes.length; i++) {
            total += sizes[i] + line.get(i).outerMargin();
        }
        float slack = Math.max(0f, width - total);
        String justify = container.raw("justify-content", "flex-start").trim().toLowerCase(Locale.ROOT);
        float x = left + leadingSpace(justify, slack, line.size());
        float between = gap + betweenSpace(justify, slack, line.size());

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < line.size(); i++) {
            order.add(i);
        }
        if (reversed) {
            java.util.Collections.reverse(order);
        }

        // A line whose items ask to stay whole moves as a unit, for the same
        // reason a grid row does: half a line on each Page is nobody's intent.
        if (line.stream().anyMatch(item -> BreakInside.avoids(item.box().style()))) {
            float lineHeight = 0f;
            for (int i = 0; i < line.size(); i++) {
                lineHeight = Math.max(lineHeight, layout.measureChildren(line.get(i).box(), sizes[i]));
            }
            layout.ensureWhole(lineHeight);
        }

        float top = layout.y();
        float bottom = top;
        for (int index : order) {
            layout.setY(top);
            layout.flowItem(line.get(index).box(), x, width, sizes[index]);
            bottom = Math.max(bottom, layout.y());
            x += sizes[index] + line.get(index).outerMargin() + between;
        }
        layout.setY(bottom);
    }

    private static float leadingSpace(String justify, float slack, int count) {
        return switch (justify) {
            case "flex-end", "end", "right" -> slack;
            case "center" -> slack / 2f;
            case "space-around" -> count > 0 ? slack / (count * 2f) : 0f;
            case "space-evenly" -> slack / (count + 1f);
            default -> 0f;
        };
    }

    private static float betweenSpace(String justify, float slack, int count) {
        if (count < 2) {
            return justify.equals("space-between") ? 0f : 0f;
        }
        return switch (justify) {
            case "space-between" -> slack / (count - 1);
            case "space-around" -> slack / count;
            case "space-evenly" -> slack / (count + 1f);
            default -> 0f;
        };
    }

    /** An item's base size and its flexibility, read from the shorthand's longhands. */
    private static Item item(Layout layout, BlockBox box, float containingWidth) {
        ComputedStyle style = box.style();
        Edges margin = Edges.margin(style, containingWidth);
        float basis = style.length("flex-basis")
                .map(length -> style.resolve(length, containingWidth))
                .orElseGet(() -> style.length("width")
                        .map(length -> style.resolve(length, containingWidth))
                        .orElseGet(() -> layout.measureIntrinsicWidth(box, containingWidth)));
        return new Item(
                box,
                Math.max(0f, basis),
                number(style.raw("flex-grow"), 0f),
                number(style.raw("flex-shrink"), 1f),
                margin.horizontal());
    }

    private static float number(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float gap(ComputedStyle style, String property, float basis) {
        return style.length(property).map(length -> style.resolve(length, basis)).orElse(0f);
    }

    /**
     * The container's flex items, in {@code order} order. Anything that is not a
     * block — stray text between items — is not a flex item and is skipped, as a
     * browser wraps it in an anonymous item and this engine simply drops it.
     */
    static List<BlockBox> itemsOf(BlockBox container) {
        List<BlockBox> items = new ArrayList<>();
        for (BoxChild child : container.children()) {
            if (child instanceof BlockBox block && block.style().display() != Display.NONE) {
                items.add(block);
            }
        }
        items.sort(Comparator.comparingInt(box -> (int) number(box.style().raw("order"), 0f)));
        return items;
    }
}
