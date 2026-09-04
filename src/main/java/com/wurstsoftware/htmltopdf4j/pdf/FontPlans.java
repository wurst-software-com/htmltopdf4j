package com.wurstsoftware.htmltopdf4j.pdf;

import com.wurstsoftware.htmltopdf4j.layout.Page;
import com.wurstsoftware.htmltopdf4j.paint.PaintCommand;
import com.wurstsoftware.htmltopdf4j.render.RenderContext;
import com.wurstsoftware.htmltopdf4j.text.CidLayout;
import com.wurstsoftware.htmltopdf4j.text.EmbeddedFace;
import com.wurstsoftware.htmltopdf4j.text.Face;
import com.wurstsoftware.htmltopdf4j.text.FaceChain;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Which Faces a document actually paints text in, and everything the writer
 * needs to emit each of them.
 *
 * <p>Discovery walks every Text Paint command, splitting each by the coverage of
 * its Face chain, so a Face only earns a {@code /Fn} resource if some character
 * is really drawn in it. Faces are deduplicated by identity in first-use order,
 * which is deterministic: two runs resolving to the same Face share one
 * resource. The default Face is always F1, even on a Page that paints no text.
 */
final class FontPlans {

    /**
     * One Face in the output.
     *
     * @param objectIds for an embedded Face, the four extra objects it needs —
     *     descendant CIDFont, descriptor, font file, ToUnicode CMap — otherwise
     *     {@code null}
     * @param program the subset font program, or {@code null} to embed the
     *     original whole
     */
    record Plan(
            String resource,
            int fontId,
            Face face,
            CidLayout cid,
            byte[] program,
            String baseName,
            int[] objectIds) {

        boolean isEmbedded() {
            return objectIds != null;
        }
    }

    private final List<Plan> plans;
    private final Map<Face, Plan> byFace = new IdentityHashMap<>();

    private FontPlans(List<Plan> plans) {
        this.plans = plans;
        for (Plan plan : plans) {
            byFace.put(plan.face(), plan);
        }
    }

    List<Plan> all() {
        return plans;
    }

    /** The {@code /Fn} name a Face is drawn through; the default Face's if it has none. */
    String resourceName(Face face) {
        Plan plan = byFace.get(face);
        return plan != null ? plan.resource() : plans.get(0).resource();
    }

    /** The object id the next free object may take. */
    int nextObjectId(int firstExtraId) {
        int next = firstExtraId;
        for (Plan plan : plans) {
            if (plan.isEmbedded()) {
                next += 4;
            }
        }
        return next;
    }

    /**
     * Finds which Faces paint text, without doing any of the expensive work.
     *
     * <p>Separate from {@link #plan} because the object numbering depends on how
     * many Faces there are, and subsetting must not be paid for twice just to
     * count them.
     */
    static FaceUsage discover(List<Page> pages, RenderContext context) {
        FaceUsage usage = new FaceUsage();
        // The default Face always gets F1, whether or not it paints anything.
        usage.register(context.defaultFace().primary());

        for (Page page : pages) {
            for (PaintCommand command : page.commands()) {
                if (!(command instanceof PaintCommand.Text text)) {
                    continue;
                }
                FaceChain chain = context.face(text.face());
                List<FaceChain.Segment> segments = chain.segment(text.text());
                if (segments == null) {
                    usage.record(chain.primary(), text.text());
                } else {
                    for (FaceChain.Segment segment : segments) {
                        usage.record(chain.at(segment.chainIndex()), segment.text());
                    }
                }
            }
        }

        return usage;
    }

    /**
     * Turns discovered usage into plans, shaping and subsetting each embedded
     * Face for the text it actually draws.
     *
     * @param firstFontId the object id of the first {@code /Fn} font object
     * @param firstExtraId the object id the embedded Faces' extra objects start at
     */
    static FontPlans plan(FaceUsage usage, int firstFontId, int firstExtraId) {
        List<Plan> plans = new ArrayList<>(usage.order.size());
        int nextExtra = firstExtraId;
        for (int ordinal = 0; ordinal < usage.order.size(); ordinal++) {
            Face face = usage.order.get(ordinal);
            String resource = "F" + (ordinal + 1);
            int fontId = firstFontId + ordinal;

            if (!(face instanceof EmbeddedFace embedded)) {
                plans.add(new Plan(resource, fontId, face, null, null, null, null));
                continue;
            }

            CidLayout cid = embedded.cidLayout(usage.textsOf(face));
            SortedSet<Integer> used = cid.usedGlyphIds();
            byte[] program = used.isEmpty() ? null : TrueTypeSubsetter.subset(embedded.bytes(), used);
            String postScriptName = embedded.descriptor().postScriptName();
            // Readers recognise a subset by its six-letter tag, and know not to
            // treat a tagged name as interchangeable with the full font.
            String baseName =
                    program != null ? PdfSyntax.subsetTag(used) + "+" + postScriptName : postScriptName;

            plans.add(new Plan(
                    resource,
                    fontId,
                    face,
                    cid,
                    program,
                    baseName,
                    new int[] {nextExtra, nextExtra + 1, nextExtra + 2, nextExtra + 3}));
            nextExtra += 4;
        }
        return new FontPlans(plans);
    }

    /**
     * The Faces seen so far, in first-use order, with the strings drawn in each.
     *
     * <p>Keyed by identity, not equality: two Faces loaded from the same file are
     * two font programs and get two resources. Ordered, so object ids are assigned
     * deterministically and the same Document always produces the same PDF.
     */
    static final class FaceUsage {

        int count() {
            return order.size();
        }


        private final List<Face> order = new ArrayList<>();
        private final Map<Face, SortedSet<String>> texts = new IdentityHashMap<>();

        void register(Face face) {
            if (!texts.containsKey(face)) {
                order.add(face);
                // Sorted and de-duplicated: the CID layout only cares which
                // strings exist, and shaping each unique one once is enough.
                texts.put(face, new TreeSet<>());
            }
        }

        void record(Face face, String text) {
            register(face);
            texts.get(face).add(text);
        }

        Iterable<String> textsOf(Face face) {
            return texts.get(face);
        }
    }

    /** The {@code /Font} sub-dictionary every Page's resources declare. */
    String fontResources() {
        StringBuilder entries = new StringBuilder();
        for (Plan plan : plans) {
            if (!entries.isEmpty()) {
                entries.append(' ');
            }
            entries.append('/').append(plan.resource()).append(' ').append(plan.fontId()).append(" 0 R");
        }
        return entries.toString();
    }

    int count() {
        return plans.size();
    }
}
