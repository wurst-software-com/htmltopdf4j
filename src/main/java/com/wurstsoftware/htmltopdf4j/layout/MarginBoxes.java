package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.PageSize;
import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.render.FaceRegistry;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.style.ContentValue;
import com.wurstsoftware.htmltopdf4j.style.Shorthands;
import com.wurstsoftware.htmltopdf4j.style.TextAlign;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The running headers and footers of an {@code @page} rule.
 *
 * <p>These are painted after the body has been flowed, not during it, because
 * {@code counter(pages)} is the total Page count and nothing knows that until
 * pagination has finished. That ordering is the whole reason margin boxes are a
 * separate pass rather than part of starting a Page.
 */
final class MarginBoxes {

    /** How far a margin box's text sits from the content edge, in points. */
    private static final float OFFSET = 12f;

    private MarginBoxes() {}

    static void paint(
            List<Page> pages,
            Map<String, List<com.wurstsoftware.htmltopdf4j.style.Declaration>> boxes,
            PageSize pageSize,
            Edges margins,
            FaceRegistry faces) {

        if (boxes.isEmpty()) {
            return;
        }
        float contentLeft = margins.left();
        float contentRight = pageSize.width() - margins.right();

        for (int index = 0; index < pages.size(); index++) {
            int number = index + 1;
            for (Map.Entry<String, List<com.wurstsoftware.htmltopdf4j.style.Declaration>> box : boxes.entrySet()) {
                ComputedStyle style = styleOf(box.getValue());
                String text = ContentValue.of(
                        style.raw("content"),
                        ContentValue.NONE,
                        counter -> switch (counter.toLowerCase(Locale.ROOT)) {
                            case "page" -> String.valueOf(number);
                            case "pages" -> String.valueOf(pages.size());
                            default -> null;
                        });
                if (text.isEmpty()) {
                    continue;
                }
                paintOne(pages.get(index), box.getKey(), text, style, faces,
                        pageSize, margins, contentLeft, contentRight);
            }
        }
    }

    private static void paintOne(
            Page page,
            String name,
            String text,
            ComputedStyle style,
            FaceRegistry faces,
            PageSize pageSize,
            Edges margins,
            float contentLeft,
            float contentRight) {

        int face = faces.indexFor(style);
        FaceChain chain = faces.chain(face);
        float size = style.fontSize();
        float width = chain.measure(text, size);

        String position = name.toLowerCase(Locale.ROOT);
        boolean atTop = position.startsWith("top");
        float baseline = atTop
                ? pageSize.height() - margins.top() + OFFSET
                : margins.bottom() - OFFSET;

        float x = switch (alignmentOf(position)) {
            case RIGHT -> contentRight - width;
            case CENTER -> contentLeft + (contentRight - contentLeft - width) / 2f;
            case LEFT, JUSTIFY -> contentLeft;
        };
        page.add(new PaintCommand.SetFillColor(style.color()));
        page.add(new PaintCommand.Text(text, x, baseline, size, face, faces.syntheticBold(face), 0f));
    }

    /**
     * Where a margin box's text sits along its edge. The corner boxes
     * ({@code top-left-corner} and friends) sit outside the content edge
     * entirely; treating them as their nearest edge box is close enough to be
     * useful and far better than dropping them.
     */
    private static TextAlign alignmentOf(String name) {
        if (name.contains("right")) {
            return TextAlign.RIGHT;
        }
        if (name.contains("center") || name.contains("middle")) {
            return TextAlign.CENTER;
        }
        return TextAlign.LEFT;
    }

    private static ComputedStyle styleOf(List<com.wurstsoftware.htmltopdf4j.style.Declaration> declarations) {
        Map<String, String> declared = new HashMap<>();
        declarations.forEach(declaration ->
                Shorthands.expand(declaration.property(), declaration.value()).forEach(declared::put));
        return ComputedStyle.of(declared);
    }
}
