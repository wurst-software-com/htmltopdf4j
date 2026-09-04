package com.wurstsoftware.htmltopdf4j.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Turning an {@code <img>} source into something a PDF can carry. */
class ImageLoaderTest {

    @TempDir
    Path directory;

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage opaque(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);
        return image;
    }

    private static BufferedImage transparent(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80FF0000);
        return image;
    }

    private Path write(String name, byte[] bytes) {
        try {
            Path path = directory.resolve(name);
            Files.write(path, bytes);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void aPngUnderTheBaseDirectoryIsDecoded() {
        write("logo.png", encode(opaque(4, 3), "png"));
        ImageLoader loader = new ImageLoader(directory);

        int index = loader.resolve("logo.png").orElseThrow();
        DecodedImage image = loader.image(index);

        assertEquals(4, image.width());
        assertEquals(3, image.height());
        assertEquals(ColorSpace.DEVICE_RGB, image.colorSpace());
        assertEquals(ImageFilter.FLATE, image.filter());
    }

    @Test
    void aPngIsDecodedToThreeBytesPerPixel() {
        write("logo.png", encode(opaque(4, 3), "png"));
        ImageLoader loader = new ImageLoader(directory);

        DecodedImage image = loader.image(loader.resolve("logo.png").orElseThrow());

        assertEquals(4 * 3 * 3, image.data().length);
        assertEquals(8, image.bitsPerComponent());
    }

    @Test
    void aJpegIsCarriedThroughUndecoded() {
        byte[] bytes = encode(opaque(8, 8), "jpg");
        write("photo.jpg", bytes);
        ImageLoader loader = new ImageLoader(directory);

        DecodedImage image = loader.image(loader.resolve("photo.jpg").orElseThrow());

        assertEquals(ImageFilter.DCT, image.filter());
        assertArrayEqualsPrefix(bytes, image.data());
    }

    @Test
    void transparencyBecomesASoftMask() {
        write("alpha.png", encode(transparent(4, 4), "png"));
        ImageLoader loader = new ImageLoader(directory);

        DecodedImage image = loader.image(loader.resolve("alpha.png").orElseThrow());

        assertTrue(image.hasSoftMask());
        assertEquals(4 * 4, image.softMask().length);
    }

    @Test
    void anUnusedAlphaChannelCostsNoSoftMask() {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                image.setRGB(x, y, 0xFFFF0000);
            }
        }
        write("opaque-alpha.png", encode(image, "png"));
        ImageLoader loader = new ImageLoader(directory);

        assertFalse(loader.image(loader.resolve("opaque-alpha.png").orElseThrow()).hasSoftMask());
    }

    @Test
    void aBase64DataUriNeedsNoFileAtAll() {
        String uri = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(encode(opaque(2, 2), "png"));
        ImageLoader loader = new ImageLoader(null);

        assertEquals(2, loader.image(loader.resolve(uri).orElseThrow()).width());
    }

    @Test
    void theSameSourceIsDecodedOnceAndShared() {
        write("logo.png", encode(opaque(4, 3), "png"));
        ImageLoader loader = new ImageLoader(directory);

        assertEquals(loader.resolve("logo.png"), loader.resolve("logo.png"));
        assertEquals(1, loader.images().size());
    }

    @Test
    void twoDifferentSourcesGetDifferentIndices() {
        write("a.png", encode(opaque(4, 3), "png"));
        write("b.png", encode(opaque(5, 3), "png"));
        ImageLoader loader = new ImageLoader(directory);

        assertNotEquals(loader.resolve("a.png"), loader.resolve("b.png"));
        assertEquals(2, loader.images().size());
    }

    @Test
    void aMissingFileResolvesToNothingRatherThanFailingTheRender() {
        ImageLoader loader = new ImageLoader(directory);

        assertEquals(Optional.empty(), loader.resolve("nosuchfile.png"));
        assertTrue(loader.images().isEmpty());
    }

    @Test
    void aFileThatIsNotAnImageResolvesToNothing() {
        write("notanimage.png", "this is text".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ImageLoader loader = new ImageLoader(directory);

        assertEquals(Optional.empty(), loader.resolve("notanimage.png"));
    }

    @Test
    void aRemoteSourceIsRefusedWithoutAnyNetworkAccess() {
        ImageLoader loader = new ImageLoader(directory);

        assertEquals(Optional.empty(), loader.resolve("https://example.com/logo.png"));
        assertEquals(Optional.empty(), loader.resolve("http://example.com/logo.png"));
    }

    @Test
    void anEmptySourceResolvesToNothing() {
        ImageLoader loader = new ImageLoader(directory);

        assertEquals(Optional.empty(), loader.resolve(null));
        assertEquals(Optional.empty(), loader.resolve("  "));
    }

    private static void assertArrayEqualsPrefix(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "byte " + i);
        }
    }
}
