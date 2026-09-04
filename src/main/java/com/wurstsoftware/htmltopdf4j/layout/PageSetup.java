package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.PageSize;
import com.wurstsoftware.htmltopdf4j.RenderOptions;
import com.wurstsoftware.htmltopdf4j.box.BlockBox;
import com.wurstsoftware.htmltopdf4j.box.BoxChild;
import com.wurstsoftware.htmltopdf4j.box.BoxTree;
import com.wurstsoftware.htmltopdf4j.box.ImageBox;
import com.wurstsoftware.htmltopdf4j.box.LineBox;
import com.wurstsoftware.htmltopdf4j.box.TableBox;
import com.wurstsoftware.htmltopdf4j.box.TableCell;
import com.wurstsoftware.htmltopdf4j.box.TableRow;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.style.Declaration;
import com.wurstsoftware.htmltopdf4j.style.Length;
import com.wurstsoftware.htmltopdf4j.style.Shorthands;
import com.wurstsoftware.htmltopdf4j.style.Stylesheet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The sheet a Document is laid out on: its size, its margins, and the margin
 * boxes that carry the page furniture.
 *
 * <p>All three come from the {@code @page} rules the Document selected, falling
 * back to what the caller asked for in {@link RenderOptions}. A rule with no
 * selector applies to every Page; a named one applies only where a Box asks for
 * it by name with the {@code page} property, so a stylesheet carrying a rule
 * nobody selected renders as though the rule were not there.
 *
 * <p>Only the Document-wide case is handled: {@code page} decides one sheet for
 * the whole render rather than switching sheet mid-flow, and the {@code :first},
 * {@code :left} and {@code :right} pseudo-classes are ignored, so a rule that
 * carries one applies to every Page as though it had none.
 */
record PageSetup(PageSize size, Edges margins, Map<String, List<Declaration>> marginBoxes) {

    /** The papers {@code size} may name, portrait, in points. */
    private static final Map<String, PageSize> PAPERS = Map.of(
            "a3", new PageSize(841.89f, 1190.55f),
            "a4", PageSize.A4,
            "a5", new PageSize(419.53f, 595.28f),
            "letter", PageSize.LETTER,
            "legal", new PageSize(612f, 1008f));

    static PageSetup of(Stylesheet stylesheet, RenderOptions options, BoxTree tree) {
        Set<String> selected = selectedPages(tree);
        // One map for the declarations of every applicable rule, so a property
        // declared twice takes its later value rather than its whole rule
        // replacing the earlier one.
        Map<String, String> declared = new LinkedHashMap<>();
        Map<String, List<Declaration>> marginBoxes = new LinkedHashMap<>();
        for (Stylesheet.PageRule rule : stylesheet.pageRules()) {
            if (!applies(rule, selected)) {
                continue;
            }
            rule.declarations().forEach(declaration ->
                    Shorthands.expand(declaration.property(), declaration.value()).forEach(declared::put));
            marginBoxes.putAll(rule.marginBoxes());
        }
        ComputedStyle style = ComputedStyle.of(declared);
        Edges margins = new Edges(
                margin(style, "margin-top", options.marginTop()),
                margin(style, "margin-right", options.marginRight()),
                margin(style, "margin-bottom", options.marginBottom()),
                margin(style, "margin-left", options.marginLeft()));
        return new PageSetup(size(style, options.pageSize()), margins, marginBoxes);
    }

    private static float margin(ComputedStyle style, String property, float fallback) {
        return style.length(property).map(length -> style.resolve(length, 0f)).orElse(fallback);
    }

    /**
     * The sheet a {@code size} declaration names: a paper, an orientation, the
     * two together in either order, or the dimensions themselves. Anything else
     * — {@code auto} included — leaves the caller's sheet alone.
     */
    private static PageSize size(ComputedStyle style, PageSize fallback) {
        String declared = style.raw("size");
        if (declared == null || declared.isBlank()) {
            return fallback;
        }
        PageSize paper = null;
        Boolean landscape = null;
        List<Float> lengths = new ArrayList<>();
        for (String token : declared.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (PAPERS.containsKey(token)) {
                paper = PAPERS.get(token);
            } else if (token.equals("landscape") || token.equals("portrait")) {
                landscape = token.equals("landscape");
            } else {
                Length.parse(token)
                        .filter(length -> !length.isRelativeToContainer())
                        .ifPresent(length -> lengths.add(style.resolve(length, 0f)));
            }
        }
        if (!lengths.isEmpty()) {
            // Dimensions say which way round the sheet goes themselves, so an
            // orientation keyword beside them has nothing left to decide.
            float width = lengths.get(0);
            return new PageSize(width, lengths.size() > 1 ? lengths.get(1) : width);
        }
        PageSize sheet = paper != null ? paper : fallback;
        if (landscape == null) {
            return sheet;
        }
        return landscape ? sheet.landscape() : sheet.portrait();
    }

    /**
     * Whether a rule was selected: either it names no page, or the Document
     * asked for the page it names.
     */
    private static boolean applies(Stylesheet.PageRule rule, Set<String> selected) {
        String name = pageName(rule.selector());
        return name.isEmpty() || selected.contains(name);
    }

    /** A {@code @page} selector reduced to the page it names, with any pseudo-class dropped. */
    private static String pageName(String selector) {
        String name = selector == null ? "" : selector.trim();
        int pseudo = name.indexOf(':');
        if (pseudo >= 0) {
            name = name.substring(0, pseudo);
        }
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * The page names the Document asks for anywhere in its Box tree.
     *
     * <p>The whole tree is searched rather than the root alone because the
     * {@code page} property is usually put on the element that wants the sheet —
     * the wide table, not the Document. Switching sheet mid-flow is out of scope,
     * so a Document that names two pages simply selects both rules.
     */
    private static Set<String> selectedPages(BoxTree tree) {
        Set<String> names = new LinkedHashSet<>();
        tree.children().forEach(child -> collectPages(child, names));
        return names;
    }

    private static void collectPages(BoxChild child, Set<String> names) {
        switch (child) {
            case BlockBox block -> {
                declaredPage(block.style(), names);
                block.children().forEach(nested -> collectPages(nested, names));
            }
            case ImageBox image -> declaredPage(image.style(), names);
            case TableBox table -> {
                declaredPage(table.style(), names);
                for (TableRow row : table.rows()) {
                    declaredPage(row.style(), names);
                    for (TableCell cell : row.cells()) {
                        collectPages(cell.content(), names);
                    }
                }
            }
            // A line of text sits on whatever sheet its block chose; `page` on a
            // run of inline content selects nothing.
            case LineBox ignored -> { }
        }
    }

    private static void declaredPage(ComputedStyle style, Set<String> names) {
        String page = style.raw("page");
        if (page != null && !page.isBlank() && !page.trim().equalsIgnoreCase("auto")) {
            names.add(page.trim().toLowerCase(Locale.ROOT));
        }
    }
}
