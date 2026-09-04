package com.wurstsoftware.htmltopdf4j;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where the default Face comes from.
 *
 * <p>A {@link Standard14} default is referenced rather than embedded, so a
 * simple Latin Document carries no font payload at all. Anything else is
 * embedded and subset.
 */
public sealed interface FaceSource {

    /**
     * One of the PDF standard-14 fonts, referenced by name and never embedded.
     * Only Helvetica is offered: it is what the reference engine defaults to,
     * and the rest would need their own hard-coded metrics to measure with.
     */
    record Standard14() implements FaceSource {}

    /** A font program on disk. */
    record File(Path path) implements FaceSource {
        public File {
            Objects.requireNonNull(path, "path");
        }
    }

    /** A font program already in memory. */
    record Bytes(byte[] data, String name) implements FaceSource {
        public Bytes {
            Objects.requireNonNull(data, "data");
            Objects.requireNonNull(name, "name");
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }

    /** A family resolved against the Faces installed on this machine. */
    record SystemFamily(String family) implements FaceSource {
        public SystemFamily {
            Objects.requireNonNull(family, "family");
        }
    }

    FaceSource HELVETICA = new Standard14();
}
