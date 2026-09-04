package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wurstsoftware.htmltopdf4j.PageSize;
import org.junit.jupiter.api.Test;

/**
 * What an {@code @page} rule decides: the Page size its {@code size} declaration
 * names, and the margins it declares — but only where the rule was selected.
 *
 * <p>A rule with no selector applies to every Page. A named one applies only
 * where the Document asks for it by name with the {@code page} property, so a
 * stylesheet that carries a rule for a page nobody uses renders as though the
 * rule were not there.
 */
class PageRuleTest {

    private static PageSize pageSizeOf(String head) {
        return Laid.of("<html><head><style>" + head + "</style></head><body><p>TEXT</p></body></html>")
                .pageSize();
    }

    private static Laid laid(String head, String body) {
        return Laid.of("<html><head><style>" + head + "</style></head><body>" + body + "</body></html>");
    }

    @Test
    void anOrientationKeywordTurnsThePageOver() {
        assertEquals(new PageSize(PageSize.A4.height(), PageSize.A4.width()), pageSizeOf("@page { size: landscape }"));
    }

    @Test
    void aPortraitKeywordLeavesAPortraitPageAlone() {
        assertEquals(PageSize.A4, pageSizeOf("@page { size: portrait }"));
    }

    @Test
    void aPaperNameChoosesThatPaper() {
        PageSize a5 = pageSizeOf("@page { size: A5 }");
        assertEquals(420f, a5.width(), 1f);
        assertEquals(595f, a5.height(), 1f);
    }

    @Test
    void aPaperNameAndAnOrientationBothCount() {
        PageSize a5 = pageSizeOf("@page { size: A5 landscape }");
        assertEquals(595f, a5.width(), 1f);
        assertEquals(420f, a5.height(), 1f);
    }

    @Test
    void theOrientationMayComeFirst() {
        PageSize a5 = pageSizeOf("@page { size: landscape A5 }");
        assertEquals(595f, a5.width(), 1f);
        assertEquals(420f, a5.height(), 1f);
    }

    @Test
    void twoLengthsAreThePageSizeItself() {
        PageSize size = pageSizeOf("@page { size: 200mm 100mm }");
        assertEquals(566.9f, size.width(), 0.5f);
        assertEquals(283.5f, size.height(), 0.5f);
    }

    @Test
    void oneLengthIsASquarePage() {
        PageSize size = pageSizeOf("@page { size: 300pt }");
        assertEquals(new PageSize(300f, 300f), size);
    }

    @Test
    void aSizeOfAutoIsTheCallersOwn() {
        assertEquals(PageSize.A4, pageSizeOf("@page { size: auto }"));
    }

    @Test
    void noSizeDeclarationLeavesTheCallersPageSize() {
        assertEquals(PageSize.A4, pageSizeOf("@page { margin: 20pt }"));
    }

    @Test
    void aNamedRuleNothingSelectsChangesNothing() {
        float indented = laid("@page nobody { margin-left: 200pt }", "<p>TEXT</p>").text("TEXT").x();
        float plain = laid("", "<p>TEXT</p>").text("TEXT").x();
        assertEquals(plain, indented, 0.01f);
    }

    @Test
    void aNamedRuleNothingSelectsDoesNotChooseThePageSize() {
        assertEquals(PageSize.A4, pageSizeOf("@page nobody { size: landscape }"));
    }

    @Test
    void aNamedRuleTheDocumentAsksForApplies() {
        Laid laid = laid("@page wide { size: landscape; margin-left: 200pt }",
                "<div style='page:wide'><p>TEXT</p></div>");
        assertEquals(842f, laid.pageSize().width(), 1f);
        assertEquals(200f, laid.text("TEXT").x(), 0.01f);
    }

    @Test
    void aPageNamedOnTheBodyAppliesToTheDocument() {
        Laid laid = Laid.of("<html><head><style>@page wide { size: landscape }</style></head>"
                + "<body style='page:wide'><p>TEXT</p></body></html>");

        assertEquals(842f, laid.pageSize().width(), 1f,
                "`page` is inherited, so naming it on the body names it for everything in it");
    }

    @Test
    void aDocumentThatNamesTwoPagesKeepsTheCallersPageSize() {
        // One Page size is chosen for the whole render, so two named pages have no
        // answer: neither rule applies, rather than half the Document being
        // rendered on the wrong paper.
        Laid laid = laid("@page a { size: A5 } @page b { size: landscape; margin-left: 200pt }",
                "<div style='page:a'><p>ONE</p></div><div style='page:b'><p>TWO</p></div>");

        assertEquals(PageSize.A4, laid.pageSize());
        assertEquals(48f, laid.text("ONE").x(), 0.01f, "and neither rule's margins apply either");
    }

    @Test
    void twoApplicableRulesCascadePerDeclaration() {
        Laid laid = laid("@page { size: A5; margin-left: 100pt } @page { margin-left: 150pt }", "<p>TEXT</p>");
        assertEquals(420f, laid.pageSize().width(), 1f);
        assertEquals(150f, laid.text("TEXT").x(), 0.01f);
    }
}
