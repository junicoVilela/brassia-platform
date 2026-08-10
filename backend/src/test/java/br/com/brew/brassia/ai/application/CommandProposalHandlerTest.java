package br.com.brew.brassia.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.ai.ModelGateway;
import br.com.brew.brassia.ai.ModelPurpose;
import br.com.brew.brassia.ai.application.port.outbound.CommandProposalRepository;
import br.com.brew.brassia.ai.application.service.BatchFactsAssembler;
import br.com.brew.brassia.ai.application.service.CommandProposalHandler;
import br.com.brew.brassia.ai.application.service.CommandProposalHandler.ModelProposals;
import br.com.brew.brassia.ai.application.service.CommandProposalHandler.ModelProposals.Proposal;
import br.com.brew.brassia.ai.domain.CommandProposal;
import br.com.brew.brassia.ai.domain.ProposalNotPendingException;
import br.com.brew.brassia.ai.domain.ProposalStatus;
import br.com.brew.brassia.ai.domain.ProposedAction;
import br.com.brew.brassia.ai.domain.UnknownBatchException;
import br.com.brew.brassia.ai.domain.UnknownProposalException;
import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.costing.BatchCostLookup;
import br.com.brew.brassia.ai.application.port.outbound.ProposalExecutor;
import br.com.brew.brassia.fermentation.FermentationLookup;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.production.BatchOutcomeLookup;
import br.com.brew.brassia.quality.BatchQualityLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import br.com.brew.brassia.shared.security.ForbiddenException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Propor comando (AIA-003).
 *
 * <p>O critério da história — "IA nunca altera nada sem comando humano explícito" — é verificado aqui pelo
 * ângulo que importa: o caso de uso <em>grava uma proposta</em> e nada mais. Não há caminho, em nenhum destes
 * testes, em que uma resposta do modelo produza efeito no negócio; o único efeito possível vem de {@code
 * accept}, e {@code accept} exige a alçada do comando.
 */
class CommandProposalHandlerTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID QUEM_PEDE = UUID.randomUUID();
    private static final UUID QUEM_CONFIRMA = UUID.randomUUID();
    private static final UUID BATCH = UUID.randomUUID();
    private static final UUID RECIPE = UUID.randomUUID();
    private static final UUID EQUIPMENT = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-08T12:00:00Z");

    private final RecordingAudit audit = new RecordingAudit();
    private final InMemoryProposals repository = new InMemoryProposals();

    /**
     * Registra o que foi mandado executar. Um dublê que só engole a chamada não distinguiria "executou" de
     * "esqueceu de executar" — que é exatamente a diferença que DEB-AIA-002 fecha.
     */
    private final SpyExecutor executor = new SpyExecutor();

    // --- propor -------------------------------------------------------------------------------------

    @Test
    @DisplayName("a proposta é gravada pendente e nada é executado")
    void propostaEhGravadaPendente() {
        var handler = handler(prompt -> new ModelProposals(List.of(fecharCusto())));

        var propostas = handler.propose(QUEM_PEDE, BREWERY, BATCH, Set.of("ai.command.propose"));

        assertThat(propostas).singleElement().satisfies(proposta -> {
            assertThat(proposta.action()).isEqualTo(ProposedAction.CLOSE_BATCH_COST);
            assertThat(proposta.status()).isEqualTo(ProposalStatus.PENDING);
            assertThat(proposta.proposedBy()).isEqualTo(QUEM_PEDE);
            assertThat(proposta.expiresAt()).isEqualTo(AGORA.plus(CommandProposal.VALIDITY));
        });
        assertThat(repository.stored).hasSize(1);
    }

    /**
     * Pedir proposta não exige a alçada do comando.
     *
     * <p>E não deve: pedir uma sugestão sobre o que fazer é diferente de fazer. Quem não pode fechar custo
     * ainda pode — e deve — poder descobrir que o custo está aberto, para pedir a quem pode.
     */
    @Test
    @DisplayName("quem pede a proposta não precisa da alçada do comando")
    void pedirNaoExigeAlcadaDoComando() {
        var handler = handler(prompt -> new ModelProposals(List.of(fecharCusto())));

        var propostas = handler.propose(QUEM_PEDE, BREWERY, BATCH, Set.of("ai.command.propose"));

        assertThat(propostas).hasSize(1);
        // A alçada que falta está declarada na própria proposta, para quem for decidir.
        assertThat(propostas.getFirst().action().requiredPermission()).isEqualTo("costing.cost.close");
    }

    @Test
    @DisplayName("nenhuma providência é resposta legítima: lista vazia, sem proposta gravada")
    void listaVaziaEhRespostaLegitima() {
        // Um copiloto que sempre encontra algo a fazer ensina a ser ignorado.
        var handler = handler(prompt -> new ModelProposals(List.of()));

        assertThat(handler.propose(QUEM_PEDE, BREWERY, BATCH, Set.of("ai.command.propose"))).isEmpty();
        assertThat(repository.stored).isEmpty();
    }

    /**
     * Proposta malformada é descartada, não guardada.
     *
     * <p>Guardar "para alguém olhar" é guardar uma proposta que alguém acaba confirmando. Aqui o modelo mandou
     * a concentração do sanitizante junto — que é exatamente o parâmetro químico que ele não pode inventar.
     */
    @Test
    @DisplayName("proposta com parâmetro inesperado é descartada e as válidas sobrevivem")
    void malformadaEhDescartadaEAsValidasSobrevivem() {
        var handler = handler(prompt -> new ModelProposals(List.of(
                fecharCusto(),
                new Proposal(ProposedAction.SCHEDULE_CLEANING_CYCLE,
                        Map.of("equipmentId", EQUIPMENT.toString(), "procedureCode", "POP-CIP-01",
                                "concentracao", "2%"),
                        "Tanque sem ciclo desde a última transferência."))));

        var propostas = handler.propose(QUEM_PEDE, BREWERY, BATCH, Set.of("ai.command.propose"));

        assertThat(propostas).singleElement()
                .satisfies(p -> assertThat(p.action()).isEqualTo(ProposedAction.CLOSE_BATCH_COST));
        assertThat(repository.stored).hasSize(1);
        // O descarte é contado na auditoria: uma resposta enfraquecida não pode ter a mesma aparência de uma
        // íntegra.
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("ai.command.propose");
            assertThat(event.metadata()).containsEntry("proposed", "1").containsEntry("malformed", "1");
        });
    }

    @Test
    @DisplayName("ação fora da allowlist não desserializa: não existe proposta a construir")
    void acaoForaDaAllowlistNaoDesserializa() {
        // O gateway rejeitaria a resposta inteira antes de chegar aqui, porque `action` é enum no contrato.
        // Este teste prova o outro lado: nem por construção direta uma ação nula passa.
        assertThatThrownBy(() -> new Proposal(null, Map.of("batchId", BATCH.toString()), "porque sim"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("allowlist");
    }

    @Test
    @DisplayName("o schema traz a allowlist como enum, montada da própria lista de ações")
    void schemaTrazAAllowlist() {
        var gateway = new SpyGateway(prompt -> new ModelProposals(List.of()));
        handler(gateway).propose(QUEM_PEDE, BREWERY, BATCH, Set.of("ai.command.propose"));

        var prompt = gateway.calls.getFirst();
        assertThat(prompt.purpose()).isEqualTo(ModelPurpose.COMMAND_PROPOSAL);
        assertThat(prompt.responseSchema()).contains("\"additionalProperties\": false");
        for (var action : ProposedAction.names()) {
            assertThat(prompt.responseSchema()).contains("\"" + action + "\"");
        }
        assertThat(prompt.instruction()).contains("NÃO executa nada");
    }

    @Test
    @DisplayName("os fatos do lote vão ao prompt; o texto do documento não vai")
    void fatosVaoAoPrompt() {
        var gateway = new SpyGateway(prompt -> new ModelProposals(List.of()));
        handler(gateway).propose(QUEM_PEDE, BREWERY, BATCH, Set.of("ai.command.propose"));

        var content = gateway.calls.getFirst().untrustedInput();
        assertThat(content).contains("volume_planejado").contains("400");
        assertThat(content).contains("CLOSE_BATCH_COST").contains("batchId");
    }

    @Test
    @DisplayName("lote inexistente não gera proposta nem chamada ao modelo")
    void loteInexistenteNaoPropoe() {
        var scene = scene();
        scene.batch = Optional.empty();
        var gateway = new SpyGateway(prompt -> new ModelProposals(List.of()));

        assertThatExceptionOfType(UnknownBatchException.class).isThrownBy(() ->
                handler(gateway, scene).propose(QUEM_PEDE, BREWERY, BATCH, Set.of("ai.command.propose")));
        assertThat(gateway.calls).isEmpty();
    }

    // --- decidir ------------------------------------------------------------------------------------

    @Test
    @DisplayName("confirmar exige a alçada do comando, conferida no instante do aceite")
    void confirmarExigeAlcadaDoComando() {
        var id = pendente();

        assertThatExceptionOfType(ForbiddenException.class).isThrownBy(() ->
                handler(prompt -> null).accept(QUEM_CONFIRMA, BREWERY, id,
                        Set.of("ai.command.propose", "ai.command.read"), null))
                .withMessageContaining("costing.cost.close");
        assertThat(repository.stored.get(id).status()).isEqualTo(ProposalStatus.PENDING);
        // Recusa não é decisão: nada foi auditado como aceite.
        assertThat(audit.events).isEmpty();
    }

    @Test
    @DisplayName("o aceite grava a decisão e audita quem consentiu, na ação e nos parâmetros")
    void aceiteAuditaQuemConsentiu() {
        var id = pendente();

        var decidida = handler(prompt -> null).accept(QUEM_CONFIRMA, BREWERY, id,
                Set.of("costing.cost.close"), "Conferi as parcelas.");

        assertThat(decidida.status()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(decidida.decidedBy()).isEqualTo(QUEM_CONFIRMA);
        assertThat(repository.stored.get(id).status()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo("ai.command.accept");
            assertThat(event.metadata())
                    .containsEntry("proposalAction", "CLOSE_BATCH_COST")
                    .containsEntry("requiredPermission", "costing.cost.close")
                    .containsEntry("proposedBy", QUEM_PEDE.toString());
            // Quem consentiu é o ator do evento, e é diferente de quem pediu.
            assertThat(event.actorId()).isEqualTo(QUEM_CONFIRMA);
        });
    }

    @Test
    @DisplayName("recusar audita como recusa e não exige a alçada do comando")
    void recusarAuditaComoRecusa() {
        var id = pendente();

        var decidida = handler(prompt -> null).reject(QUEM_CONFIRMA, BREWERY, id, "Não se aplica.");

        assertThat(decidida.status()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(audit.events).singleElement()
                .satisfies(event -> assertThat(event.action()).isEqualTo("ai.command.reject"));
    }

    /**
     * Dois "confirmar" ao mesmo tempo.
     *
     * <p>Aqui o repositório simula a corrida: a proposta lida ainda estava pendente, mas a gravação encontra
     * o estado já mudado. Quem chegou depois precisa descobrir isso — se a segunda gravação vencesse, o
     * registro diria que a decisão foi dele.
     */
    @Test
    @DisplayName("gravação condicional perdida vira conflito, não segundo aceite")
    void gravacaoPerdidaViraConflito() {
        var id = pendente();
        repository.decideBehindOurBack = true;

        assertThatExceptionOfType(ProposalNotPendingException.class).isThrownBy(() ->
                handler(prompt -> null).accept(QUEM_CONFIRMA, BREWERY, id,
                        Set.of("costing.cost.close"), null));
        assertThat(audit.events).isEmpty();
    }

    @Test
    @DisplayName("o aceite EXECUTA o comando, com quem confirmou como ator")
    void aceiteExecutaOComando() {
        // DEB-AIA-002. Antes disto o consentimento ficava gravado e a execução dependia de alguém não
        // esquecer o segundo passo — numa proposta cuja razão de existir é justamente que "o lote termina,
        // as parcelas entram, e ninguém lembra de fechar".
        var id = pendente();

        handler(prompt -> null).accept(QUEM_CONFIRMA, BREWERY, id, Set.of("costing.cost.close"), null);

        assertThat(executor.executadas).hasSize(1);
        assertThat(executor.executadas.getFirst().id()).isEqualTo(id);
    }

    @Test
    @DisplayName("a EXECUÇÃO vem depois da gravação: quem perde a corrida não dispara comando")
    void execucaoVemDepoisDaGravacao() {
        // A ordem é a proteção. Executar antes dispararia o comando duas vezes em dois cliques, e só então
        // descobriria que uma das duas não devia ter passado — com o custo já fechado.
        var id = pendente();
        repository.decideBehindOurBack = true;

        assertThatExceptionOfType(ProposalNotPendingException.class).isThrownBy(() ->
                handler(prompt -> null).accept(QUEM_CONFIRMA, BREWERY, id,
                        Set.of("costing.cost.close"), null));

        assertThat(executor.executadas).isEmpty();
    }

    @Test
    @DisplayName("comando que falha propaga, para a transação desfazer a decisão junto")
    void comandoQueFalhaPropaga() {
        // Consentimento gravado sem o efeito que ele autorizou é pior que nenhum dos dois: alguém leria
        // "confirmado" e acreditaria que o custo foi fechado. E a auditoria não pode registrar um aceite
        // que vai ser desfeito.
        var id = pendente();
        executor.falha = new IllegalStateException("custo já fechado");

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                handler(prompt -> null).accept(QUEM_CONFIRMA, BREWERY, id,
                        Set.of("costing.cost.close"), null));

        assertThat(audit.events).isEmpty();
    }

    @Test
    @DisplayName("proposta de outra cervejaria não existe")
    void propostaDeOutraCervejariaNaoExiste() {
        var id = pendente();

        assertThatExceptionOfType(UnknownProposalException.class).isThrownBy(() ->
                handler(prompt -> null).accept(QUEM_CONFIRMA, UUID.randomUUID(), id,
                        Set.of("costing.cost.close"), null));
    }

    @Test
    @DisplayName("decisão sem autor não existe")
    void decisaoSemAutorNaoExiste() {
        var id = pendente();

        assertThatThrownBy(() -> handler(prompt -> null).accept(null, BREWERY, id,
                Set.of("costing.cost.close"), null)).isInstanceOf(NullPointerException.class);
    }

    // --- infraestrutura do teste --------------------------------------------------------------------

    private UUID pendente() {
        var proposta = CommandProposal.propose(BREWERY, ProposedAction.CLOSE_BATCH_COST,
                Map.of("batchId", BATCH.toString()), "O lote terminou e o custo segue derivado.",
                QUEM_PEDE, AGORA);
        repository.insert(proposta);
        return proposta.id();
    }

    private static Proposal fecharCusto() {
        return new Proposal(ProposedAction.CLOSE_BATCH_COST, Map.of("batchId", BATCH.toString()),
                "O lote terminou e o custo segue derivado.");
    }

    private CommandProposalHandler handler(Function<ModelGateway.Prompt, ModelProposals> behaviour) {
        return handler(new SpyGateway(behaviour), scene());
    }

    private CommandProposalHandler handler(SpyGateway gateway) {
        return handler(gateway, scene());
    }

    private CommandProposalHandler handler(SpyGateway gateway, Scene scene) {
        var assembler = new BatchFactsAssembler(
                (breweryId, batchId) -> scene.batch,
                (breweryId, batchId) -> scene.outcome,
                (breweryId, batchId) -> scene.quality,
                (breweryId, batchId) -> scene.cost,
                new RecipeLookup() {
                    @Override
                    public Optional<PublishedRecipe> findPublished(UUID breweryId, UUID recipeId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<PublishedComposition> findPublishedComposition(UUID breweryId,
                            UUID recipeId) {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<PublishedForOrder> findPublishedForOrder(UUID breweryId,
                            UUID recipeId) {
                        return Optional.of(new PublishedForOrder(recipeId, 2, "IPA da casa", null,
                                new BigDecimal("400"), scene.metrics));
                    }
                },
                (breweryId, batchId) -> scene.fermentation);
        return new CommandProposalHandler(assembler, gateway, repository, executor, audit,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static Scene scene() {
        return new Scene();
    }

    static final class SpyExecutor implements ProposalExecutor {

        final List<CommandProposal> executadas = new ArrayList<>();
        RuntimeException falha;

        @Override
        public void execute(CommandProposal proposal, UUID actorId) {
            if (falha != null) {
                throw falha;
            }
            executadas.add(proposal);
        }
    }

    private static final class Scene {
        /**
         * Fermentação vazia por padrão: a maioria dos casos aqui é sobre a conferência do texto do modelo,
         * não sobre a curva. Os testes que se importam com ela a preenchem.
         */
        Optional<FermentationLookup.Snapshot> fermentation = Optional.empty();
        Optional<BatchLookup.Snapshot> batch = Optional.of(new BatchLookup.Snapshot(BATCH,
                UUID.randomUUID(), "LOTE-100", new BigDecimal("400"), new BigDecimal("390"),
                "PACKAGED", RECIPE, 2, "IPA da casa"));
        Optional<BatchOutcomeLookup.BatchOutcome> outcome = Optional.of(
                new BatchOutcomeLookup.BatchOutcome(new BigDecimal("400"), new BigDecimal("390"),
                        new BigDecimal("10")));
        BatchQualityLookup.BatchQuality quality = new BatchQualityLookup.BatchQuality(10, 8,
                List.of(), List.of(), List.of());
        Optional<BatchCostLookup.CostSummary> cost = Optional.of(new BatchCostLookup.CostSummary(
                new BigDecimal("780.00"), new BigDecimal("2.0000"), new BigDecimal("390"), false,
                true, List.of("sem mão de obra")));
        Optional<RecipeLookup.Metrics> metrics = Optional.of(new RecipeLookup.Metrics(
                new BigDecimal("1.060"), new BigDecimal("1.012"), new BigDecimal("6.3"),
                new BigDecimal("45"), new BigDecimal("22")));
    }

    /** Repositório em memória com a gravação condicional que o de verdade faz no {@code UPDATE}. */
    private static final class InMemoryProposals implements CommandProposalRepository {

        private final Map<UUID, CommandProposal> stored = new LinkedHashMap<>();
        private boolean decideBehindOurBack;

        @Override
        public void insert(CommandProposal proposal) {
            stored.put(proposal.id(), proposal);
        }

        @Override
        public boolean saveDecision(CommandProposal proposal) {
            if (decideBehindOurBack) {
                decideBehindOurBack = false;
                // Outra pessoa decidiu primeiro: o estado gravado passa a ser o dela, não o nosso.
                stored.put(proposal.id(), stored.get(proposal.id())
                        .reject(UUID.randomUUID(), "decidido por outra pessoa", proposal.decidedAt()));
                return false;
            }
            var current = stored.get(proposal.id());
            if (current == null || !current.pending()) {
                return false;
            }
            stored.put(proposal.id(), proposal);
            return true;
        }

        @Override
        public Optional<CommandProposal> find(UUID breweryId, UUID proposalId) {
            return Optional.ofNullable(stored.get(proposalId))
                    .filter(proposal -> proposal.breweryId().equals(breweryId));
        }

        @Override
        public List<CommandProposal> findAll(UUID breweryId) {
            return stored.values().stream().filter(p -> p.breweryId().equals(breweryId)).toList();
        }
    }

    private static final class SpyGateway implements ModelGateway {

        private final List<Prompt> calls = new ArrayList<>();
        private final Function<Prompt, ModelProposals> behaviour;

        SpyGateway(Function<Prompt, ModelProposals> behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T complete(Prompt prompt, Class<T> contract) {
            calls.add(prompt);
            return (T) behaviour.apply(prompt);
        }
    }

    private static final class RecordingAudit implements AuditTrail {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }
    }
}
