package com.wurstsoftware.htmltopdf4j.image;

/**
 * A raster image ready to be embedded as a PDF image XObject.
 *
 * @param data the bytes that go into the stream: the original JPEG for
 *     {@link ImageFilter#DCT}, decoded samples for {@link ImageFilter#FLATE}
 * @param softMask one 8-bit alpha sample per pixel, or {@code null} when the
 *     image is fully opaque; always Flate-filtered
 */
public record DecodedImage(
        int width,
        int height,
        ColorSpace colorSpace,
        int bitsPerComponent,
        ImageFilter filter,
        byte[] data,
        byte[] softMask) {

    public boolean hasSoftMask() {
        return softMask != null;
    }
}
