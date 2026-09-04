package com.wurstsoftware.htmltopdf4j.parity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The Fixtures known not to meet their Expectation yet.
 *
 * <p>The port lands one stage at a time, so most Fixtures fail for most of it.
 * A ledger keeps the build green without hiding that: a listed Fixture that
 * fails is reported as skipped, and a listed Fixture that <em>passes</em> fails
 * the build, which is what forces the ledger to shrink as the engine grows
 * rather than rotting into a list nobody reads.
 *
 * <p>An unlisted Fixture that fails is an ordinary failure. That is the whole
 * point: once a Fixture passes it can never quietly stop passing.
 */
final class KnownFailures {

    private static final Set<String> IDS = load();

    private KnownFailures() {}

    static boolean contains(String fixtureId) {
        return IDS.contains(fixtureId);
    }

    private static Set<String> load() {
        try (InputStream stream = KnownFailures.class.getResourceAsStream("/parity-known-failures.txt")) {
            if (stream == null) {
                return Set.of();
            }
            return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the parity known-failures ledger", e);
        }
    }
}
