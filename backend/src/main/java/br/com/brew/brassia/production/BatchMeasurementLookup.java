package br.com.brew.brassia.production;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * As medições de um lote, para quem precisa da série e não do lote (SPC-001).
 *
 * <p><strong>Por que não bastava {@code quality.BatchQualityLookup}.</strong> Aquela consulta devolve as
 * medições que ficaram <em>fora</em> da faixa, porque responde a pergunta da qualidade: o que desviou. Um
 * gráfico de controle construído sobre ela veria só os pontos ruins — e um controle estatístico alimentado
 * apenas com os piores pontos calcula limites que não descrevem processo nenhum. Controle precisa da série
 * inteira, boa e ruim.
 *
 * <p>Consulta publicada, e não porta invertida: a produção não depende de quem analisa a série. Quem tem o
 * dado responde por ele — a mesma regra de {@link BatchLookup} e {@link BatchOutcomeLookup}.
 */
public interface BatchMeasurementLookup {

    /**
     * Medições de uma grandeza num lote, em ordem cronológica.
     *
     * <p>A ordem é parte do contrato, não detalhe da implementação: sequência e tendência só existem no
     * tempo, e uma lista ordenada por outra coisa produziria sinais que o processo nunca deu.
     *
     * @param kind grandeza como o módulo de produção a nomeia (DENSITY, TEMPERATURE, VOLUME, PH, COLOR, IBU)
     */
    List<Reading> ofBatch(UUID breweryId, UUID batchId, String kind);

    /** Uma medição, reduzida ao que uma análise de série precisa. */
    record Reading(BigDecimal value, String unit, Instant measuredAt) {}
}
