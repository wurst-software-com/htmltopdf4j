package com.wurstsoftware.htmltopdf4j.box;

import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import java.util.List;

/**
 * A block-level box: it stacks vertically among its siblings and establishes a
 * containing block for its children.
 *
 * <p>Every geometric property — margins, padding, borders, width, flex and grid
 * parameters, position offsets — is read from {@link #style()} when Layout needs
 * it, rather than being copied into fields here. The Cascade has already
 * resolved those values; duplicating them into the Box tree would mean a second
 * place for them to be wrong, and would make this record a hundred fields wide.
 *
 * @param marker the list marker to draw before the first line, or {@code null}
 *     when this box is not a list item
 * @param anchor the element's HTML {@code id}, kept as a destination for
 *     {@code #fragment} links, or {@code null}
 */
public record BlockBox(
        ComputedStyle style,
        String tag,
        String marker,
        String anchor,
        List<BoxChild> children)
        implements BoxChild {

    public BlockBox {
        children = List.copyOf(children);
    }

    /** A block with no styling of its own, wrapping inline content that needed a block parent. */
    public static BlockBox anonymous(ComputedStyle style, List<BoxChild> children) {
        return new BlockBox(style, "", null, null, children);
    }

    @Override
    public boolean hasContent() {
        return marker != null || children.stream().anyMatch(BoxChild::hasContent);
    }
}
