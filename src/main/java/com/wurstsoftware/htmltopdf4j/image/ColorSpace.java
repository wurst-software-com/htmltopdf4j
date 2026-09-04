package com.wurstsoftware.htmltopdf4j.image;

/** The colour spaces the writer can embed samples in. */
public enum ColorSpace {
    DEVICE_RGB("DeviceRGB"),
    DEVICE_GRAY("DeviceGray");

    private final String pdfName;

    ColorSpace(String pdfName) {
        this.pdfName = pdfName;
    }

    public String pdfName() {
        return pdfName;
    }
}
