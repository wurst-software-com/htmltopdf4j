package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.paint.Rect;

/**
 * The clickable rectangle of one laid-out piece of a link, in Page space.
 *
 * @param link 1-based index into the render context's interned link targets
 */
public record LinkArea(Rect rect, int link) {}
