package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.RenderOptions;
import com.wurstsoftware.htmltopdf4j.box.BoxTree;
import com.wurstsoftware.htmltopdf4j.box.BoxTreeBuilder;
import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import com.wurstsoftware.htmltopdf4j.style.Cascade;
import com.wurstsoftware.htmltopdf4j.style.Stylesheet;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * An HTML string taken as far as the Pages, so a test can assert on the Display
 * list rather than on PDF bytes.
 *
 * <p>Stopping short of the writer keeps a layout test reading in layout terms —
 * "this text sits at this y" — instead of grepping a content stream, and it
 * still exercises every stage a Fixture does bar the last one.
 */
final class Laid {

    private final List<Page> pages;

    private Laid(List<Page> pages) {
        this.pages = pages;
    }

    static Laid of(String html) {
        return of(html, RenderOptions.defaults());
    }

    static Laid of(String html, RenderOptions options) {
        Document document = Jsoup.parse(html);
        Stylesheet stylesheet = Cascade.authorStylesheet(document);
        BoxTree tree = BoxTreeBuilder.build(document, Cascade.apply(document, stylesheet));
        return new Laid(Layout.layout(tree, stylesheet, options).pages());
    }

    List<Page> pages() {
        return pages;
    }

    int pageCount() {
        return pages.size();
    }

    /** Every command on every Page, in paint order. */
    List<PaintCommand> commands() {
        return pages.stream().flatMap(page -> page.commands().stream()).toList();
    }

    List<PaintCommand> commands(int page) {
        return pages.get(page).commands();
    }

    List<PaintCommand.Text> texts() {
        return commands().stream().filter(PaintCommand.Text.class::isInstance)
                .map(PaintCommand.Text.class::cast).toList();
    }

    List<PaintCommand.Text> texts(int page) {
        return commands(page).stream().filter(PaintCommand.Text.class::isInstance)
                .map(PaintCommand.Text.class::cast).toList();
    }

    List<Rect> fills() {
        return commands().stream().filter(PaintCommand.FillRect.class::isInstance)
                .map(command -> ((PaintCommand.FillRect) command).rect()).toList();
    }

    /**
     * The visual lines, in paint order.
     *
     * <p>A line is drawn as one Text command per word, so a test that wants to
     * count lines has to group the runs by the baseline they share rather than
     * count commands.
     */
    List<Line> lines() {
        List<Line> lines = new java.util.ArrayList<>();
        for (int page = 0; page < pages.size(); page++) {
            for (PaintCommand.Text run : texts(page)) {
                Line last = lines.isEmpty() ? null : lines.get(lines.size() - 1);
                if (last != null && last.page == page && Math.abs(last.y - run.y()) < 0.01f) {
                    last.runs.add(run);
                } else {
                    Line line = new Line(page, run.y());
                    line.runs.add(run);
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /** One baseline's worth of runs. */
    static final class Line {
        final int page;
        final float y;
        final List<PaintCommand.Text> runs = new java.util.ArrayList<>();

        Line(int page, float y) {
            this.page = page;
            this.y = y;
        }

        /** Where the line starts, which is what a float beside it changes. */
        float x() {
            return runs.get(0).x();
        }

        String text() {
            return runs.stream().map(PaintCommand.Text::text).reduce("", String::concat);
        }
    }

    /** The first drawn run whose text contains {@code needle}. */
    PaintCommand.Text text(String needle) {
        return texts().stream()
                .filter(text -> text.text().contains(needle))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no run containing `" + needle + "`; drawn: " + drawnText()));
    }

    boolean draws(String needle) {
        return texts().stream().anyMatch(text -> text.text().contains(needle));
    }

    /** Everything drawn, for a failure message that says what did come out. */
    List<String> drawnText() {
        return texts().stream().map(PaintCommand.Text::text).toList();
    }

    /** The Page a run containing {@code needle} landed on. */
    int pageOf(String needle) {
        for (int i = 0; i < pages.size(); i++) {
            if (texts(i).stream().anyMatch(text -> text.text().contains(needle))) {
                return i;
            }
        }
        throw new AssertionError("no run containing `" + needle + "`; drawn: " + drawnText());
    }
}
