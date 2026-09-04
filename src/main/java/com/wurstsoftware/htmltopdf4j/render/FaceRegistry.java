package com.wurstsoftware.htmltopdf4j.render;

import com.wurstsoftware.htmltopdf4j.FaceSource;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.text.EmbeddedFace;
import com.wurstsoftware.htmltopdf4j.text.Face;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import com.wurstsoftware.htmltopdf4j.text.FontEnvironment;
import com.wurstsoftware.htmltopdf4j.text.Standard14Face;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Faces one render needs, interned so each one is loaded, shaped and
 * subsetted once however many elements use it.
 *
 * <p>This is per-render state, not a cache shared between renders: a Face chain
 * index is only meaningful against the {@link RenderContext} it was built for.
 * Font *files* are cached by the {@link FontEnvironment} this registry draws
 * from, because parsing the same font again for every render is pure waste and
 * the bytes on disk do not change under us.
 */
public final class FaceRegistry {

    /**
     * One variant of one {@code @font-face} family: which family, and which of
     * its four upright/slanted, light/heavy corners this program is.
     */
    private record Variant(String family, boolean bold, boolean italic) {

        static Variant of(String family, boolean bold, boolean italic) {
            return new Variant(family.trim().toLowerCase(Locale.ROOT), bold, italic);
        }
    }

    /** Families tried, in order, for a character the requested Face cannot display. */
    private static final List<String> FALLBACK_FAMILIES =
            List.of("dejavu sans", "noto sans", "freeserif", "noto sans symbols", "symbola", "noto color emoji");

    private final List<FaceChain> chains = new ArrayList<>();
    private final List<Boolean> syntheticBold = new ArrayList<>();
    private final Map<String, Integer> byRequest = new HashMap<>();
    private final Map<Variant, EmbeddedFace> declaredFaces = new LinkedHashMap<>();
    private final Face defaultFace;
    private final FontEnvironment fonts;

    public FaceRegistry(FaceSource defaultSource, FontEnvironment fonts) {
        this.fonts = fonts;
        this.defaultFace = load(defaultSource);
        chains.add(chainFor(defaultFace));
        syntheticBold.add(false);
        // The commonest style of all — no font-family, upright, regular — is
        // the default chain, so it must hash to it rather than resolve to an
        // identical second chain and embed the same font twice.
        byRequest.put(requestKey(List.of(), false, false), 0);
    }

    /** The Face chain index a run of text with this style should be shaped with. */
    public int indexFor(ComputedStyle style) {
        List<String> families = style.fontFamily();
        boolean bold = style.bold();
        boolean italic = style.italic();
        String request = requestKey(families, bold, italic);

        Integer existing = byRequest.get(request);
        if (existing != null) {
            return existing;
        }
        int index = chains.size();
        Face resolved = resolve(families, bold, italic);
        chains.add(chainFor(resolved));
        // Emboldening a Face that is already bold draws it twice as heavy, so
        // the writer is only asked to fake it when the family had nothing.
        syntheticBold.add(bold && !resolved.bold());
        byRequest.put(request, index);
        return index;
    }

    private static String requestKey(List<String> families, boolean bold, boolean italic) {
        return String.join(",", families).toLowerCase(Locale.ROOT) + "|" + bold + "|" + italic;
    }

    /**
     * Whether text drawn with this chain has to be emboldened by the writer,
     * because the family offered no real bold Face.
     */
    public boolean syntheticBold(int index) {
        return syntheticBold.get(index);
    }

    public FaceChain chain(int index) {
        return chains.get(index);
    }

    public List<FaceChain> chains() {
        return List.copyOf(chains);
    }

    public Face defaultFace() {
        return defaultFace;
    }

    /**
     * Registers a Face declared by an {@code @font-face} rule. A declared family
     * beats an installed one of the same name, which is what lets a Document
     * ship its own font.
     *
     * <p>A family may be declared several times, once per variant: the rule's
     * {@code font-weight} and {@code font-style} descriptors say which one this
     * program is, so a second rule can supply the real bold rather than
     * replacing the regular.
     */
    public void declare(String family, boolean bold, boolean italic, byte[] program) {
        try {
            declaredFaces.put(Variant.of(family, bold, italic),
                    EmbeddedFace.fromBytes(program, family.trim()));
        } catch (RuntimeException e) {
            // A font-face the shaper cannot read leaves the family unresolved,
            // and the Cascade's next family in the list takes over.
        }
    }

    /**
     * The declared Face closest to what was asked for: the exact variant, then
     * the upright regular, then whatever the family declared at all — a Document
     * that ships one file expects it used for every weight.
     */
    private EmbeddedFace declaredFace(String family, boolean bold, boolean italic) {
        for (Variant variant : List.of(
                Variant.of(family, bold, italic),
                Variant.of(family, bold, false),
                Variant.of(family, false, italic),
                Variant.of(family, false, false))) {
            EmbeddedFace face = declaredFaces.get(variant);
            if (face != null) {
                return face;
            }
        }
        String wanted = family.trim().toLowerCase(Locale.ROOT);
        return declaredFaces.entrySet().stream()
                .filter(entry -> entry.getKey().family().equals(wanted))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private Face resolve(List<String> families, boolean bold, boolean italic) {
        for (String family : families) {
            EmbeddedFace declared = declaredFace(family, bold, italic);
            if (declared != null) {
                return declared;
            }
            Optional<Face> installed = fonts.find(family, bold, italic).flatMap(fonts::open);
            if (installed.isPresent()) {
                return installed.get();
            }
        }
        return defaultFace;
    }

    /**
     * A chain of the requested Face plus the broad-coverage families, so a
     * character the requested Face lacks is drawn from a Face that has it
     * instead of appearing as {@code .notdef}.
     */
    private FaceChain chainFor(Face primary) {
        List<Face> fallbacks = new ArrayList<>();
        for (String family : FALLBACK_FAMILIES) {
            fonts.find(family, false, false)
                    .flatMap(fonts::open)
                    .filter(face -> !face.family().equalsIgnoreCase(primary.family()))
                    .ifPresent(fallbacks::add);
        }
        return new FaceChain(primary, fallbacks);
    }

    private Face load(FaceSource source) {
        return switch (source) {
            case FaceSource.Standard14 ignored -> Standard14Face.HELVETICA;
            case FaceSource.Bytes bytes -> EmbeddedFace.fromBytes(bytes.data(), bytes.name());
            case FaceSource.File file -> loadFile(file.path());
            case FaceSource.SystemFamily family -> fonts.find(family.family(), false, false)
                    .flatMap(fonts::open)
                    .orElse(Standard14Face.HELVETICA);
        };
    }

    private static Face loadFile(Path path) {
        try {
            return EmbeddedFace.fromBytes(Files.readAllBytes(path), path.getFileName().toString());
        } catch (IOException e) {
            throw new com.wurstsoftware.htmltopdf4j.RenderException("cannot read the default Face " + path, e);
        }
    }
}
