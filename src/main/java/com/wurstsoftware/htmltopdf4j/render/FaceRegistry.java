package com.wurstsoftware.htmltopdf4j.render;

import com.wurstsoftware.htmltopdf4j.FaceSource;
import com.wurstsoftware.htmltopdf4j.style.ComputedStyle;
import com.wurstsoftware.htmltopdf4j.text.EmbeddedFace;
import com.wurstsoftware.htmltopdf4j.text.Face;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import com.wurstsoftware.htmltopdf4j.text.FontLibrary;
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
 * Font *files* are cached across renders by {@link #FILE_CACHE}, because parsing
 * the same font again for every render is pure waste and the bytes on disk do
 * not change under us.
 */
public final class FaceRegistry {

    /** Faces loaded from disk, shared between renders and keyed by absolute path. */
    private static final Map<Path, EmbeddedFace> FILE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Families tried, in order, for a character the requested Face cannot display. */
    private static final List<String> FALLBACK_FAMILIES =
            List.of("dejavu sans", "noto sans", "freeserif", "noto sans symbols", "symbola", "noto color emoji");

    private final List<FaceChain> chains = new ArrayList<>();
    private final Map<String, Integer> byRequest = new HashMap<>();
    private final Map<String, EmbeddedFace> declaredFaces = new LinkedHashMap<>();
    private final Face defaultFace;

    public FaceRegistry(FaceSource defaultSource) {
        this.defaultFace = load(defaultSource);
        chains.add(chainFor(defaultFace));
        byRequest.put("", 0);
    }

    /** The Face chain index a run of text with this style should be shaped with. */
    public int indexFor(ComputedStyle style) {
        List<String> families = style.fontFamily();
        boolean bold = style.bold();
        boolean italic = style.italic();
        String request = String.join(",", families).toLowerCase(Locale.ROOT) + "|" + bold + "|" + italic;

        Integer existing = byRequest.get(request);
        if (existing != null) {
            return existing;
        }
        int index = chains.size();
        chains.add(chainFor(resolve(families, bold, italic)));
        byRequest.put(request, index);
        return index;
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
     */
    public void declare(String family, byte[] program) {
        try {
            declaredFaces.put(family.trim().toLowerCase(Locale.ROOT),
                    EmbeddedFace.fromBytes(program, family.trim()));
        } catch (RuntimeException e) {
            // A font-face the shaper cannot read leaves the family unresolved,
            // and the Cascade's next family in the list takes over.
        }
    }

    private Face resolve(List<String> families, boolean bold, boolean italic) {
        for (String family : families) {
            EmbeddedFace declared = declaredFaces.get(family.trim().toLowerCase(Locale.ROOT));
            if (declared != null) {
                return declared;
            }
            Optional<Face> installed = FontLibrary.find(family, bold, italic).flatMap(FaceRegistry::open);
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
            FontLibrary.find(family, false, false)
                    .flatMap(FaceRegistry::open)
                    .filter(face -> !face.family().equalsIgnoreCase(primary.family()))
                    .ifPresent(fallbacks::add);
        }
        return new FaceChain(primary, fallbacks);
    }

    private static Optional<Face> open(FontLibrary.Entry entry) {
        EmbeddedFace cached = FILE_CACHE.get(entry.path());
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            EmbeddedFace face = EmbeddedFace.fromBytes(Files.readAllBytes(entry.path()), entry.family());
            FILE_CACHE.put(entry.path(), face);
            return Optional.of(face);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Face load(FaceSource source) {
        return switch (source) {
            case FaceSource.Standard14 ignored -> Standard14Face.HELVETICA;
            case FaceSource.Bytes bytes -> EmbeddedFace.fromBytes(bytes.data(), bytes.name());
            case FaceSource.File file -> loadFile(file.path());
            case FaceSource.SystemFamily family -> FontLibrary.find(family.family(), false, false)
                    .flatMap(FaceRegistry::open)
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
