package br.com.brew.brassia.ai.adapter.outbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.ai.domain.CommandProposal;
import br.com.brew.brassia.ai.domain.ProposedAction;
import br.com.brew.brassia.costing.BatchCostCommands;
import br.com.brew.brassia.sanitation.CleaningCycleCommands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A tradução de proposta confirmada em comando (DEB-AIA-002).
 *
 * <p>Três coisas se verificam aqui, e nenhuma é óbvia: que a ação certa chama o módulo certo, que o ator é
 * <strong>quem confirmou</strong> e não quem pediu a análise, e que a ação sem porta publicada não faz nada
 * em silêncio por engano — faz nada por decisão.
 */
class ProposalExecutorAdapterTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID QUEM_PEDIU = UUID.randomUUID();
    private static final UUID QUEM_CONFIRMA = UUID.randomUUID();
    private static final UUID LOTE = UUID.randomUUID();
    private static final UUID EQUIPAMENTO = UUID.randomUUID();

    private final SpyCosts costs = new SpyCosts();
    private final SpyCycles cycles = new SpyCycles();
    private final SpyNonConformities nonConformities = new SpyNonConformities();
    private final ProposalExecutorAdapter adapter =
            new ProposalExecutorAdapter(costs, cycles, nonConformities);

    @Test
    @DisplayName("fechar custo chama o custeio, com quem CONFIRMOU como ator")
    void fecharCustoChamaOCusteio() {
        // Não quem pediu a análise: a permissão foi conferida contra quem confirma, e é o nome dele que
        // precisa aparecer na trilha do custeio. Um comando disparado por conta da IA apagaria o rastro que
        // a confirmação humana existe para criar.
        adapter.execute(proposta(ProposedAction.CLOSE_BATCH_COST,
                Map.of("batchId", LOTE.toString())), QUEM_CONFIRMA);

        assertThat(costs.chamadas).hasSize(1);
        assertThat(costs.chamadas.getFirst().actorId()).isEqualTo(QUEM_CONFIRMA);
        assertThat(costs.chamadas.getFirst().batchId()).isEqualTo(LOTE);
        assertThat(costs.chamadas.getFirst().breweryId()).isEqualTo(CERVEJARIA);
        assertThat(cycles.chamadas).isEmpty();
    }

    @Test
    @DisplayName("a justificativa do fechamento carrega o motivo da proposta")
    void justificativaCarregaOMotivo() {
        // Quem abrir o custo meses depois precisa achar por que ele foi fechado. "Fechado pelo copiloto"
        // sozinho não é motivo; o motivo é o que o copiloto argumentou.
        adapter.execute(proposta(ProposedAction.CLOSE_BATCH_COST,
                Map.of("batchId", LOTE.toString())), QUEM_CONFIRMA);

        assertThat(costs.chamadas.getFirst().note()).contains("Lote terminou e as parcelas entraram");
    }

    @Test
    @DisplayName("ciclo de limpeza chama a sanitização com equipamento e código do procedimento")
    void cicloChamaSanitizacao() {
        adapter.execute(proposta(ProposedAction.SCHEDULE_CLEANING_CYCLE,
                Map.of("equipmentId", EQUIPAMENTO.toString(), "procedureCode", "CIP-01")), QUEM_CONFIRMA);

        assertThat(cycles.chamadas).hasSize(1);
        assertThat(cycles.chamadas.getFirst().equipmentId()).isEqualTo(EQUIPAMENTO);
        assertThat(cycles.chamadas.getFirst().procedureCode()).isEqualTo("CIP-01");
        assertThat(costs.chamadas).isEmpty();
    }

    @Test
    @DisplayName("ABRIR NÃO CONFORMIDADE PASSOU A EXECUTAR, com o lote e quem confirmou")
    void naoConformidadeExecuta() {
        // Continuava manual por duas barreiras, nenhuma de código: a NC não sabia dizer de que lote falava,
        // e os prazos não tinham de onde vir. A primeira caiu com o vínculo (V112); a segunda tinha caído
        // sozinha quando a PRM-001 criou a política de prazos por severidade.
        adapter.execute(proposta(ProposedAction.OPEN_NON_CONFORMITY,
                Map.of("batchId", LOTE.toString(), "title", "FG fora da faixa", "severity", "MAJOR")),
                QUEM_CONFIRMA);

        assertThat(nonConformities.batchId).isEqualTo(LOTE);
        assertThat(nonConformities.actorId).isEqualTo(QUEM_CONFIRMA);
        assertThat(nonConformities.title).isEqualTo("FG fora da faixa");
        assertThat(nonConformities.severity).isEqualTo("MAJOR");
        assertThat(costs.chamadas).isEmpty();
        assertThat(cycles.chamadas).isEmpty();
    }

    @Test
    @DisplayName("A DESCRIÇÃO DIZ QUE VEIO DO COPILOTO, e carrega o porquê que ele deu")
    void descricaoRegistraOrigem() {
        // Meses depois, "quem abriu esta NC?" tem como resposta um copiloto. Se isso não estiver escrito
        // na própria NC, o histórico mostra só o nome de quem confirmou — e some a metade da história.
        adapter.execute(proposta(ProposedAction.OPEN_NON_CONFORMITY,
                Map.of("batchId", LOTE.toString(), "title", "FG fora da faixa", "severity", "MAJOR")),
                QUEM_CONFIRMA);

        assertThat(nonConformities.origin).contains("copiloto");
    }

    @Test
    @DisplayName("a ação diz se confirmar executa, e é o que a tela usa para não mentir")
    void acaoDizSeExecuta() {
        assertThat(ProposedAction.CLOSE_BATCH_COST.executedOnConfirm()).isTrue();
        assertThat(ProposedAction.SCHEDULE_CLEANING_CYCLE.executedOnConfirm()).isTrue();
        assertThat(ProposedAction.OPEN_NON_CONFORMITY.executedOnConfirm()).isTrue();
    }

    @Test
    @DisplayName("o rótulo do ciclo diz INICIAR, porque é o que acontece")
    void rotuloDoCicloDizIniciar() {
        // A sanitização não tem agendamento: um ciclo existe a partir do momento em que começa. Manter
        // "programar" num botão que inicia faria a pessoa consentir com uma coisa e outra acontecer.
        assertThat(ProposedAction.SCHEDULE_CLEANING_CYCLE.label()).contains("Iniciar");
    }

    private static CommandProposal proposta(ProposedAction action, Map<String, String> parameters) {
        return CommandProposal.propose(CERVEJARIA, action, parameters,
                "Lote terminou e as parcelas entraram; ninguém fechou.", QUEM_PEDIU, Instant.now());
    }

    private static final class SpyCosts implements BatchCostCommands {

        private final List<Fechamento> chamadas = new ArrayList<>();

        @Override
        public void close(UUID actorId, UUID breweryId, UUID batchId, String note) {
            chamadas.add(new Fechamento(actorId, breweryId, batchId, note));
        }

        record Fechamento(UUID actorId, UUID breweryId, UUID batchId, String note) {}
    }

    private static final class SpyCycles implements CleaningCycleCommands {

        private final List<Inicio> chamadas = new ArrayList<>();

        @Override
        public UUID start(UUID actorId, UUID breweryId, UUID equipmentId, String procedureCode) {
            chamadas.add(new Inicio(actorId, breweryId, equipmentId, procedureCode));
            return UUID.randomUUID();
        }

        record Inicio(UUID actorId, UUID breweryId, UUID equipmentId, String procedureCode) {}
    }

    /** Espião da abertura de NC (DEB-AIA-003). */
    private static final class SpyNonConformities implements br.com.brew.brassia.quality.NonConformityOpening {

        UUID breweryId;
        UUID actorId;
        UUID batchId;
        String title;
        String severity;
        String origin;

        @Override
        public UUID openForBatch(UUID breweryId, UUID actorId, UUID batchId, String title, String severity,
                String origin) {
            this.breweryId = breweryId;
            this.actorId = actorId;
            this.batchId = batchId;
            this.title = title;
            this.severity = severity;
            this.origin = origin;
            return UUID.randomUUID();
        }
    }
}