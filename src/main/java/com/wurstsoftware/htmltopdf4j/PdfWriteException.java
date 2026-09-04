package com.wurstsoftware.htmltopdf4j;

/** The Document laid out, but its PDF could not be written. */
public final class PdfWriteException extends RenderException {

    private static final long serialVersionUID = 1L;

    public PdfWriteException(String message) {
        super(message);
    }

    public PdfWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
