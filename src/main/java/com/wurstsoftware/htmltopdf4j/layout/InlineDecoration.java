package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.box.InlineBox;
import com.wurstsoftware.htmltopdf4j.box.InlineRun;
import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import com.wurstsoftware.htmltopdf4j.paint.RoundedRect;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The background and borders of the inline boxes a line of text sits inside.
 *
 * <p>An inline box is not one rectangle but one per line it occupies, and the
 * near edges belong to the first and the last of them: that is what makes a
 * wrapped chip read as one chip cut in two rather than as two chips. Everything
 * needed to draw them is on the line already — the fragments, where they were
 * placed, and the baseline they share — so this needs nothing of the block flow
 * that produced them.
 */
final class InlineDecoration {

    private final LineBreaker breaker;

    InlineDecoration(LineBreaker breaker) {
        this.breaker = breaker;
    }

    /**
     * Paints the boxes this line is inside, behind the text that is about to be
     * drawn.
     *
     * <p>Outer boxes are painted before the boxes nested in them, so a chip
     * inside a highlighted sentence sits on top of the highlight.
     *
     * @param origins where each fragment's own {@code x} is measured from
     * @param pdfBaseline the line's baseline, already in PDF page space
     * @param next the runs the line break left over, which say which boxes carry
     *     on past the end of this line and so do not close on it
     */
    void paint(
            Page page,
            List<LineBreaker.Fragment> fragments,
            float[] origins,
            float pdfBaseline,
            float containingWidth,
            List<InlineRun> next) {

        Set<Integer> continuing = new HashSet<>();
        for (InlineRun run : next) {
            run.inlines().forEach(box -> continuing.add(box.id()));
        }
        for (int depth = 0; depth < maxDepth(fragments); depth++) {
            int start = -1;
            for (int i = 0; i <= fragments.size(); i++) {
                InlineBox box = i < fragments.size() ? inlineAt(fragments.get(i), depth) : null;
                InlineBox open = start < 0 ? null : inlineAt(fragments.get(start), depth);
                if (start >= 0 && !open.sameBoxAs(box)) {
                    paintBox(page, open, fragments, origins, start, i - 1, pdfBaseline, containingWidth,
                            !continuing.contains(open.id()));
                    start = -1;
                }
                if (box != null && start < 0) {
                    start = i;
                }
            }
        }
    }

    private static int maxDepth(List<LineBreaker.Fragment> fragments) {
        int depth = 0;
        for (LineBreaker.Fragment fragment : fragments) {
            depth = Math.max(depth, fragment.run().inlines().size());
        }
        return depth;
    }

    private static InlineBox inlineAt(LineBreaker.Fragment fragment, int depth) {
        List<InlineBox> inlines = fragment.run().inlines();
        return depth < inlines.size() ? inlines.get(depth) : null;
    }

    /** One inline box's rectangle on one line, from its first fragment to its last. */
    private void paintBox(
            Page page,
            InlineBox box,
            List<LineBreaker.Fragment> fragments,
            float[] origins,
            int first,
            int last,
            float pdfBaseline,
            float containingWidth,
            boolean closesHere) {

        ComputedStyle style = box.style();
        Edges padding = Edges.padding(style, containingWidth);
        Edges border = Edges.borderWidths(style);
        float left = origins[first] + fragments.get(first).x()
                - (box.opensHere() ? padding.left() + border.left() : 0f);
        float right = origins[last] + fragments.get(last).x() + fragments.get(last).width()
                + (closesHere ? padding.right() + border.right() : 0f);

        // The box is as tall as everything it wraps on this line — its tallest
        // ascent above the baseline, its deepest descent below it, from the
        // Faces' own metrics — grown by the box's own padding and border.
        LineBreaker.Extent extent = breaker.extentOf(fragments.subList(first, last + 1));
        float bottom = pdfBaseline - extent.descent() - padding.bottom() - border.bottom();
        float height = extent.height() + padding.vertical() + border.vertical();
        Rect rect = new Rect(left, bottom, Math.max(0f, right - left), height);

        // A rounded corner belongs to a whole box: a box cut by a line break is
        // drawn square, because the corners the break made are not corners.
        float radius = box.opensHere() && closesHere
                ? style.length("border-top-left-radius")
                        .map(length -> style.resolve(length, rect.width()))
                        .orElse(0f)
                : 0f;
        Edges sides = new Edges(
                border.top(),
                closesHere ? border.right() : 0f,
                border.bottom(),
                box.opensHere() ? border.left() : 0f);

        List<PaintCommand> decoration = new ArrayList<>();
        style.backgroundColor().ifPresent(color -> {
            decoration.add(new PaintCommand.SetFillColor(color));
            decoration.add(radius > 0f
                    ? new PaintCommand.FillRoundedRect(
                            new RoundedRect(rect.x(), rect.y(), rect.width(), rect.height(), radius))
                    : new PaintCommand.FillRect(rect));
        });
        if (sides.horizontal() + sides.vertical() > 0f) {
            Layout.paintBorders(decoration, style, rect, sides, radius);
        }
        decoration.forEach(page::add);
    }
}
