package com.wurstsoftware.htmltopdf4j.style;

import java.util.List;

/**
 * One selector of one rule, with everything the Cascade needs to order it.
 *
 * <p>A rule written with a selector list is split into one StyleRule per
 * selector, because each selector has its own specificity and only the ones that
 * match the element count.
 *
 * @param order the rule's position in source order, breaking ties between equal
 *     specificities
 */
public record StyleRule(String selector, Specificity specificity, int order, List<Declaration> declarations) {

    public StyleRule {
        declarations = List.copyOf(declarations);
    }
}
