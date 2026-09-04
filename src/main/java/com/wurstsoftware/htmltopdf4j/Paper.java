package com.wurstsoftware.htmltopdf4j;

/**
 * The base paper size to render on, before the Document's own {@code @page}
 * orientation is applied.
 */
public enum Paper {
    A4(PageSize.A4, PageSize.A4_LANDSCAPE),
    LETTER(PageSize.LETTER, PageSize.LETTER_LANDSCAPE);

    private final PageSize portrait;
    private final PageSize landscape;

    Paper(PageSize portrait, PageSize landscape) {
        this.portrait = portrait;
        this.landscape = landscape;
    }

    public PageSize portrait() {
        return portrait;
    }

    public PageSize landscape() {
        return landscape;
    }
}
