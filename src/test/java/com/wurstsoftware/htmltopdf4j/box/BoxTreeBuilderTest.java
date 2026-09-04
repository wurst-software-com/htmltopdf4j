package com.wurstsoftware.htmltopdf4j.box;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wurstsoftware.htmltopdf4j.style.Cascade;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BoxTreeBuilderTest {

    private static BoxTree treeOf(String html) {
        Document document = Jsoup.parse(html);
        return BoxTreeBuilder.build(document, Cascade.apply(document, Cascade.authorStylesheet(document)));
    }

    /** Every text run in the tree, in document order — what the reader would see. */
    private static List<String> textOf(BoxTree tree) {
        List<String> text = new ArrayList<>();
        collectText(tree.children(), text);
        return text;
    }

    private static void collectText(List<BoxChild> children, List<String> text) {
        for (BoxChild child : children) {
            switch (child) {
                case BlockBox block -> collectText(block.children(), text);
                case LineBox line -> {
                    for (InlineRun run : line.runs()) {
                        if (run.isText()) {
                            text.add(run.text());
                        }
                        if (run.inlineBlock() != null) {
                            collectText(run.inlineBlock().children(), text);
                        }
                    }
                }
                case ImageBox image -> text.add("[img " + image.source() + "]");
                case TableBox table -> {
                    for (TableRow row : table.rows()) {
                        for (TableCell cell : row.cells()) {
                            collectText(cell.content().children(), text);
                        }
                    }
                }
            }
        }
    }

    private static <T> T only(List<BoxChild> children, Class<T> type) {
        assertEquals(1, children.size(), "expected exactly one child, got " + children);
        return assertInstanceOf(type, children.get(0));
    }

    // --- Structure ----------------------------------------------------------

    @Test
    void aParagraphBecomesABlockBoxWithOneLine() {
        BlockBox block = only(treeOf("<p>hello</p>").children(), BlockBox.class);
        assertEquals("p", block.tag());
        assertEquals(List.of("hello"), textOf(new BoxTree(block.children())));
    }

    @Test
    void nestedBlocksNestInTheBoxTree() {
        BlockBox outer = only(treeOf("<div><p>hi</p></div>").children(), BlockBox.class);
        assertEquals("div", outer.tag());
        assertEquals("p", only(outer.children(), BlockBox.class).tag());
    }

    @Test
    void anInlineElementDoesNotGenerateABoxOfItsOwn() {
        BlockBox block = only(treeOf("<p>a<em>b</em>c</p>").children(), BlockBox.class);
        LineBox line = only(block.children(), LineBox.class);
        assertEquals(List.of("a", "b", "c"), line.runs().stream().map(InlineRun::text).toList());
    }

    @Test
    void anInlineElementsRunKeepsItsOwnStyle() {
        BlockBox block = only(treeOf("<p>a<em>b</em></p>").children(), BlockBox.class);
        LineBox line = only(block.children(), LineBox.class);
        assertFalse(line.runs().get(0).style().italic());
        assertTrue(line.runs().get(1).style().italic());
    }

    @Test
    void textBesideABlockChildIsWrappedInItsOwnLine() {
        BlockBox block = only(treeOf("<div>before<p>inside</p>after</div>").children(), BlockBox.class);
        assertEquals(3, block.children().size());
        assertInstanceOf(LineBox.class, block.children().get(0));
        assertInstanceOf(BlockBox.class, block.children().get(1));
        assertInstanceOf(LineBox.class, block.children().get(2));
    }

    @Test
    void aDisplayNoneElementGeneratesNoBox() {
        assertEquals(List.of("kept"), textOf(treeOf("<p style='display:none'>gone</p><p>kept</p>")));
    }

    @Test
    void headAndScriptContentNeverReachTheBoxTree() {
        BoxTree tree = treeOf("<html><head><title>t</title><style>p{}</style></head>"
                + "<body><script>var x = 1</script><p>body</p></body></html>");
        assertEquals(List.of("body"), textOf(tree));
    }

    @Test
    void aCommentGeneratesNoBox() {
        assertEquals(List.of("a", "b"), textOf(treeOf("<p>a<!-- note -->b</p>")));
    }

    @Test
    void anEmptyDocumentHasNoContent() {
        assertFalse(treeOf("<html><body></body></html>").hasContent());
    }

    @Test
    void aWhitespaceOnlyDocumentHasNoContent() {
        assertFalse(treeOf("<html><body>   \n  </body></html>").hasContent());
    }

    // --- Whitespace ---------------------------------------------------------

    @Test
    void runsOfWhitespaceCollapseToOneSpace() {
        BlockBox block = only(treeOf("<p>a   \n\t b</p>").children(), BlockBox.class);
        assertEquals(List.of("a b"), textOf(new BoxTree(block.children())));
    }

    @Test
    void whitespaceBetweenInlineElementsSurvivesAsOneSpace() {
        BlockBox block = only(treeOf("<p><em>a</em> <em>b</em></p>").children(), BlockBox.class);
        LineBox line = only(block.children(), LineBox.class);
        assertEquals("a b", String.join("", line.runs().stream().map(InlineRun::text).toList()));
    }

    @Test
    void aSpaceIsNotEmittedTwiceAcrossAdjacentTextNodes() {
        BlockBox block = only(treeOf("<p>a <em> </em> b</p>").children(), BlockBox.class);
        LineBox line = only(block.children(), LineBox.class);
        assertEquals("a b", String.join("", line.runs().stream().map(InlineRun::text).toList()));
    }

    @Test
    void leadingAndTrailingWhitespaceIsStrippedFromALine() {
        BlockBox block = only(treeOf("<p>   a   </p>").children(), BlockBox.class);
        assertEquals(List.of("a"), textOf(new BoxTree(block.children())));
    }

    @Test
    void preservedWhitespaceKeepsItsSpacesAndBreaksAtEachNewline() {
        BlockBox block = only(treeOf("<pre>a  b\nc</pre>").children(), BlockBox.class);
        assertEquals(2, block.children().size());
        assertEquals(List.of("a  b", "c"), textOf(new BoxTree(block.children())));
    }

    // --- Forced breaks ------------------------------------------------------

    @Test
    void aBreakSplitsInlineContentIntoTwoLines() {
        BlockBox block = only(treeOf("<p>a<br>b</p>").children(), BlockBox.class);
        assertEquals(2, block.children().size());
        assertEquals(List.of("a", "b"), textOf(new BoxTree(block.children())));
    }

    @Test
    void aBreakOnAnEmptyLineStillProducesALine() {
        BlockBox block = only(treeOf("<p><br>a</p>").children(), BlockBox.class);
        assertEquals(2, block.children().size());
    }

    // --- Links --------------------------------------------------------------

    @Test
    void aRunInsideAnAnchorCarriesItsTarget() {
        BlockBox block = only(treeOf("<p><a href='https://x.test'>go</a></p>").children(), BlockBox.class);
        LineBox line = only(block.children(), LineBox.class);
        assertEquals("https://x.test", line.runs().get(0).link());
    }

    @Test
    void aLinkTargetReachesRunsNestedInsideTheAnchor() {
        BlockBox block = only(treeOf("<p><a href='#a'><em>go</em></a></p>").children(), BlockBox.class);
        assertEquals("#a", only(block.children(), LineBox.class).runs().get(0).link());
    }

    @Test
    void textOutsideAnAnchorCarriesNoTarget() {
        BlockBox block = only(treeOf("<p>plain</p>").children(), BlockBox.class);
        assertNull(only(block.children(), LineBox.class).runs().get(0).link());
    }

    @Test
    void anElementIdBecomesAnAnchorDestination() {
        BlockBox block = only(treeOf("<div id=chapter>x</div>").children(), BlockBox.class);
        assertEquals("chapter", block.anchor());
    }

    // --- Images -------------------------------------------------------------

    @Test
    void anImageInTextIsAnInlineRun() {
        BlockBox block = only(treeOf("<p>a<img src=x.png alt=pic width=20>b</p>").children(), BlockBox.class);
        LineBox line = only(block.children(), LineBox.class);
        InlineRun run = line.runs().get(1);
        assertNotNull(run.image());
        assertEquals("x.png", run.image().source());
        assertEquals("pic", run.image().alternativeText());
        assertEquals(20f, run.image().attributeWidth());
    }

    @Test
    void aBlockLevelImageIsItsOwnBox() {
        ImageBox image = only(treeOf("<img src=x.png style='display:block'>").children(), ImageBox.class);
        assertEquals("x.png", image.source());
    }

    @Test
    void aFloatedImageLeavesTheInlineFlow() {
        ImageBox image = only(treeOf("<img src=x.png style='float:left'>").children(), ImageBox.class);
        assertEquals("x.png", image.source());
    }

    // --- Inline blocks ------------------------------------------------------

    @Test
    void anInlineBlockIsAnAtomicRunHoldingAWholeBlockBox() {
        BlockBox block = only(treeOf("<p>a<span style='display:inline-block'>b</span></p>")
                .children(), BlockBox.class);
        LineBox line = only(block.children(), LineBox.class);
        InlineRun run = line.runs().get(1);
        assertNotNull(run.inlineBlock());
        assertEquals(List.of("b"), textOf(new BoxTree(run.inlineBlock().children())));
    }

    // --- List markers -------------------------------------------------------

    @Test
    void unorderedListItemsGetABullet() {
        BlockBox list = only(treeOf("<ul><li>a</li><li>b</li></ul>").children(), BlockBox.class);
        assertEquals(List.of("•", "•"),
                list.children().stream().map(child -> ((BlockBox) child).marker()).toList());
    }

    @Test
    void orderedListItemsAreNumberedInSequence() {
        BlockBox list = only(treeOf("<ol><li>a</li><li>b</li><li>c</li></ol>").children(), BlockBox.class);
        assertEquals(List.of("1.", "2.", "3."),
                list.children().stream().map(child -> ((BlockBox) child).marker()).toList());
    }

    @Test
    void anOrderedListHonoursItsStartAttribute() {
        BlockBox list = only(treeOf("<ol start=5><li>a</li><li>b</li></ol>").children(), BlockBox.class);
        assertEquals(List.of("5.", "6."),
                list.children().stream().map(child -> ((BlockBox) child).marker()).toList());
    }

    @Test
    void anItemValueAttributeRenumbersTheItemsAfterIt() {
        BlockBox list = only(treeOf("<ol><li>a</li><li value=9>b</li><li>c</li></ol>").children(), BlockBox.class);
        assertEquals(List.of("1.", "9.", "10."),
                list.children().stream().map(child -> ((BlockBox) child).marker()).toList());
    }

    @Test
    void romanAndAlphabeticMarkersFollowTheirListStyleType() {
        BlockBox list = only(treeOf("<ol style='list-style-type:upper-roman'><li>a</li>"
                + "<li>b</li><li>c</li><li>d</li></ol>").children(), BlockBox.class);
        assertEquals(List.of("I.", "II.", "III.", "IV."),
                list.children().stream().map(child -> ((BlockBox) child).marker()).toList());
    }

    @Test
    void alphabeticMarkersRollOverPastTwentySix() {
        BlockBox list = only(treeOf("<ol style='list-style-type:lower-alpha'><li value=27>a</li></ol>")
                .children(), BlockBox.class);
        assertEquals("aa.", ((BlockBox) list.children().get(0)).marker());
    }

    @Test
    void listStyleTypeNoneSuppressesTheMarker() {
        BlockBox list = only(treeOf("<ul style='list-style-type:none'><li>a</li></ul>")
                .children(), BlockBox.class);
        assertNull(((BlockBox) list.children().get(0)).marker());
    }

    // --- Tables -------------------------------------------------------------

    @Test
    void aTableFlattensItsRowsOutOfTheirSections() {
        TableBox table = only(treeOf("<table><thead><tr><th>h</th></tr></thead>"
                + "<tbody><tr><td>b</td></tr></tbody>"
                + "<tfoot><tr><td>f</td></tr></tfoot></table>").children(), TableBox.class);
        assertEquals(
                List.of(TableRow.Section.HEADER, TableRow.Section.BODY, TableRow.Section.FOOTER),
                table.rows().stream().map(TableRow::section).toList());
        assertEquals(List.of("h", "b", "f"), textOf(new BoxTree(List.of(table))));
    }

    @Test
    void rowsWithNoSectionElementAreBodyRows() {
        TableBox table = only(treeOf("<table><tr><td>a</td></tr></table>").children(), TableBox.class);
        assertEquals(TableRow.Section.BODY, table.rows().get(0).section());
    }

    @Test
    void cellSpansComeFromTheirAttributes() {
        TableBox table = only(treeOf("<table><tr><td colspan=3 rowspan=2>a</td></tr></table>")
                .children(), TableBox.class);
        TableCell cell = table.rows().get(0).cells().get(0);
        assertEquals(3, cell.columnSpan());
        assertEquals(2, cell.rowSpan());
        assertEquals(3, table.columnCount());
    }

    @Test
    void aHeaderCellIsMarkedAsOne() {
        TableBox table = only(treeOf("<table><tr><th>a</th><td>b</td></tr></table>")
                .children(), TableBox.class);
        assertTrue(table.rows().get(0).cells().get(0).header());
        assertFalse(table.rows().get(0).cells().get(1).header());
    }

    @Test
    void aCellHoldsWholeBlocksRatherThanOnlyText() {
        TableBox table = only(treeOf("<table><tr><td><p>a</p><p>b</p></td></tr></table>")
                .children(), TableBox.class);
        assertEquals(2, table.rows().get(0).cells().get(0).content().children().size());
    }

    @Test
    void declaredColumnWidthsAreKept() {
        TableBox table = only(treeOf("<table><colgroup><col width=100><col width=200></colgroup>"
                + "<tr><td>a</td><td>b</td></tr></table>").children(), TableBox.class);
        assertEquals(List.of(100f, 200f), table.columnWidths());
    }

    @Test
    void aTableWithNoDeclaredColumnWidthsSizesEveryColumnAutomatically() {
        TableBox table = only(treeOf("<table><tr><td>a</td></tr></table>").children(), TableBox.class);
        assertTrue(table.columnWidths().isEmpty());
    }

    @Test
    void aTableInFlowContentKeepsItsPlaceInDocumentOrder() {
        BoxTree tree = treeOf("<p>before</p><table><tr><td>cell</td></tr></table><p>after</p>");
        assertEquals(3, tree.children().size());
        assertInstanceOf(TableBox.class, tree.children().get(1));
        assertEquals(List.of("before", "cell", "after"), textOf(tree));
    }
}
