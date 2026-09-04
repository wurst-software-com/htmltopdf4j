package com.wurstsoftware.htmltopdf4j.render;

import com.wurstsoftware.htmltopdf4j.PageSize;
import com.wurstsoftware.htmltopdf4j.image.DecodedImage;
import com.wurstsoftware.htmltopdf4j.text.Face;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import java.util.List;

/**
 * The state one render resolves for itself, kept apart from the caller's
 * {@link com.wurstsoftware.htmltopdf4j.RenderOptions}.
 *
 * <p>The reference engine kept both in one mutable struct, so a render wrote its
 * resolved faces and interned links back into the options it was handed. Two
 * concurrent renders sharing an options value would then have raced. Splitting
 * them is what makes the published thread-safety contract true rather than
 * merely intended: options go in, a context is derived per render, and nothing
 * a render touches is visible to another.
 *
 * @param faces the Faces the Document selects, indexed by the {@code face} field
 *     of a Text Paint command; index 0 is the default Face
 * @param links the Document's {@code <a href>} targets, indexed by
 *     {@link com.wurstsoftware.htmltopdf4j.layout.LinkArea#link} minus one
 */
public record RenderContext(PageSize pageSize, List<FaceChain> faces, List<String> links, List<DecodedImage> images) {

    public RenderContext {
        faces = List.copyOf(faces);
        links = List.copyOf(links);
        images = List.copyOf(images);
        if (faces.isEmpty()) {
            throw new IllegalArgumentException("a render context always has a default Face at index 0");
        }
    }

    /** A context with only the default Face and nothing else resolved. */
    public static RenderContext of(PageSize pageSize, Face defaultFace) {
        return new RenderContext(pageSize, List.of(FaceChain.of(defaultFace)), List.of(), List.of());
    }

    public FaceChain defaultFace() {
        return faces.get(0);
    }

    /** The Face chain a Text Paint command selects, falling back to the default if the index is stale. */
    public FaceChain face(int index) {
        return index >= 0 && index < faces.size() ? faces.get(index) : defaultFace();
    }

    /** The link target a {@link com.wurstsoftware.htmltopdf4j.layout.LinkArea} points at, or {@code null}. */
    public String link(int oneBasedIndex) {
        int index = oneBasedIndex - 1;
        return index >= 0 && index < links.size() ? links.get(index) : null;
    }
}
