package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Keeping a box whole across a Page boundary.
 *
 * <p>`page-break-inside: avoid` is a hint, not a constraint: a box that would be
 * split moves to the next Page, but a box that could not fit on any Page breaks
 * as if the property were absent rather than leaving a Page half empty.
 */
class BreakInsideTest {

    /** Enough content to leave about 95pt of the first Page free. */
    private static final String FILLER = "<div style='height:650pt'>TOP</div>";

    /** Eight lines, about 160pt tall — more than the space FILLER leaves. */
    private static String block(String style) {
        StringBuilder html = new StringBuilder("<div style='" + style + "'>");
        for (int i = 1; i <= 8; i++) {
            html.append("<p style='margin:0; font-size:12pt; line-height:20pt'>LINE").append(i).append("</p>");
        }
        return html.append("</div>").toString();
    }

    @Test
    void aBoxThatWouldBeSplitMovesToTheNextPageWhole() {
        Laid laid = Laid.of(FILLER + block("page-break-inside: avoid"));

        assertEquals(laid.pageOf("LINE1"), laid.pageOf("LINE8"), "the box should not be split");
        assertEquals(1, laid.pageOf("LINE1"), "and it should be on the Page after the filler");
    }

    @Test
    void withoutTheDeclarationTheSameBoxIsSplit() {
        Laid laid = Laid.of(FILLER + block(""));

        assertTrue(laid.pageOf("LINE8") > laid.pageOf("LINE1"), "an ordinary box breaks where it lands");
    }

    @Test
    void aBoxTallerThanAPageIsBrokenAsIfTheDeclarationWereAbsent() {
        // `avoid` is a hint. A box that cannot fit on any Page has to break
        // somewhere, and pushing it to a fresh Page first would only waste one.
        StringBuilder tall = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            tall.append("<p style='margin:0; line-height:20pt'>TALL").append(i).append("</p>");
        }
        Laid avoided = Laid.of(FILLER + "<div style='page-break-inside: avoid'>" + tall + "</div>");
        Laid plain = Laid.of(FILLER + "<div>" + tall + "</div>");

        assertTrue(avoided.pageOf("TALL60") > avoided.pageOf("TALL1"), "it still has to break");
        assertEquals(plain.pageCount(), avoided.pageCount(), "and it wastes no Page doing so");
    }

    @Test
    void avoidColumnIsNotAvoidPage() {
        // This engine has no columns, so there is nothing for `avoid-column` to
        // avoid; treating it as `avoid-page` would break Pages nobody asked for.
        Laid laid = Laid.of(FILLER + block("break-inside: avoid-column"));

        assertTrue(laid.pageOf("LINE8") > laid.pageOf("LINE1"));
    }

    @Test
    void theModernSpellingsAreHonoured() {
        for (String declaration : new String[] {"break-inside: avoid", "break-inside: avoid-page"}) {
            Laid laid = Laid.of(FILLER + block(declaration));
            assertEquals(laid.pageOf("LINE1"), laid.pageOf("LINE8"), declaration + " should keep the box whole");
        }
    }

    @Test
    void theDeclarationDoesNotInherit() {
        // An `avoid` on a section must not quietly make every box inside it
        // unbreakable; the inner box here has no declaration of its own.
        Laid laid = Laid.of(
                "<div style='page-break-inside: avoid'>" + FILLER + block("") + "</div>");

        assertTrue(laid.pageOf("LINE8") > laid.pageOf("LINE1"));
    }

    @Test
    void anUnfittableBoxStillHonoursTheChildrenThatCanFit() {
        // The outer box cannot be kept whole, but that is no reason to throw
        // away what its children asked for.
        StringBuilder cards = new StringBuilder("<div style='page-break-inside: avoid'>");
        for (int card = 1; card <= 8; card++) {
            cards.append("<div style='page-break-inside: avoid'>");
            for (int line = 1; line <= 5; line++) {
                cards.append("<p style='margin:0; line-height:20pt'>C").append(card).append("L").append(line)
                        .append("</p>");
            }
            cards.append("</div>");
        }
        Laid laid = Laid.of(cards.append("</div>").toString());

        for (int card = 1; card <= 8; card++) {
            assertEquals(laid.pageOf("C" + card + "L1"), laid.pageOf("C" + card + "L5"),
                    "card " + card + " should be whole");
        }
    }

    @Test
    void aForcedBreakInsideAnAvoidedBoxStillFires() {
        // A forced break is an instruction and `avoid` is only a hint, so the
        // instruction wins.
        Laid laid = Laid.of(
                "<div style='page-break-inside: avoid'>"
                        + "<p>BEFORE</p><p style='page-break-before: always'>AFTER</p></div>");

        assertTrue(laid.pageOf("AFTER") > laid.pageOf("BEFORE"));
    }

    @Test
    void aTableThatWouldBeSplitMovesToTheNextPageWhole() {
        StringBuilder table = new StringBuilder(
                "<table style='page-break-inside: avoid'><thead><tr><td>HEAD</td></tr></thead><tbody>");
        for (int i = 1; i <= 8; i++) {
            table.append("<tr><td style='line-height:20pt'>ROW").append(i).append("</td></tr>");
        }
        Laid laid = Laid.of(FILLER + table.append("</tbody></table>").toString());

        assertEquals(laid.pageOf("ROW1"), laid.pageOf("ROW8"), "the table should not be split");
        assertEquals(1, laid.pageOf("ROW1"));
    }

    @Test
    void aGridRowMovesWholeRatherThanTearing() {
        // Breaking one item onto the next Page while its neighbours stay behind
        // would leave the row torn in half, so the whole row moves.
        StringBuilder grid = new StringBuilder(
                FILLER + "<div style='display:grid; grid-template-columns:1fr 1fr'>");
        for (String item : new String[] {"LEFT", "RIGHT"}) {
            grid.append("<div style='page-break-inside: avoid'>");
            for (int line = 1; line <= 7; line++) {
                grid.append("<p style='margin:0; line-height:20pt'>").append(item).append(line).append("</p>");
            }
            grid.append("</div>");
        }
        Laid laid = Laid.of(grid.append("</div>").toString());

        assertEquals(laid.pageOf("LEFT1"), laid.pageOf("RIGHT1"), "the row should stay together");
        assertEquals(laid.pageOf("LEFT1"), laid.pageOf("LEFT7"), "and each item should be whole");
        assertEquals(1, laid.pageOf("LEFT1"));
    }

    @Test
    void aFlexLineMovesWholeRatherThanTearing() {
        StringBuilder flex = new StringBuilder(FILLER + "<div style='display:flex'>");
        for (String item : new String[] {"ONE", "TWO"}) {
            flex.append("<div style='page-break-inside: avoid'>");
            for (int line = 1; line <= 7; line++) {
                flex.append("<p style='margin:0; line-height:20pt'>").append(item).append(line).append("</p>");
            }
            flex.append("</div>");
        }
        Laid laid = Laid.of(flex.append("</div>").toString());

        assertEquals(laid.pageOf("ONE1"), laid.pageOf("TWO1"), "the line should stay together");
        assertEquals(laid.pageOf("ONE1"), laid.pageOf("ONE7"));
        assertEquals(1, laid.pageOf("ONE1"));
    }

    @Test
    void aBlockImageIsNeverSplitWhateverTheDeclarationSays() {
        // Images are already atomic — `ensureSpace` moves one that does not fit
        // onto the next Page whole. This pins that, since nothing else does.
        String uri = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png());
        Laid laid = Laid.of(FILLER + "<img src='" + uri + "' style='width:300pt; height:300pt'>");

        assertEquals(1, imagePage(laid), "the image should move whole to the next Page");
    }

    private static int imagePage(Laid laid) {
        for (int page = 0; page < laid.pageCount(); page++) {
            if (laid.commands(page).stream()
                    .anyMatch(com.wurstsoftware.htmltopdf4j.paint.PaintCommand.Image.class::isInstance)) {
                return page;
            }
        }
        throw new AssertionError("no image was painted");
    }

    private static byte[] png() {
        try {
            java.awt.image.BufferedImage image =
                    new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
