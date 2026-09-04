package com.wurstsoftware.htmltopdf4j.box;

import java.util.List;

/**
 * A run of inline content between two forced breaks.
 *
 * <p>This is not a visual line: it is the inline content Layout will wrap into
 * as many visual lines as the containing width requires. A {@code <br>} ends one
 * {@code LineBox} and starts the next, which is what makes a forced break
 * different from a wrap.
 */
public record LineBox(List<InlineRun> runs) implements BoxChild {

    public LineBox {
        runs = List.copyOf(runs);
    }

    @Override
    public boolean hasContent() {
        return runs.stream().anyMatch(InlineRun::hasContent);
    }
}
