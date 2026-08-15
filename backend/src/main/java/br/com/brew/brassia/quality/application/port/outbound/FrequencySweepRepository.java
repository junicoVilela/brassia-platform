package br.com.brew.brassia.quality.application.port.outbound;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * O que a varredura de cadência precisa ler e gravar (QLT-001-A).
 *
 * <p>Consultas de varredura, e não de tela: elas atravessam a cervejaria inteira e são feitas por um
 * agendador, não por alguém esperando resposta.
 */
public interface FrequencySweepRepository {

    /** Cervejarias com plano de controle publicado — não adianta varrer quem não tem plano. */
    List<UUID> breweriesWithPublishedPlans();

    /**
     * Pontos por hora dos planos publicados que valem para a receita do lote.
     *
     * <p>Plano sem receita vale para todos: é assim que controle da casa (água, higiene) alcança
     * qualquer lote.
     */
    List<HourlyPoint> hourlyPointsFor(UUID breweryId, UUID recipeId);

    /** Quando o ponto foi medido pela última vez naquele lote; nulo quando nunca foi. */
    Instant lastMeasuredAt(UUID breweryId, UUID pointId, UUID batchId);

    /**
     * Grava que o atraso foi avisado.
     *
     * @return {@code false} quando esta janela já tinha sido avisada — quem decide é a restrição única,
     *         e é ela que impede a varredura de hora em hora repetir o mesmo aviso 24 vezes por dia
     */
    boolean recordAlert(UUID breweryId, UUID pointId, UUID batchId, Instant missedWindowAt, Instant at);

    /**
     * @param severity severidade do ponto, que vai no texto do alerta: quem lê a central precisa saber se
     *                 o atraso é de um controle crítico ou de um acompanhamento
     */
    record HourlyPoint(UUID pointId, String parameter, int everyHours, String severity, boolean critical) {}
}
