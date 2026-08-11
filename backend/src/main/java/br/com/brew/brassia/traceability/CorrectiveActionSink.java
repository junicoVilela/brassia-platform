package br.com.brew.brassia.traceability;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Onde as ações corretivas de um simulado passam a ser acompanhadas (FDS-004-A).
 *
 * <p><strong>Declarada aqui e implementada lá</strong>, como o {@link LineageSource}. A direção não é
 * estética: uma porta em `quality` chamada por `traceability` fecharia o ciclo
 * `production → traceability → quality → production`, porque a qualidade depende da produção desde a
 * QLT-001 (alerta de lote). Invertendo, quem depende é quem implementa, e a rastreabilidade continua sem
 * saber que CAPA existe.
 *
 * <p>O que ela resolve: a ação corretiva do simulado era texto livre no relatório — sem dono, sem prazo e
 * sem aparecer em lista nenhuma. Seis meses depois o próximo simulado encontrava a mesma lacuna, com o
 * relatório anterior dizendo o que fazer, que é a definição de um exercício que não melhora nada.
 */
public interface CorrectiveActionSink {

    /**
     * Registra as ações no destino indicado.
     *
     * <p>O destino vem de fora, e não é criado aqui: criá-lo obrigaria a decidir a severidade da não
     * conformidade, e o quanto uma cobertura de 75% é grave depende do produto e de quem audita.
     *
     * @param targetId a não conformidade escolhida por quem encerra o simulado
     */
    void plan(UUID breweryId, UUID actorId, UUID targetId, List<CorrectiveAction> actions);

    /**
     * @param kind CORRECTIVE trata a ocorrência; PREVENTIVE trata a causa. Um plano só com corretiva se
     *             repete, e por isso o tipo é obrigatório
     * @param owner quem responde — é o campo que distingue uma ação de uma intenção
     */
    record CorrectiveAction(String kind, String description, String owner, LocalDate dueOn) {}
}
