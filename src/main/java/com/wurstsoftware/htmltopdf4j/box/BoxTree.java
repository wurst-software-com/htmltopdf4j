package com.wurstsoftware.htmltopdf4j.box;

import java.util.List;

/**
 * The root of a Document's Box tree: the top-level boxes, in document order.
 *
 * <p>The root contributes no spacing of its own — only its children do.
 */
public record BoxTree(List<BoxChild> children) {

    public static final BoxTree EMPTY = new BoxTree(List.of());

    public BoxTree {
        children = List.copyOf(children);
    }

    /** Whether the Document would put anything at all on a Page. */
    public boolean hasContent() {
        return children.stream().anyMatch(BoxChild::hasContent);
    }
}
