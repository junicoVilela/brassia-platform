package br.com.brew.brassia.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranqueia substitutos por proximidade das propriedades técnicas (REC-010).
 * Determinístico — a IA não calcula score nem inventa propriedades. Compara os
 * pontos médios das faixas por propriedade compartilhada; estoque pode filtrar os
 * candidatos, mas não altera a equivalência técnica.
 */
public final class SubstitutionRanker {

    /** Tolerância relativa para considerar uma propriedade "similar". */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.15");

    public record Candidate(UUID ingredientId, String code, String name, String sourceName,
            Map<String, PropertyRange> ranges) {}

    public List<SubstitutionMatch> rank(Map<String, PropertyRange> targetRanges, List<Candidate> candidates) {
        var matches = new ArrayList<SubstitutionMatch>();
        for (Candidate candidate : candidates) {
            matches.add(evaluate(targetRanges, candidate));
        }
        matches.sort(Comparator.comparing(SubstitutionMatch::score).reversed());
        return matches;
    }

    private SubstitutionMatch evaluate(Map<String, PropertyRange> targetRanges, Candidate candidate) {
        var comparisons = new ArrayList<PropertyComparison>();
        int comparable = 0;
        int similar = 0;
        for (var entry : targetRanges.entrySet()) {
            BigDecimal targetMid = midpoint(entry.getValue());
            PropertyRange candidateRange = candidate.ranges().get(entry.getKey());
            BigDecimal candidateMid = candidateRange == null ? null : midpoint(candidateRange);
            if (targetMid == null || candidateMid == null) {
                continue;
            }
            comparable++;
            boolean isSimilar = similar(targetMid, candidateMid);
            if (isSimilar) {
                similar++;
            }
            comparisons.add(new PropertyComparison(entry.getKey(), targetMid, candidateMid,
                    entry.getValue().unit(), isSimilar));
        }
        BigDecimal score = comparable == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(similar).divide(BigDecimal.valueOf(comparable), 2, RoundingMode.HALF_UP);
        return new SubstitutionMatch(candidate.ingredientId(), candidate.code(), candidate.name(),
                candidate.sourceName(), score, confidence(comparable), comparisons);
    }

    private static boolean similar(BigDecimal target, BigDecimal candidate) {
        BigDecimal base = target.abs().max(new BigDecimal("0.0001"));
        BigDecimal relative = target.subtract(candidate).abs().divide(base, 6, RoundingMode.HALF_UP);
        return relative.compareTo(TOLERANCE) <= 0;
    }

    private static String confidence(int comparable) {
        if (comparable >= 4) {
            return "HIGH";
        }
        if (comparable >= 2) {
            return "MEDIUM";
        }
        if (comparable == 1) {
            return "LOW";
        }
        return "NONE";
    }

    private static BigDecimal midpoint(PropertyRange range) {
        if (range == null) {
            return null;
        }
        if (range.min() != null && range.max() != null) {
            return range.min().add(range.max()).divide(new BigDecimal("2"), 6, RoundingMode.HALF_UP);
        }
        return range.min() != null ? range.min() : range.max();
    }
}
