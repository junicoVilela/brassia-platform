package br.com.brew.brassia.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.catalog.domain.SubstitutionRanker.Candidate;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubstitutionRankerTest {

    private final SubstitutionRanker ranker = new SubstitutionRanker();

    private static PropertyRange range(String min, String max) {
        return new PropertyRange(new BigDecimal(min), new BigDecimal(max), "%");
    }

    private static Map<String, PropertyRange> ranges(Object... kv) {
        var map = new LinkedHashMap<String, PropertyRange>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], (PropertyRange) kv[i + 1]);
        }
        return map;
    }

    @Test
    void ranksClosestCandidateFirst() {
        var target = ranges("alphaAcid", range("5.5", "7.5"), "cohumulone", range("20", "25"));
        var near = new Candidate(UUID.randomUUID(), "SIM", "Similar", "Fonte A",
                ranges("alphaAcid", range("5.6", "7.4"), "cohumulone", range("21", "24")));
        var far = new Candidate(UUID.randomUUID(), "FAR", "Distante", "Fonte B",
                ranges("alphaAcid", range("12", "14"), "cohumulone", range("21", "24")));

        var result = ranker.rank(target, List.of(far, near));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).code()).isEqualTo("SIM");
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
        assertThat(result.get(0).confidence()).isEqualTo("MEDIUM");
        assertThat(result.get(0).sourceName()).isEqualTo("Fonte A");
        assertThat(result.get(0).comparisons()).allMatch(PropertyComparison::similar);
    }

    @Test
    void noComparablePropertiesYieldsZeroScore() {
        var target = ranges("alphaAcid", range("5.5", "7.5"));
        var candidate = new Candidate(UUID.randomUUID(), "X", "Outro", null,
                ranges("diastaticPower", range("100", "120")));

        var result = ranker.rank(target, List.of(candidate));

        assertThat(result).singleElement().satisfies(m -> {
            assertThat(m.score()).isEqualByComparingTo("0");
            assertThat(m.confidence()).isEqualTo("NONE");
            assertThat(m.comparisons()).isEmpty();
        });
    }

    @Test
    void differencesAreFlaggedNotSimilar() {
        var target = ranges("alphaAcid", range("5.5", "7.5"));
        var candidate = new Candidate(UUID.randomUUID(), "HI", "Alto alfa", "Fonte",
                ranges("alphaAcid", range("12", "14")));

        var result = ranker.rank(target, List.of(candidate));

        assertThat(result).singleElement().satisfies(m -> {
            assertThat(m.comparisons()).singleElement().satisfies(c -> assertThat(c.similar()).isFalse());
            assertThat(m.score()).isEqualByComparingTo("0");
        });
    }
}
