package com.wurstsoftware.htmltopdf4j.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Turning an {@code <img>} source into bytes a PDF can carry.
 *
 * <p>A JPEG is passed through untouched: its DCT-compressed bytes are already a
 * legal PDF image stream, so re-encoding it would cost time and quality for
 * nothing. Everything else is decoded through ImageIO and re-encoded as raw
 * samples, which the PDF writer deflates. An image with transparency is split
 * into colour samples and an alpha soft mask, because PDF has no interleaved
 * alpha channel.
 *
 * <p>Only local sources are read — files under the render's base directory and
 * {@code data:} URIs. Fetching {@code http(s)} is out of scope: a library that
 * silently made network requests while rendering a Document would be a
 * surprising thing to embed in a server.
 */
public final class ImageLoader {

    private final Path baseDirectory;
    private final Map<String, Integer> bySource = new HashMap<>();
    private final java.util.List<DecodedImage> images = new java.util.ArrayList<>();

    public ImageLoader(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    /**
     * The index of a decoded image in the render's image table, decoding it on
     * first use, or empty when the source cannot be read.
     */
    public Optional<Integer> resolve(String source) {
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }
        Integer existing = bySource.get(source);
        if (existing != null) {
            return existing < 0 ? Optional.empty() : Optional.of(existing);
        }
        Optional<DecodedImage> decoded = decode(source);
        // A source that failed once is remembered as failed, so a Document that
        // references a missing image a hundred times tries to read it once.
        int index = decoded.map(image -> {
            images.add(image);
            return images.size() - 1;
        }).orElse(-1);
        bySource.put(source, index);
        return index < 0 ? Optional.empty() : Optional.of(index);
    }

    public java.util.List<DecodedImage> images() {
        return java.util.List.copyOf(images);
    }

    public DecodedImage image(int index) {
        return images.get(index);
    }

    private Optional<DecodedImage> decode(String source) {
        try {
            byte[] bytes = read(source);
            return bytes == null ? Optional.empty() : Optional.of(decodeBytes(bytes));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private byte[] read(String source) throws IOException {
        String trimmed = source.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("data:")) {
            return readDataUri(trimmed);
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("http://")
                || trimmed.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return null;
        }
        String path = trimmed.startsWith("file://") ? trimmed.substring("file://".length()) : trimmed;
        Path resolved = baseDirectory != null ? baseDirectory.resolve(path) : Path.of(path);
        return Files.isReadable(resolved) ? Files.readAllBytes(resolved) : null;
    }

    private static byte[] readDataUri(String uri) {
        int comma = uri.indexOf(',');
        if (comma < 0) {
            return null;
        }
        String header = uri.substring(0, comma);
        String payload = uri.substring(comma + 1);
        if (header.toLowerCase(Locale.ROOT).contains(";base64")) {
            return Base64.getMimeDecoder().decode(payload.replaceAll("\\s", ""));
        }
        return java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.ISO_8859_1);
    }

    static DecodedImage decodeBytes(byte[] bytes) throws IOException {
        if (isJpeg(bytes)) {
            BufferedImage probe = ImageIO.read(new ByteArrayInputStream(bytes));
            if (probe != null) {
                return new DecodedImage(
                        probe.getWidth(),
                        probe.getHeight(),
                        probe.getRaster().getNumBands() == 1 ? ColorSpace.DEVICE_GRAY : ColorSpace.DEVICE_RGB,
                        8,
                        ImageFilter.DCT,
                        bytes,
                        null);
            }
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("no ImageIO reader recognised this image");
        }
        return fromRaster(image);
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    /** Splits an image into interleaved RGB samples and, when it has any, an alpha soft mask. */
    private static DecodedImage fromRaster(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean transparent = image.getColorModel().hasAlpha();

        byte[] samples = new byte[width * height * 3];
        byte[] alpha = transparent ? new byte[width * height] : null;
        int[] row = new int[width];

        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = row[x];
                int offset = (y * width + x) * 3;
                samples[offset] = (byte) (argb >> 16);
                samples[offset + 1] = (byte) (argb >> 8);
                samples[offset + 2] = (byte) argb;
                if (alpha != null) {
                    alpha[y * width + x] = (byte) (argb >>> 24);
                }
            }
        }
        // An image whose alpha channel is entirely opaque carries no soft mask,
        // so a PNG saved with an unused alpha channel costs nothing extra.
        if (alpha != null && isOpaque(alpha)) {
            alpha = null;
        }
        return new DecodedImage(width, height, ColorSpace.DEVICE_RGB, 8, ImageFilter.FLATE, samples, alpha);
    }

    private static boolean isOpaque(byte[] alpha) {
        for (byte value : alpha) {
            if ((value & 0xFF) != 0xFF) {
                return false;
            }
        }
        return true;
    }
}
