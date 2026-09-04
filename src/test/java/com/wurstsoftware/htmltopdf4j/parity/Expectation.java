package com.wurstsoftware.htmltopdf4j.parity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The recorded assertions a Fixture's rendered output must satisfy, ported
 * verbatim from the reference engine and authoritative for Parity.
 *
 * <p>They are structural rather than byte snapshots — required operators,
 * required text, page count, size bounds — which is precisely what makes them
 * portable to a different implementation. Parity is behavioural; byte-identical
 * output was never the goal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Expectation(
        String fixture,
        String layer,
        String description,
        @JsonProperty("pdf_assertions") PdfAssertions pdfAssertions,
        @JsonProperty("visual_assertions") List<String> visualAssertions) {

    /**
     * @param mustContainOperators content-stream operators that must appear
     * @param mustContainText strings that must be present in the inflated streams
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PdfAssertions(
            @JsonProperty("must_contain_operators") List<String> mustContainOperators,
            @JsonProperty("must_contain_text") List<String> mustContainText,
            @JsonProperty("min_size_bytes") Long minSizeBytes,
            @JsonProperty("max_size_bytes") Long maxSizeBytes,
            @JsonProperty("min_pages") Integer minPages) {

        public List<String> mustContainOperators() {
            return mustContainOperators == null ? List.of() : mustContainOperators;
        }

        public List<String> mustContainText() {
            return mustContainText == null ? List.of() : mustContainText;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Reads the Expectation for a Fixture. The file name is {@code layer_name.json},
     * exactly as the reference engine names them.
     */
    public static Expectation load(Fixture fixture) {
        String resource = "/fixtures/expectations/" + fixture.layer() + "_" + fixture.name() + ".json";
        try (InputStream stream = Expectation.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("no Expectation for Fixture " + fixture + " at " + resource);
            }
            return MAPPER.readValue(stream, Expectation.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read Expectation " + resource, e);
        }
    }
}
