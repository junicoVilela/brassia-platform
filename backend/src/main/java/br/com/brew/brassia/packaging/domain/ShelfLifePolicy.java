package br.com.brew.brassia.packaging.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Política de vida útil da cervejaria (FSL-001): faixas de oxigênio total da embalagem (TPO) e
 * quantos dias cada faixa sustenta.
 *
 * <p>Os números vêm da cervejaria, não do sistema. TPO é o que mais empurra o envelhecimento, mas
 * traduzir ppb em dias depende do estilo, da temperatura de estocagem e do padrão de frescor que a
 * casa aceita — inventar uma tabela aqui seria dar precisão a um palpite. Sem política configurada
 * não há recomendação, e a validade passa a ser decisão humana registrada.
 *
 * @param tiers        faixas por TPO máximo, aplicadas da mais limpa para a mais suja
 * @param fallbackDays validade quando o TPO medido passa de todas as faixas (pior caso da casa)
 */
public record ShelfLifePolicy(List<Tier> tiers, int fallbackDays) {

    /** Faixa de TPO e a validade que ela sustenta. */
    public record Tier(BigDecimal maxTpoPpb, int shelfLifeDays) {

        public Tier {
            Objects.requireNonNull(maxTpoPpb, "TPO máximo da faixa é obrigatório");
            if (maxTpoPpb.signum() <= 0) {
                throw new IllegalArgumentException("TPO máximo da faixa deve ser positivo");
            }
            if (shelfLifeDays < 1) {
                throw new IllegalArgumentException("validade da faixa deve ser positiva (dias)");
            }
        }
    }

    public ShelfLifePolicy {
        Objects.requireNonNull(tiers, "faixas são obrigatórias");
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("política sem faixa não recomenda nada");
        }
        if (fallbackDays < 1) {
            throw new IllegalArgumentException("validade de pior caso deve ser positiva (dias)");
        }
        tiers = tiers.stream().sorted(Comparator.comparing(Tier::maxTpoPpb)).toList();
        // Faixa mais suja não pode prometer mais dias que uma mais limpa: a curva só desce.
        for (int i = 1; i < tiers.size(); i++) {
            if (tiers.get(i).maxTpoPpb().compareTo(tiers.get(i - 1).maxTpoPpb()) == 0) {
                throw new IllegalArgumentException("faixas de TPO repetidas");
            }
            if (tiers.get(i).shelfLifeDays() > tiers.get(i - 1).shelfLifeDays()) {
                throw new IllegalArgumentException("mais oxigênio não pode render mais validade");
            }
        }
        if (fallbackDays > tiers.getLast().shelfLifeDays()) {
            throw new IllegalArgumentException("o pior caso não pode render mais que a última faixa");
        }
    }

    /** Primeira faixa que comporta o TPO medido; vazio quando ele passa de todas. */
    public Optional<Tier> tierFor(BigDecimal tpoPpb) {
        Objects.requireNonNull(tpoPpb, "TPO é obrigatório");
        return tiers.stream().filter(tier -> tpoPpb.compareTo(tier.maxTpoPpb()) <= 0).findFirst();
    }

    /** Dias sustentados pelo TPO medido, caindo para o pior caso quando nenhuma faixa comporta. */
    public int shelfLifeDaysFor(BigDecimal tpoPpb) {
        return tierFor(tpoPpb).map(Tier::shelfLifeDays).orElse(fallbackDays);
    }
}
