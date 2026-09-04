package com.wurstsoftware.htmltopdf4j;

/**
 * Base of the engine's error hierarchy. Unchecked: a render either produces PDF
 * bytes or fails, and there is nothing a caller can meaningfully recover from
 * halfway through, so Rust's {@code Result} becomes an exception rather than a
 * carrier type threaded through every signature.
 */
public class RenderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RenderException(String message) {
        super(message);
    }

    public RenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
