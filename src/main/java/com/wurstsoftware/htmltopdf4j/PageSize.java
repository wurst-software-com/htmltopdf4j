package com.wurstsoftware.htmltopdf4j;

/** The dimensions of one sheet of output, in points. */
public record PageSize(float width, float height) {

    public static final PageSize A4 = new PageSize(595f, 842f);
    public static final PageSize A4_LANDSCAPE = new PageSize(842f, 595f);
    public static final PageSize LETTER = new PageSize(612f, 792f);
    public static final PageSize LETTER_LANDSCAPE = new PageSize(792f, 612f);

    public PageSize {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("page size must be positive, got " + width + "x" + height);
        }
    }

    public PageSize landscape() {
        return width >= height ? this : new PageSize(height, width);
    }

    public PageSize portrait() {
        return height >= width ? this : new PageSize(height, width);
    }
}
