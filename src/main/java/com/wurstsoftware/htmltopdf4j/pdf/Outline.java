package com.wurstsoftware.htmltopdf4j.pdf;

import com.wurstsoftware.htmltopdf4j.layout.AnchorMark;
import com.wurstsoftware.htmltopdf4j.layout.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * The bookmark tree, built from the Document's headings in document order.
 *
 * <p>Each heading nests under the closest preceding heading of a shallower
 * level, so an {@code <h3>} that follows an {@code <h1>} with no {@code <h2>}
 * between them nests directly under the {@code <h1>} rather than breaking the
 * tree.
 */
final class Outline {

    record Entry(int level, String title, int pageObject, float y, int parent, List<Integer> children) {}

    private final List<Entry> entries;

    private Outline(List<Entry> entries) {
        this.entries = entries;
    }

    static Outline of(List<Page> pages, IntUnaryOperator pageObjectId) {
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < pages.size(); index++) {
            for (AnchorMark anchor : pages.get(index).anchors()) {
                if (anchor.isOutlineEntry()) {
                    entries.add(new Entry(
                            anchor.level(),
                            anchor.title(),
                            pageObjectId.applyAsInt(index),
                            anchor.y(),
                            -1,
                            new ArrayList<>()));
                }
            }
        }
        return new Outline(link(entries));
    }

    /** Walks the flat list with a stack of open ancestors, closing any at or below each level. */
    private static List<Entry> link(List<Entry> flat) {
        List<Entry> linked = new ArrayList<>(flat);
        List<Integer> openAncestors = new ArrayList<>();
        for (int index = 0; index < linked.size(); index++) {
            Entry entry = linked.get(index);
            while (!openAncestors.isEmpty()
                    && linked.get(openAncestors.get(openAncestors.size() - 1)).level() >= entry.level()) {
                openAncestors.remove(openAncestors.size() - 1);
            }
            if (!openAncestors.isEmpty()) {
                int parent = openAncestors.get(openAncestors.size() - 1);
                linked.set(index, new Entry(
                        entry.level(), entry.title(), entry.pageObject(), entry.y(), parent, entry.children()));
                linked.get(parent).children().add(index);
            }
            openAncestors.add(index);
        }
        return linked;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    int size() {
        return entries.size();
    }

    List<Entry> entries() {
        return entries;
    }

    /** The indices of the entries directly under {@code parent} ({@code -1} = the root). */
    List<Integer> childrenOf(int parent) {
        List<Integer> siblings = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).parent() == parent) {
                siblings.add(index);
            }
        }
        return siblings;
    }

    /** Every entry beneath {@code index}, which is what an open {@code /Count} reports. */
    int descendantCount(int index) {
        int total = 0;
        for (int child : entries.get(index).children()) {
            total += 1 + descendantCount(child);
        }
        return total;
    }
}
