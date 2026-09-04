package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One sheet of output, holding the Display list that falls on it. Produced by
 * Layout, consumed by the writer.
 *
 * <p>Mutable while Layout fills it and effectively frozen afterwards; a Page is
 * never shared between renders, so it needs no synchronisation. The reference
 * engine also kept parallel {@code lines} and {@code rects} lists beside the
 * commands; they are not carried over, because the Display list is the only
 * thing the writer reads and a second representation could only drift from it.
 */
public final class Page {

    private final List<PaintCommand> commands = new ArrayList<>();
    private final List<LinkArea> linkAreas = new ArrayList<>();
    private final List<AnchorMark> anchors = new ArrayList<>();

    public void add(PaintCommand command) {
        commands.add(command);
    }

    /**
     * Adds a clickable area, joining it to the one before when they are two
     * pieces of the same link on the same line.
     *
     * <p>Each word is drawn as its own run, so a three-word link arrives here as
     * three areas. Left as they are, a reader would show three annotations over
     * one phrase and the gaps between the words would not be clickable.
     */
    public void addLink(LinkArea area) {
        LinkArea previous = linkAreas.isEmpty() ? null : linkAreas.get(linkAreas.size() - 1);
        if (previous != null && joins(previous, area)) {
            linkAreas.set(linkAreas.size() - 1, join(previous, area));
            return;
        }
        linkAreas.add(area);
    }

    private static boolean joins(LinkArea previous, LinkArea next) {
        Rect before = previous.rect();
        Rect after = next.rect();
        return previous.link() == next.link()
                && Math.abs(before.y() - after.y()) < 0.01f
                // Word runs are separated by the space between them, which is
                // well under half the line; a further-off run is a second line
                // or a second link and keeps its own rectangle.
                && after.x() >= before.x()
                && after.x() - (before.x() + before.width()) < before.height() * 0.5f;
    }

    private static LinkArea join(LinkArea previous, LinkArea next) {
        Rect before = previous.rect();
        Rect after = next.rect();
        float right = Math.max(before.x() + before.width(), after.x() + after.width());
        return new LinkArea(
                new Rect(before.x(), before.y(), right - before.x(),
                        Math.max(before.height(), after.height())),
                previous.link());
    }

    public void addAnchor(AnchorMark anchor) {
        anchors.add(anchor);
    }

    /**
     * A position in this Page's command list to come back to.
     *
     * <p>A block's background has to be painted before its content but is not
     * measured until after it, because its height is its content's height. Rather
     * than laying the content out twice, the painter marks the spot and inserts
     * the background there once the height is known.
     */
    public int mark() {
        return commands.size();
    }

    public void insert(int index, List<PaintCommand> inserted) {
        commands.addAll(index, inserted);
    }

    public List<PaintCommand> commands() {
        return Collections.unmodifiableList(commands);
    }

    public List<LinkArea> linkAreas() {
        return Collections.unmodifiableList(linkAreas);
    }

    public List<AnchorMark> anchors() {
        return Collections.unmodifiableList(anchors);
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }
}
