package com.wurstsoftware.htmltopdf4j.pdf;

import static com.wurstsoftware.htmltopdf4j.pdf.PdfSyntax.coord;
import static com.wurstsoftware.htmltopdf4j.pdf.PdfSyntax.number;

import com.wurstsoftware.htmltopdf4j.PdfWriteException;
import com.wurstsoftware.htmltopdf4j.image.DecodedImage;
import com.wurstsoftware.htmltopdf4j.layout.AnchorMark;
import com.wurstsoftware.htmltopdf4j.layout.LinkArea;
import com.wurstsoftware.htmltopdf4j.layout.Page;
import com.wurstsoftware.htmltopdf4j.paint.Rect;
import com.wurstsoftware.htmltopdf4j.render.RenderContext;
import com.wurstsoftware.htmltopdf4j.text.CidLayout;
import com.wurstsoftware.htmltopdf4j.text.EmbeddedFace;
import com.wurstsoftware.htmltopdf4j.text.PdfFontDescriptor;
import com.wurstsoftware.htmltopdf4j.text.Standard14Face;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a laid-out Document as a PDF file.
 *
 * <p>Object ids are assigned up front, in a fixed order, before anything is
 * written: the catalog, the page tree, one object per font resource, two per
 * Page, then the embedded Faces' extras, the images, the link annotations and
 * the outline. Planning the whole numbering first is what lets objects refer
 * forwards to each other in a single pass, with no patching afterwards.
 */
public final class PdfDocumentWriter {

    private static final int CATALOG_ID = 1;
    private static final int PAGES_ID = 2;
    private static final int FIRST_FONT_ID = 3;

    /** Object ids are two bytes in the cross reference table's own conventions. */
    private static final int MAX_OBJECTS = 0xFFFF;

    private PdfDocumentWriter() {}

    public static byte[] write(List<Page> pages, RenderContext context) {
        int pageCount = pages.size();
        FontPlans.FaceUsage usage = FontPlans.discover(pages, context);
        int firstPageId = FIRST_FONT_ID + usage.count();
        // Each Page is two objects: the page dictionary and its content stream.
        int firstExtraId = firstPageId + pageCount * 2;

        FontPlans plans = FontPlans.plan(usage, FIRST_FONT_ID, firstExtraId);
        int nextId = plans.nextObjectId(firstExtraId);

        List<ImagePlan> imagePlans = new ArrayList<>();
        for (int index = 0; index < context.images().size(); index++) {
            DecodedImage image = context.images().get(index);
            int smaskId = image.hasSoftMask() ? nextId++ : -1;
            imagePlans.add(new ImagePlan("Im" + index, nextId++, smaskId));
        }

        Map<String, Destination> namedDestinations = namedDestinations(pages, firstPageId);
        List<List<AnnotationPlan>> annotations = new ArrayList<>(pageCount);
        for (Page page : pages) {
            List<AnnotationPlan> forPage = new ArrayList<>();
            for (LinkArea area : page.linkAreas()) {
                AnnotationPlan plan = planAnnotation(area, context, namedDestinations, nextId);
                if (plan != null) {
                    forPage.add(plan);
                    nextId++;
                }
            }
            annotations.add(forPage);
        }

        Outline outline = Outline.of(pages, index -> firstPageId + index * 2);
        int outlineRootId = -1;
        if (!outline.isEmpty()) {
            outlineRootId = nextId;
            nextId += 1 + outline.size();
        }

        int objectCount = nextId - 1;
        if (objectCount > MAX_OBJECTS) {
            throw new PdfWriteException("document needs " + objectCount + " PDF objects, more than the "
                    + MAX_OBJECTS + " this writer assigns");
        }

        PdfObjectWriter writer = new PdfObjectWriter();
        writeCatalog(writer, outlineRootId);
        writePageTree(writer, pageCount, firstPageId);
        writeFontObjects(writer, plans);
        writePages(writer, pages, context, plans, imagePlans, annotations, firstPageId);
        writeEmbeddedFaces(writer, plans);
        writeImages(writer, context.images(), imagePlans);
        writeAnnotations(writer, annotations);
        writeOutline(writer, outline, outlineRootId);

        return writer.finish(CATALOG_ID, objectCount);
    }

    private static void writeCatalog(PdfObjectWriter writer, int outlineRootId) {
        String outlines = outlineRootId > 0 ? " /Outlines " + outlineRootId + " 0 R" : "";
        writer.object(CATALOG_ID, "<< /Type /Catalog /Pages " + PAGES_ID + " 0 R" + outlines + " >>");
    }

    private static void writePageTree(PdfObjectWriter writer, int pageCount, int firstPageId) {
        StringBuilder kids = new StringBuilder();
        for (int index = 0; index < pageCount; index++) {
            if (index > 0) {
                kids.append(' ');
            }
            kids.append(firstPageId + index * 2).append(" 0 R");
        }
        writer.object(PAGES_ID, "<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>");
    }

    private static void writeFontObjects(PdfObjectWriter writer, FontPlans plans) {
        for (FontPlans.Plan plan : plans.all()) {
            if (!plan.isEmbedded()) {
                String baseFont = plan.face() instanceof Standard14Face standard
                        ? standard.baseFontName()
                        : "Helvetica";
                writer.object(
                        plan.fontId(),
                        "<< /Type /Font /Subtype /Type1 /BaseFont /" + baseFont
                                + " /Encoding /WinAnsiEncoding >>");
            } else {
                int[] ids = plan.objectIds();
                writer.object(
                        plan.fontId(),
                        "<< /Type /Font /Subtype /Type0 /BaseFont /" + plan.baseName()
                                + " /Encoding /Identity-H /DescendantFonts [" + ids[0] + " 0 R]"
                                + " /ToUnicode " + ids[3] + " 0 R >>");
            }
        }
    }

    private static void writePages(
            PdfObjectWriter writer,
            List<Page> pages,
            RenderContext context,
            FontPlans plans,
            List<ImagePlan> imagePlans,
            List<List<AnnotationPlan>> annotations,
            int firstPageId) {

        // Every Page declares every image, so an /ImN operator resolves wherever
        // it appears. Declaring one a Page does not use costs nothing.
        String xobjects = "";
        if (!imagePlans.isEmpty()) {
            StringBuilder entries = new StringBuilder();
            for (ImagePlan plan : imagePlans) {
                if (!entries.isEmpty()) {
                    entries.append(' ');
                }
                entries.append('/').append(plan.name()).append(' ').append(plan.objectId()).append(" 0 R");
            }
            xobjects = " /XObject << " + entries + " >>";
        }
        String fonts = plans.fontResources();

        for (int index = 0; index < pages.size(); index++) {
            Page page = pages.get(index);
            int pageId = firstPageId + index * 2;
            int contentId = pageId + 1;

            StringBuilder annots = new StringBuilder();
            for (AnnotationPlan plan : annotations.get(index)) {
                annots.append(annots.isEmpty() ? "" : " ").append(plan.objectId()).append(" 0 R");
            }
            String annotEntry = annots.isEmpty() ? "" : "/Annots [" + annots + "] ";

            writer.object(
                    pageId,
                    "<< /Type /Page /Parent " + PAGES_ID + " 0 R /MediaBox [0 0 "
                            + coord(context.pageSize().width()) + " " + coord(context.pageSize().height())
                            + "] /Resources << /Font << " + fonts + " >>" + xobjects + " >> "
                            + annotEntry + "/Contents " + contentId + " 0 R >>");
            writer.streamObject(contentId, ContentStream.of(page, context, plans));
        }
    }

    private static void writeEmbeddedFaces(PdfObjectWriter writer, FontPlans plans) {
        for (FontPlans.Plan plan : plans.all()) {
            if (!plan.isEmbedded() || !(plan.face() instanceof EmbeddedFace face)) {
                continue;
            }
            int[] ids = plan.objectIds();
            int descendantId = ids[0];
            int descriptorId = ids[1];
            int fontFileId = ids[2];
            int toUnicodeId = ids[3];
            CidLayout cid = plan.cid();
            PdfFontDescriptor descriptor = face.descriptor();

            StringBuilder widths = new StringBuilder();
            cid.widths().forEach((gid, width) -> {
                if (!widths.isEmpty()) {
                    widths.append(' ');
                }
                widths.append(gid).append(" [").append(width).append(']');
            });

            writer.object(
                    descendantId,
                    "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /" + plan.baseName()
                            + " /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>"
                            + " /FontDescriptor " + descriptorId + " 0 R /CIDToGIDMap /Identity /DW 1000"
                            + " /W [" + widths + "] >>");

            writer.object(
                    descriptorId,
                    "<< /Type /FontDescriptor /FontName /" + plan.baseName()
                            + " /Flags " + descriptor.flags()
                            + " /FontBBox [" + descriptor.bboxXMin() + " " + descriptor.bboxYMin() + " "
                            + descriptor.bboxXMax() + " " + descriptor.bboxYMax() + "]"
                            + " /ItalicAngle " + number(descriptor.italicAngle(), 2)
                            + " /Ascent " + descriptor.ascent()
                            + " /Descent " + descriptor.descent()
                            + " /CapHeight " + descriptor.capHeight()
                            + " /StemV " + descriptor.stemV()
                            + " /FontFile2 " + fontFileId + " 0 R >>");

            // Retaining glyph ids is what lets the subset stand in for the whole
            // program without touching /W, /ToUnicode or the identity map.
            writer.fontFileObject(fontFileId, plan.program() != null ? plan.program() : face.bytes());
            writer.streamObject(toUnicodeId, ToUnicodeCMap.of(cid));
        }
    }

    private static void writeImages(
            PdfObjectWriter writer, List<DecodedImage> images, List<ImagePlan> plans) {
        for (int index = 0; index < images.size(); index++) {
            DecodedImage image = images.get(index);
            ImagePlan plan = plans.get(index);

            if (plan.softMaskId() > 0) {
                writer.streamWithDictionary(
                        plan.softMaskId(),
                        "/Type /XObject /Subtype /Image /Width " + image.width() + " /Height " + image.height()
                                + " /ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode",
                        PdfObjectWriter.deflate(image.softMask()));
            }

            String dict = "/Type /XObject /Subtype /Image /Width " + image.width()
                    + " /Height " + image.height()
                    + " /ColorSpace /" + image.colorSpace().pdfName()
                    + " /BitsPerComponent " + image.bitsPerComponent()
                    + " /Filter /" + image.filter().pdfName()
                    + (plan.softMaskId() > 0 ? " /SMask " + plan.softMaskId() + " 0 R" : "");

            // JPEG bytes pass through as DCTDecode; decoded samples are
            // compressed here.
            byte[] body = switch (image.filter()) {
                case DCT -> image.data();
                case FLATE -> PdfObjectWriter.deflate(image.data());
            };
            writer.streamWithDictionary(plan.objectId(), dict, body);
        }
    }

    private static void writeAnnotations(PdfObjectWriter writer, List<List<AnnotationPlan>> annotations) {
        for (List<AnnotationPlan> forPage : annotations) {
            for (AnnotationPlan plan : forPage) {
                Rect rect = plan.rect();
                String bounds = "[" + coord(rect.x()) + " " + coord(rect.y()) + " "
                        + coord(rect.right()) + " " + coord(rect.top()) + "]";
                String action = plan.destination() != null
                        ? "/Dest [" + plan.destination().pageObject() + " 0 R /XYZ null "
                                + coord(plan.destination().y()) + " null]"
                        : "/A << /S /URI /URI (" + PdfSyntax.escapeLiteral(plan.uri()) + ") >>";
                writer.object(
                        plan.objectId(),
                        "<< /Type /Annot /Subtype /Link /Rect " + bounds + " /Border [0 0 0] " + action + " >>");
            }
        }
    }

    private static void writeOutline(PdfObjectWriter writer, Outline outline, int rootId) {
        if (outline.isEmpty()) {
            return;
        }
        List<Integer> top = outline.childrenOf(-1);
        writer.object(
                rootId,
                "<< /Type /Outlines /First " + itemId(rootId, top.get(0)) + " 0 R /Last "
                        + itemId(rootId, top.get(top.size() - 1)) + " 0 R /Count " + outline.size() + " >>");

        for (int index = 0; index < outline.size(); index++) {
            Outline.Entry entry = outline.entries().get(index);
            List<Integer> siblings = outline.childrenOf(entry.parent());
            int position = siblings.indexOf(index);

            StringBuilder body = new StringBuilder("<< /Title ")
                    .append(PdfSyntax.textString(entry.title()))
                    .append(" /Parent ")
                    .append(entry.parent() < 0 ? rootId : itemId(rootId, entry.parent()))
                    .append(" 0 R");
            if (position > 0) {
                body.append(" /Prev ").append(itemId(rootId, siblings.get(position - 1))).append(" 0 R");
            }
            if (position + 1 < siblings.size()) {
                body.append(" /Next ").append(itemId(rootId, siblings.get(position + 1))).append(" 0 R");
            }
            if (!entry.children().isEmpty()) {
                List<Integer> children = entry.children();
                body.append(" /First ").append(itemId(rootId, children.get(0))).append(" 0 R")
                        .append(" /Last ").append(itemId(rootId, children.get(children.size() - 1)))
                        .append(" 0 R /Count ").append(outline.descendantCount(index));
            }
            body.append(" /Dest [").append(entry.pageObject()).append(" 0 R /XYZ null ")
                    .append(coord(entry.y())).append(" null] >>");
            writer.object(itemId(rootId, index), body.toString());
        }
    }

    private static int itemId(int rootId, int index) {
        return rootId + 1 + index;
    }

    /** Where each HTML {@code id} anchor landed; the first occurrence of a name wins. */
    private static Map<String, Destination> namedDestinations(List<Page> pages, int firstPageId) {
        Map<String, Destination> destinations = new HashMap<>();
        for (int index = 0; index < pages.size(); index++) {
            for (AnchorMark anchor : pages.get(index).anchors()) {
                if (anchor.name() != null) {
                    destinations.putIfAbsent(anchor.name(), new Destination(firstPageId + index * 2, anchor.y()));
                }
            }
        }
        return destinations;
    }

    /**
     * Plans one link annotation, or returns {@code null} for a link that cannot
     * be honoured: an empty target, or a {@code #fragment} whose anchor is not in
     * the Document. A dead fragment gets no annotation rather than one that goes
     * nowhere when clicked.
     */
    private static AnnotationPlan planAnnotation(
            LinkArea area, RenderContext context, Map<String, Destination> named, int objectId) {
        String target = context.link(area.link());
        if (target == null || target.isEmpty()) {
            return null;
        }
        if (target.startsWith("#")) {
            Destination destination = named.get(target.substring(1));
            return destination == null ? null : new AnnotationPlan(objectId, area.rect(), null, destination);
        }
        return new AnnotationPlan(objectId, area.rect(), target, null);
    }

    /** Where in the document a {@code #fragment} resolves to. */
    private record Destination(int pageObject, float y) {}

    /** One planned {@code /Link} annotation; exactly one of {@code uri} and {@code destination} is set. */
    private record AnnotationPlan(int objectId, Rect rect, String uri, Destination destination) {}

    /** The object ids one image and its optional soft mask occupy. */
    private record ImagePlan(String name, int objectId, int softMaskId) {}
}
