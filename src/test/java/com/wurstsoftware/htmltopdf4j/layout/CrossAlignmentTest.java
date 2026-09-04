package com.wurstsoftware.htmltopdf4j.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.paint.Rect;
import org.junit.jupiter.api.Test;

/**
 * Where a flex item or a grid item sits across its line or its track.
 *
 * <p>`align-items` and `align-self` share one set of keywords with a table
 * cell's `vertical-align`, and `stretch` — the initial value — is the odd one
 * out: it is not an offset but a floor under the item's height.
 */
class CrossAlignmentTest {

    /** The painted box of the item marked with a background. */
    private static Rect box(Laid laid) {
        return laid.fills().stream()
                .max((a, b) -> Float.compare(a.width() * a.height(), b.width() * b.height()))
                .orElseThrow(() -> new AssertionError("nothing was painted"));
    }

    private static String flex(String containerStyle, String itemStyle) {
        return "<div style='display:flex; " + containerStyle + "'>"
                + "<div style='width:100pt; height:100pt'>TALL</div>"
                + "<div style='width:100pt; background:#ff0; " + itemStyle + "'>SHORT</div>"
                + "</div>";
    }

    @Test
    void aFlexItemIsStretchedToItsLineByDefault() {
        assertEquals(100f, box(Laid.of(flex("", ""))).height(), 1f,
                "stretch is the initial value of align-items, and it really stretches");
    }

    @Test
    void alignItemsCenterCentresAShortItemInItsLine() {
        Laid top = Laid.of(flex("align-items:flex-start", ""));
        Laid centred = Laid.of(flex("align-items:center", ""));

        assertTrue(centred.text("SHORT").y() < top.text("SHORT").y() - 20f,
                "a centred item sits well below a start-aligned one");
        assertTrue(centred.text("SHORT").y() > top.text("SHORT").y() - 80f,
                "but not as far down as an end-aligned one");
    }

    @Test
    void alignItemsFlexEndDropsTheItemToTheFootOfTheLine() {
        Laid centred = Laid.of(flex("align-items:center", ""));
        Laid end = Laid.of(flex("align-items:flex-end", ""));

        assertTrue(end.text("SHORT").y() < centred.text("SHORT").y(), "end is below centre");
    }

    @Test
    void alignSelfOverridesTheContainersAlignItems() {
        Laid inherited = Laid.of(flex("align-items:flex-start", ""));
        Laid overridden = Laid.of(flex("align-items:flex-start", "align-self:flex-end"));

        assertTrue(overridden.text("SHORT").y() < inherited.text("SHORT").y(),
                "the item's own alignment wins over the container's");
    }

    @Test
    void aFlexItemWithADeclaredHeightIsNotStretched() {
        assertEquals(30f, box(Laid.of(flex("", "height:30pt"))).height(), 1f,
                "a declared height is the item's own business");
    }

    @Test
    void alignContentDistributesTheLinesOfAWrappedContainer() {
        String wrapped = "<div style='display:flex; flex-wrap:wrap; height:400pt; width:200pt; %s'>"
                + "<div style='width:150pt'>LINEONE</div>"
                + "<div style='width:150pt'>LINETWO</div></div>";
        Laid packed = Laid.of(String.format(wrapped, "align-content:flex-start"));
        Laid centred = Laid.of(String.format(wrapped, "align-content:center"));
        Laid ended = Laid.of(String.format(wrapped, "align-content:flex-end"));

        assertTrue(centred.text("LINEONE").y() < packed.text("LINEONE").y() - 50f,
                "centring the lines pushes the first one down the container");
        assertTrue(ended.text("LINEONE").y() < centred.text("LINEONE").y(),
                "and packing them at the end pushes it further");
    }

    private static String grid(String containerStyle, String itemStyle) {
        return "<div style='display:grid; grid-template-columns:100pt 100pt; "
                + "grid-template-rows:120pt; " + containerStyle + "'>"
                + "<div>TALL</div>"
                + "<div style='background:#ff0; " + itemStyle + "'>SHORT</div>"
                + "</div>";
    }

    @Test
    void aGridItemIsStretchedToItsTrackByDefault() {
        assertEquals(120f, box(Laid.of(grid("", ""))).height(), 1f,
                "an item in a track taller than its content fills the track");
    }

    @Test
    void aGridItemWithADeclaredHeightIsNotStretched() {
        assertEquals(40f, box(Laid.of(grid("", "height:40pt"))).height(), 1f);
    }

    @Test
    void alignSelfStillPlacesAGridItemInItsTrack() {
        Laid stretched = Laid.of(grid("", ""));
        Laid centred = Laid.of(grid("", "align-self:center"));

        assertTrue(box(centred).height() < box(stretched).height(),
                "a centred item keeps its own height");
        assertTrue(centred.text("SHORT").y() < stretched.text("SHORT").y(),
                "and sits below the top of the track");
    }
}
