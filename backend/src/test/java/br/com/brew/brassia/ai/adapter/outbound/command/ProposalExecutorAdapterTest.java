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
    private final ProposalExecutorAdapter adapter = new ProposalExecutorAdapter(costs, cycles);

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
    @DisplayName("abrir NÃO CONFORMIDADE não executa nada — e é decisão, não esquecimento")
    void naoConformidadeNaoExecuta() {
        // Executá-la exigiria inventar prazos de contenção, investigação e verificação, que são NOT NULL e
        // não vêm na proposta. Inventar prazo de contenção de uma NC é inventar regra de negócio.
        adapter.execute(proposta(ProposedAction.OPEN_NON_CONFORMITY,
                Map.of("batchId", LOTE.toString(), "title", "FG fora da faixa", "severity", "MAJOR")),
                QUEM_CONFIRMA);

        assertThat(costs.chamadas).isEmpty();
        assertThat(cycles.chamadas).isEmpty();
    }

    @Test
    @DisplayName("a ação diz se confirmar executa, e é o que a tela usa para não mentir")
    void acaoDizSeExecuta() {
        assertThat(ProposedAction.CLOSE_BATCH_COST.executedOnConfirm()).isTrue();
        assertThat(ProposedAction.SCHEDULE_CLEANING_CYCLE.executedOnConfirm()).isTrue();
        assertThat(ProposedAction.OPEN_NON_CONFORMITY.executedOnConfirm()).isFalse();
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
}
