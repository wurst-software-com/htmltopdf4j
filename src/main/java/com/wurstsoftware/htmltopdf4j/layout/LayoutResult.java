package com.wurstsoftware.htmltopdf4j.layout;

import com.wurstsoftware.htmltopdf4j.render.RenderContext;
import java.util.List;

/**
 * What Layout produces: the Pages, and the context the PDF writer needs to
 * resolve the indices the Paint commands carry.
 */
public record LayoutResult(List<Page> pages, RenderContext context) {

    public LayoutResult {
        pages = List.copyOf(pages);
    }
}
