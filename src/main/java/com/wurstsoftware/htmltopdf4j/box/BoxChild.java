package com.wurstsoftware.htmltopdf4j.box;

/**
 * One child of a block box's content.
 *
 * <p>Sealed so Layout's switches over it are total: a new kind of box is a
 * compile error in every place that lays boxes out, rather than a silently
 * unhandled case that renders as nothing.
 */
public sealed interface BoxChild permits BlockBox, LineBox, ImageBox, TableBox {

    /** Whether this child would put anything visible on a Page. */
    boolean hasContent();
}
