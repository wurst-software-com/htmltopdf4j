package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
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

    public void addLink(LinkArea area) {
        linkAreas.add(area);
    }

    public void addAnchor(AnchorMark anchor) {
        anchors.add(anchor);
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
