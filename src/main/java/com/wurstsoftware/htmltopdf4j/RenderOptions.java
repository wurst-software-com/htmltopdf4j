package com.wurstsoftware.htmltopdf4j;

import com.wurstsoftware.htmltopdf4j.text.FontEnvironment;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The caller's inputs to one render.
 *
 * <p>Immutable, and never written to by a render. The reference engine's
 * {@code RenderOptions} doubled as scratch space for per-render state — resolved
 * Faces, interned link targets — which made concurrent renders share mutable
 * structure. That state lives in an internal per-render context here instead, so
 * one options value can safely drive many simultaneous renders.
 */
public final class RenderOptions {

    private static final float DEFAULT_MARGIN = 48f;

    private final Paper paper;
    private final PageSize pageSize;
    private final float marginTop;
    private final float marginRight;
    private final float marginBottom;
    private final float marginLeft;
    private final FaceSource defaultFace;
    private final Path baseDirectory;
    private final FontEnvironment fontEnvironment;

    private RenderOptions(Builder builder) {
        this.paper = builder.paper;
        this.pageSize = builder.pageSize != null ? builder.pageSize : builder.paper.portrait();
        this.marginTop = builder.marginTop;
        this.marginRight = builder.marginRight;
        this.marginBottom = builder.marginBottom;
        this.marginLeft = builder.marginLeft;
        this.defaultFace = builder.defaultFace;
        this.baseDirectory = builder.baseDirectory;
        this.fontEnvironment = builder.fontEnvironment;
    }

    /** A4 portrait, 48pt margins, Helvetica, and no base directory for local images. */
    public static RenderOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder pre-loaded with this value's settings. */
    public Builder toBuilder() {
        return new Builder()
                .paper(paper)
                .pageSize(pageSize)
                .margins(marginTop, marginRight, marginBottom, marginLeft)
                .defaultFace(defaultFace)
                .baseDirectory(baseDirectory)
                .fontEnvironment(fontEnvironment);
    }

    public Paper paper() {
        return paper;
    }

    public PageSize pageSize() {
        return pageSize;
    }

    public float marginTop() {
        return marginTop;
    }

    public float marginRight() {
        return marginRight;
    }

    public float marginBottom() {
        return marginBottom;
    }

    public float marginLeft() {
        return marginLeft;
    }

    public FaceSource defaultFace() {
        return defaultFace;
    }

    /**
     * Where relative {@code <img src>} paths resolve from. Empty disables
     * file-path images entirely; {@code data:} URIs still work. Remote URLs are
     * never fetched — that capability is absent, not disabled.
     */
    public Optional<Path> baseDirectory() {
        return Optional.ofNullable(baseDirectory);
    }

    /**
     * The fonts this render may draw with. Defaults to the shared environment,
     * which scans the machine's own font directories once for the life of the
     * process; give it another to render against a different search path, or
     * {@link FontEnvironment#empty()} to render against none.
     */
    public FontEnvironment fontEnvironment() {
        return fontEnvironment;
    }

    /** The width available to content after the left and right margins. */
    public float contentWidth() {
        return pageSize.width() - marginLeft - marginRight;
    }

    /** The height available to content after the top and bottom margins. */
    public float contentHeight() {
        return pageSize.height() - marginTop - marginBottom;
    }

    public static final class Builder {

        private Paper paper = Paper.A4;
        private PageSize pageSize;
        private float marginTop = DEFAULT_MARGIN;
        private float marginRight = DEFAULT_MARGIN;
        private float marginBottom = DEFAULT_MARGIN;
        private float marginLeft = DEFAULT_MARGIN;
        private FaceSource defaultFace = FaceSource.HELVETICA;
        private Path baseDirectory;
        private FontEnvironment fontEnvironment = FontEnvironment.shared();

        private Builder() {}

        /** Sets the base paper. Clears any explicit page size. */
        public Builder paper(Paper paper) {
            this.paper = Objects.requireNonNull(paper, "paper");
            this.pageSize = null;
            return this;
        }

        /** Overrides the paper with an explicit size. */
        public Builder pageSize(PageSize pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /** Sets all four margins to {@code margin} points. */
        public Builder margin(float margin) {
            return margins(margin, margin, margin, margin);
        }

        public Builder margins(float top, float right, float bottom, float left) {
            this.marginTop = requireNonNegative(top, "top");
            this.marginRight = requireNonNegative(right, "right");
            this.marginBottom = requireNonNegative(bottom, "bottom");
            this.marginLeft = requireNonNegative(left, "left");
            return this;
        }

        public Builder marginTop(float margin) {
            this.marginTop = requireNonNegative(margin, "top");
            return this;
        }

        public Builder marginRight(float margin) {
            this.marginRight = requireNonNegative(margin, "right");
            return this;
        }

        public Builder marginBottom(float margin) {
            this.marginBottom = requireNonNegative(margin, "bottom");
            return this;
        }

        public Builder marginLeft(float margin) {
            this.marginLeft = requireNonNegative(margin, "left");
            return this;
        }

        public Builder defaultFace(FaceSource defaultFace) {
            this.defaultFace = Objects.requireNonNull(defaultFace, "defaultFace");
            return this;
        }

        /** The fonts this render may draw with; {@link FontEnvironment#shared()} by default. */
        public Builder fontEnvironment(FontEnvironment fontEnvironment) {
            this.fontEnvironment = Objects.requireNonNull(fontEnvironment, "fontEnvironment");
            return this;
        }

        /** {@code null} disables resolving {@code <img src>} against the file system. */
        public Builder baseDirectory(Path baseDirectory) {
            this.baseDirectory = baseDirectory;
            return this;
        }

        public RenderOptions build() {
            return new RenderOptions(this);
        }

        private static float requireNonNegative(float margin, String side) {
            if (!(margin >= 0)) {
                throw new IllegalArgumentException(side + " margin must be >= 0, got " + margin);
            }
            return margin;
        }
    }
}
