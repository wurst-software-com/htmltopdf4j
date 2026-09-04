package com.wurstsoftware.htmltopdf4j.image;

/** How an image's bytes are encoded in its PDF stream. */
public enum ImageFilter {
    /** Original JPEG bytes, passed through untouched. */
    DCT("DCTDecode"),
    /** Decoded samples, compressed by the writer. */
    FLATE("FlateDecode");

    private final String pdfName;

    ImageFilter(String pdfName) {
        this.pdfName = pdfName;
    }

    public String pdfName() {
        return pdfName;
    }
}
