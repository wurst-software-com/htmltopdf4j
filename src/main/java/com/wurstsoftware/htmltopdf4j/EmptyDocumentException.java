package com.wurstsoftware.htmltopdf4j;

/**
 * The Document held nothing that would generate a box — no text, no images.
 * Distinct from {@link PdfWriteException} so a caller can tell bad input from a
 * rendering defect.
 */
public final class EmptyDocumentException extends RenderException {

    private static final long serialVersionUID = 1L;

    public EmptyDocumentException() {
        super("document does not contain renderable content");
    }
}
