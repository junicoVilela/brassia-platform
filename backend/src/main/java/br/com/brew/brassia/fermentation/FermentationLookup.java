package br.com.brew.brassia.fermentation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * O que a fermentação sabe de um lote, publicado para outros módulos (FER-002 / FER-004).
 *
 * <p><strong>Existe porque a avaliação de risco estava cega justamente onde mora o risco.</strong> Quem
 * julga um lote via volume, receita, medições de qualidade e custo — e não via curva, agenda nem levedura.
 * Um lote com três etapas atrasadas e densidade parada há dois dias parecia idêntico a um lote saudável.
 *
 * <p><strong>Devolve o estado, não a série.</strong> A tentação é publicar a curva inteira, e ela ficou
 * cara desde que a telemetria alimenta a fermentação: um dispositivo de 30 segundos gera 2.880 pontos por
 * dia, e uma fermentação de duas semanas passa de 40 mil. Carregar tudo para descobrir o último valor seria
 * varrer a tabela a cada avaliação. Quem precisa da série inteira usa o endpoint de leituras, que existe e
 * pagina.
 *
 * <p><strong>Ausência viaja como ausência.</strong> "Ninguém mediu" e "não há levedura vinculada" voltam
 * nulos, não zerados — um zero no lugar da ausência faria a avaliação ler um lote não medido como um lote
 * com densidade zero, e um lote de levedura nova como geração zero.
 */
public interface FermentationLookup {

    /**
     * @return o estado de fermentação do lote, ou vazio quando o lote não tem nada registrado — nem
     *         leitura, nem agenda, nem levedura. Vazio é diferente de um retrato com tudo nulo: o primeiro
     *         diz "a fermentação não conhece este lote", o segundo diz "conhece e não há o que contar".
     */
    Optional<Snapshot> ofBatch(UUID breweryId, UUID batchId);

    /**
     * @param readingCount    quantas leituras o lote tem, de qualquer origem
     * @param lastDensity     última densidade medida; {@code null} se nenhuma
     * @param lastTemperature última temperatura medida; {@code null} se nenhuma
     * @param doneSteps       etapas da agenda já executadas
     * @param totalSteps      etapas planejadas; {@code 0} quando não há agenda
     * @param lateSteps       etapas pendentes fora da janela — o sinal mais direto de lote em apuros
     * @param yeastGeneration geração da levedura inoculada; {@code null} quando não há colheita vinculada.
     *                        Geração alta é fator de risco conhecido: a levedura perde viabilidade e
     *                        muda de comportamento a cada reuso.
     */
    record Snapshot(int readingCount, Measurement lastDensity, Measurement lastTemperature,
            int doneSteps, int totalSteps, int lateSteps, Integer yeastGeneration) {}

    /** Um valor medido, com a unidade em que foi medido e quando. */
    record Measurement(BigDecimal value, String unit, Instant measuredAt) {}
}
