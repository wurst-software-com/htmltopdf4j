package com.wurstsoftware.htmltopdf4j.box;

import com.wurstsoftware.htmltopdf4j.style.Cascade;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.style.Display;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Box generation: lowering a cascaded Document into the Box tree Layout walks.
 *
 * <p>The DOM and the Box tree are not the same shape. An element with
 * {@code display: none} generates no box; an element with both text and block
 * children has its text wrapped in anonymous blocks; a {@code <br>} splits one
 * element's inline content into several {@link LineBox}es; the rows of a table
 * are flattened out of their section elements. This class is where those
 * differences are decided, so Layout only ever sees boxes.
 */
public final class BoxTreeBuilder {

    private final Cascade cascade;

    /**
     * The decorated inline elements currently open, outermost first.
     *
     * <p>This is a field rather than a parameter threaded through the walk
     * because it is pure traversal state: it always describes the path from the
     * nearest block down to the run being emitted, and a block box that opens
     * inside an inline one starts a line of its own with an empty path.
     */
    private final List<InlineBox> openInlines = new ArrayList<>();

    /** Gives each decorated inline element an identity its runs can share. */
    private int nextInlineId;

    private BoxTreeBuilder(Cascade cascade) {
        this.cascade = cascade;
    }

    /** Generates the Box tree for a Document that has already been cascaded. */
    public static BoxTree build(Document document, Cascade cascade) {
        Element root = document.body() != null ? document.body() : document.root();
        BoxTreeBuilder builder = new BoxTreeBuilder(cascade);
        return new BoxTree(builder.childrenOf(root, null));
    }

    /**
     * Accumulates one block's content, keeping the inline runs seen since the
     * last block-level child so they can be flushed into a {@link LineBox} at the
     * moment one arrives — which is what an anonymous block box is.
     */
    private final class Content {
        private final List<BoxChild> children = new ArrayList<>();
        private final List<InlineRun> pending = new ArrayList<>();

        void inline(InlineRun run) {
            pending.add(run);
        }

        void block(BoxChild child) {
            flush();
            children.add(child);
        }

        /** Ends the current line box, which is what a {@code <br>} does. */
        void breakLine(ComputedStyle style) {
            if (pending.isEmpty()) {
                // A <br> on an otherwise empty line still advances one line, and
                // it advances by the height of the font in force there.
                pending.add(InlineRun.text("", style, null));
            }
            flush();
        }

        void flush() {
            if (pending.isEmpty()) {
                return;
            }
            trimTrailingSpace(pending);
            children.add(new LineBox(List.copyOf(pending)));
            pending.clear();
        }

        List<BoxChild> finish() {
            flush();
            return children;
        }

        /** Whether the next text node would start at the beginning of a line. */
        boolean atLineStart() {
            return pending.isEmpty();
        }

        String lastText() {
            for (int i = pending.size() - 1; i >= 0; i--) {
                InlineRun run = pending.get(i);
                if (!run.isText()) {
                    return "x";
                }
                if (!run.text().isEmpty()) {
                    return run.text();
                }
            }
            return "";
        }
    }

    private List<BoxChild> childrenOf(Element element, String link) {
        Content content = new Content();
        appendChildren(element, content, link);
        List<BoxChild> children = content.finish();
        return blockifies(cascade.styleOf(element)) ? blockify(children, cascade.styleOf(element)) : children;
    }

    /**
     * Whether an element's children are laid out as items rather than as flow
     * content — a flex or grid container's are.
     */
    private static boolean blockifies(ComputedStyle style) {
        return switch (style.display()) {
            case FLEX, INLINE_FLEX, GRID, INLINE_GRID -> true;
            default -> false;
        };
    }

    /**
     * Wraps a flex or grid container's inline content in anonymous block boxes.
     *
     * <p>A flex container has no inline content: every child, including a bare
     * {@code <span>} or a run of text, becomes an item. Without this a
     * {@code <span>} between two flex items would vanish, because the layout only
     * looks at block children.
     */
    private static List<BoxChild> blockify(List<BoxChild> children, ComputedStyle style) {
        List<BoxChild> items = new ArrayList<>(children.size());
        for (BoxChild child : children) {
            items.add(child instanceof LineBox line ? blockifyLine(line, style) : child);
        }
        return items;
    }

    /**
     * One anonymous item per inline element in the line, so sibling
     * {@code <span>}s become sibling items rather than one merged item.
     */
    private static BoxChild blockifyLine(LineBox line, ComputedStyle style) {
        if (line.runs().size() == 1 && line.runs().get(0).inlineBlock() != null) {
            return line.runs().get(0).inlineBlock();
        }
        return BlockBox.anonymous(style, List.of(line));
    }

    private void appendChildren(Element element, Content content, String link) {
        ComputedStyle style = cascade.styleOf(element);
        appendGenerated(element, Cascade.Pseudo.BEFORE, content, link);
        for (Node node : element.childNodes()) {
            switch (node) {
                case TextNode text -> appendText(text.getWholeText(), style, content, link);
                case Element child -> appendElement(child, content, link);
                default -> { /* comments, doctypes and data nodes generate no boxes */ }
            }
        }
        appendGenerated(element, Cascade.Pseudo.AFTER, content, link);
    }

    /**
     * Emits a {@code ::before} or {@code ::after}'s content as an inline run.
     *
     * <p>Generated content is inline: it joins the line its originating element
     * is on rather than starting one, which is what makes a required-field
     * asterisk sit against the label instead of below it.
     */
    private void appendGenerated(Element element, Cascade.Pseudo pseudo, Content content, String link) {
        cascade.pseudoStyleOf(element, pseudo).ifPresent(style -> {
            String text = com.wurstsoftware.htmltopdf4j.style.ContentValue.of(
                    style.raw("content"),
                    name -> element.hasAttr(name) ? element.attr(name) : null,
                    // An element's generated content has no counters: this engine
                    // keeps only the Page counters, and those belong to @page.
                    com.wurstsoftware.htmltopdf4j.style.ContentValue.NONE);
            if (!text.isEmpty()) {
                content.inline(InlineRun.text(text, style, link, inlines()));
            }
        });
    }

    /**
     * Runs {@code body} with {@code element} on the open-inline path, when it is
     * an inline element with something to paint or to reserve.
     */
    private void withInline(ComputedStyle style, Runnable body) {
        if (!InlineBox.decorates(style)) {
            body.run();
            return;
        }
        openInlines.add(new InlineBox(nextInlineId++, style, true));
        try {
            body.run();
        } finally {
            openInlines.remove(openInlines.size() - 1);
        }
    }

    /** The open-inline path as the runs emitted right now should record it. */
    private List<InlineBox> inlines() {
        return List.copyOf(openInlines);
    }

    private void appendElement(Element element, Content content, String link) {
        ComputedStyle style = cascade.styleOf(element);
        Display display = style.display();
        if (display == Display.NONE) {
            return;
        }

        String tag = element.normalName();
        if (tag.equals("br")) {
            content.breakLine(style);
            return;
        }
        String href = tag.equals("a") && element.hasAttr("href") ? element.attr("href") : link;

        if (tag.equals("img")) {
            ImageBox image = imageBox(element, style);
            if (display.isBlockLevel() || isFloated(style)) {
                content.block(image);
            } else {
                content.inline(InlineRun.image(image, style, href, inlines()));
            }
            return;
        }

        switch (display) {
            case TABLE -> content.block(tableBox(element, style));
            case INLINE -> {
                if (blockifies(cascade.styleOf(parentOf(element)))) {
                    // A flex or grid container's inline child is an item in its
                    // own right, not part of a line.
                    content.block(blockBox(element, style, href, null));
                } else {
                    withInline(style, () -> appendChildren(element, content, href));
                }
            }
            case INLINE_BLOCK, INLINE_FLEX, INLINE_GRID -> content.inline(InlineRun.inlineBlock(
                    blockBox(element, style, href, null), style, href, inlines()));
            default -> content.block(blockBox(element, style, href, markerOf(element, style)));
        }
    }

    private BlockBox blockBox(Element element, ComputedStyle style, String link, String marker) {
        // A block box inside an inline one begins its own lines, and an inline
        // box has no say over them, so the path does not follow it down.
        List<InlineBox> outer = List.copyOf(openInlines);
        openInlines.clear();
        try {
            return new BlockBox(style, element.normalName(), marker, anchorOf(element),
                    childrenOf(element, link));
        } finally {
            openInlines.addAll(outer);
        }
    }

    /** An element's parent element, or itself at the root, so a style is always available. */
    private static Element parentOf(Element element) {
        return element.parent() != null ? element.parent() : element;
    }

    private static String anchorOf(Element element) {
        String id = element.id();
        return id.isEmpty() ? null : id;
    }

    private static boolean isFloated(ComputedStyle style) {
        String value = style.raw("float", "none");
        return value.equalsIgnoreCase("left") || value.equalsIgnoreCase("right");
    }

    // --- Text ---------------------------------------------------------------

    /**
     * Collapses a text node's whitespace unless {@code white-space} preserves it.
     * A space that has already been emitted is not emitted again, which is what
     * makes {@code <em>a</em> <em>b</em>} one space rather than one per node.
     */
    private void appendText(String raw, ComputedStyle style, Content content, String link) {
        if (raw.isEmpty()) {
            return;
        }
        if (preservesWhitespace(style)) {
            appendPreformatted(raw, style, content, link);
            return;
        }
        String collapsed = collapseWhitespace(raw);
        if (collapsed.isEmpty()) {
            return;
        }
        if (collapsed.startsWith(" ")
                && (content.atLineStart() || content.lastText().endsWith(" "))) {
            collapsed = collapsed.substring(1);
        }
        if (collapsed.isEmpty()) {
            return;
        }
        content.inline(InlineRun.text(transform(collapsed, style), style, link, inlines()));
    }

    /** Preformatted text keeps its spaces, and each newline is a forced break. */
    private void appendPreformatted(String raw, ComputedStyle style, Content content, String link) {
        String[] lines = raw.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                content.breakLine(style);
            }
            if (!lines[i].isEmpty()) {
                content.inline(InlineRun.text(transform(lines[i], style), style, link, inlines()));
            }
        }
    }

    /**
     * Applies {@code text-transform}. The transform happens here rather than at
     * paint time so that the transformed text is what gets measured, wrapped and
     * put in the PDF's {@code /ToUnicode} map — a reader copying the text out
     * gets what was rendered.
     */
    private static String transform(String text, ComputedStyle style) {
        return switch (style.raw("text-transform", "none").trim().toLowerCase(Locale.ROOT)) {
            case "uppercase" -> text.toUpperCase(Locale.ROOT);
            case "lowercase" -> text.toLowerCase(Locale.ROOT);
            case "capitalize" -> capitalize(text);
            default -> text;
        };
    }

    /** Upper-cases the first letter of each word, leaving the rest as the author wrote it. */
    private static String capitalize(String text) {
        StringBuilder capitalized = new StringBuilder(text);
        boolean atWordStart = true;
        for (int i = 0; i < capitalized.length(); i++) {
            char c = capitalized.charAt(i);
            if (atWordStart && Character.isLetter(c)) {
                capitalized.setCharAt(i, Character.toUpperCase(c));
            }
            atWordStart = !Character.isLetterOrDigit(c);
        }
        return capitalized.toString();
    }

    private static boolean preservesWhitespace(ComputedStyle style) {
        String value = style.raw("white-space", "normal").trim().toLowerCase(Locale.ROOT);
        return value.equals("pre") || value.equals("pre-wrap") || value.equals("break-spaces");
    }

    /** Every run of whitespace becomes one space, as CSS {@code white-space: normal} requires. */
    static String collapseWhitespace(String text) {
        StringBuilder collapsed = new StringBuilder(text.length());
        boolean inWhitespace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
                if (!inWhitespace) {
                    collapsed.append(' ');
                    inWhitespace = true;
                }
            } else {
                collapsed.append(c);
                inWhitespace = false;
            }
        }
        return collapsed.toString();
    }

    private static void trimTrailingSpace(List<InlineRun> runs) {
        for (int i = runs.size() - 1; i >= 0; i--) {
            InlineRun run = runs.get(i);
            if (!run.isText()) {
                return;
            }
            if (run.text().isEmpty()) {
                continue;
            }
            if (run.text().endsWith(" ")) {
                runs.set(i, InlineRun.text(
                        run.text().stripTrailing(), run.style(), run.link(), run.inlines()));
            }
            return;
        }
    }

    // --- List markers -------------------------------------------------------

    /**
     * The marker text for a list item: its ordinal in the list for an ordered
     * one, a bullet for an unordered one.
     */
    private static String markerOf(Element element, ComputedStyle style) {
        if (style.display() != Display.LIST_ITEM) {
            return null;
        }
        String type = style.raw("list-style-type", defaultMarkerType(element)).trim().toLowerCase(Locale.ROOT);
        if (type.equals("none")) {
            return null;
        }
        return switch (type) {
            case "disc" -> "•";
            case "circle" -> "◦";
            case "square" -> "▪";
            case "decimal-leading-zero" -> String.format(Locale.ROOT, "%02d.", ordinalOf(element));
            case "lower-alpha", "lower-latin" -> alphabetic(ordinalOf(element), 'a') + ".";
            case "upper-alpha", "upper-latin" -> alphabetic(ordinalOf(element), 'A') + ".";
            case "lower-roman" -> roman(ordinalOf(element)).toLowerCase(Locale.ROOT) + ".";
            case "upper-roman" -> roman(ordinalOf(element)) + ".";
            default -> ordinalOf(element) + ".";
        };
    }

    private static String defaultMarkerType(Element element) {
        Element parent = element.parent();
        return parent != null && parent.normalName().equals("ol") ? "decimal" : "disc";
    }

    /** Honours the {@code start} attribute on {@code <ol>} and {@code value} on {@code <li>}. */
    private static int ordinalOf(Element element) {
        int value = intAttribute(element, "value", Integer.MIN_VALUE);
        if (value != Integer.MIN_VALUE) {
            return value;
        }
        Element parent = element.parent();
        int ordinal = parent != null ? intAttribute(parent, "start", 1) : 1;
        if (parent == null) {
            return ordinal;
        }
        for (Element sibling : parent.children()) {
            if (sibling == element) {
                return ordinal;
            }
            if (sibling.normalName().equals("li")) {
                int explicit = intAttribute(sibling, "value", Integer.MIN_VALUE);
                ordinal = explicit != Integer.MIN_VALUE ? explicit + 1 : ordinal + 1;
            }
        }
        return ordinal;
    }

    private static int intAttribute(Element element, String name, int fallback) {
        try {
            return element.hasAttr(name) ? Integer.parseInt(element.attr(name).trim()) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String alphabetic(int ordinal, char base) {
        if (ordinal < 1) {
            return String.valueOf(ordinal);
        }
        StringBuilder text = new StringBuilder();
        int n = ordinal;
        while (n > 0) {
            int digit = (n - 1) % 26;
            text.insert(0, (char) (base + digit));
            n = (n - 1) / 26;
        }
        return text.toString();
    }

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_NUMERALS =
            {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private static String roman(int ordinal) {
        if (ordinal < 1 || ordinal > 3999) {
            return String.valueOf(ordinal);
        }
        StringBuilder numeral = new StringBuilder();
        int remaining = ordinal;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (remaining >= ROMAN_VALUES[i]) {
                numeral.append(ROMAN_NUMERALS[i]);
                remaining -= ROMAN_VALUES[i];
            }
        }
        return numeral.toString();
    }

    // --- Images and tables --------------------------------------------------

    private static ImageBox imageBox(Element element, ComputedStyle style) {
        return new ImageBox(
                element.attr("src"),
                style,
                floatAttribute(element, "width"),
                floatAttribute(element, "height"),
                element.attr("alt"));
    }

    private static Float floatAttribute(Element element, String name) {
        try {
            String value = element.attr(name).trim().replace("px", "");
            return value.isEmpty() ? null : Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Flattens a {@code <table>} into rows tagged with their section, so header
     * rows can be repeated on later Pages without re-walking the DOM.
     */
    private TableBox tableBox(Element table, ComputedStyle style) {
        List<TableRow> rows = new ArrayList<>();
        collectRows(table, TableRow.Section.BODY, rows);
        List<Float> columns = new ArrayList<>();
        for (Element col : table.select("col")) {
            Float width = floatAttribute(col, "width");
            columns.add(width != null ? width : 0f);
        }
        if (columns.stream().allMatch(width -> width == 0f)) {
            columns.clear();
        }
        return new TableBox(style, rows, columns, anchorOf(table));
    }

    private void collectRows(Element element, TableRow.Section section, List<TableRow> rows) {
        for (Element child : element.children()) {
            ComputedStyle style = cascade.styleOf(child);
            switch (style.display()) {
                case NONE -> { }
                case TABLE_ROW -> rows.add(tableRow(child, section, style));
                case TABLE_HEADER_GROUP -> collectRows(child, TableRow.Section.HEADER, rows);
                case TABLE_FOOTER_GROUP -> collectRows(child, TableRow.Section.FOOTER, rows);
                case TABLE_ROW_GROUP -> collectRows(child, TableRow.Section.BODY, rows);
                default -> collectRows(child, section, rows);
            }
        }
    }

    private TableRow tableRow(Element row, TableRow.Section section, ComputedStyle style) {
        List<TableCell> cells = new ArrayList<>();
        for (Element child : row.children()) {
            ComputedStyle cellStyle = cascade.styleOf(child);
            if (cellStyle.display() == Display.NONE) {
                continue;
            }
            cells.add(new TableCell(
                    blockBox(child, cellStyle, null, null),
                    intAttribute(child, "colspan", 1),
                    intAttribute(child, "rowspan", 1),
                    child.normalName().equals("th")));
        }
        return new TableRow(section, style, cells);
    }
}
