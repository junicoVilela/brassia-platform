package br.com.brew.brassia.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.shared.security.ForbiddenException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O ciclo de vida de uma proposta (AIA-003).
 *
 * <p>Os testes aqui são sobre <em>quem pode transformar sugestão em decisão</em>, que é a única pergunta que
 * esta história de fato responde. O resto — a chamada ao modelo, a montagem do prompt — está coberto no
 * handler e na integração.
 */
class CommandProposalTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID QUEM_PEDIU = UUID.randomUUID();
    private static final UUID QUEM_CONFIRMA = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-07T10:00:00Z");
    private static final Map<String, String> PARAMETROS = Map.of("batchId", UUID.randomUUID().toString());

    private CommandProposal pendente() {
        return CommandProposal.propose(BREWERY, ProposedAction.CLOSE_BATCH_COST, PARAMETROS,
                "O lote terminou e o custo segue derivado.", QUEM_PEDIU, AGORA);
    }

    @Test
    @DisplayName("nasce pendente, com prazo, e sem decisão nenhuma")
    void nascePendente() {
        var proposta = pendente();

        assertThat(proposta.status()).isEqualTo(ProposalStatus.PENDING);
        assertThat(proposta.pending()).isTrue();
        assertThat(proposta.proposedBy()).isEqualTo(QUEM_PEDIU);
        assertThat(proposta.expiresAt()).isEqualTo(AGORA.plus(CommandProposal.VALIDITY));
        assertThat(proposta.decidedBy()).isNull();
        assertThat(proposta.decidedAt()).isNull();
    }

    /**
     * O teste central da história.
     *
     * <p>Quem pediu a proposta tinha {@code ai.command.propose}. Isso não é alçada para fechar custo, e não
     * pode virar alçada por passar pela IA — que é exatamente o caminho lateral que a separação fecha.
     */
    @Test
    @DisplayName("confirmar exige a permissão do comando, não a de propor")
    void confirmarExigePermissaoDoComando() {
        var proposta = pendente();

        assertThatExceptionOfType(ForbiddenException.class)
                .isThrownBy(() -> proposta.accept(QUEM_CONFIRMA, Set.of("ai.command.propose",
                        "ai.command.read", "ai.assessment.batch"), null, AGORA))
                .withMessageContaining("costing.cost.close");
    }

    @Test
    @DisplayName("com a permissão do comando, o aceite registra quem consentiu")
    void aceiteRegistraQuemConsentiu() {
        var decidida = pendente().accept(QUEM_CONFIRMA, Set.of("costing.cost.close"),
                "Conferi as parcelas.", AGORA.plusSeconds(60));

        assertThat(decidida.status()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(decidida.decidedBy()).isEqualTo(QUEM_CONFIRMA);
        assertThat(decidida.decidedAt()).isEqualTo(AGORA.plusSeconds(60));
        assertThat(decidida.decisionNote()).isEqualTo("Conferi as parcelas.");
        // Quem pediu continua registrado: a proposta e a decisão têm autores distintos, e os dois importam.
        assertThat(decidida.proposedBy()).isEqualTo(QUEM_PEDIU);
    }

    /**
     * A assimetria é deliberada, não esquecimento.
     *
     * <p>Dizer "não" a uma sugestão não altera nada. Exigir alçada de comando para descartar deixaria
     * propostas pendentes acumulando até vencer — e proposta pendente parece decisão adiada, não decisão
     * tomada.
     */
    @Test
    @DisplayName("recusar não exige a permissão do comando")
    void recusarNaoExigePermissaoDoComando() {
        var decidida = pendente().reject(QUEM_CONFIRMA, "Custo já conferido fora do sistema.",
                AGORA.plusSeconds(60));

        assertThat(decidida.status()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(decidida.decidedBy()).isEqualTo(QUEM_CONFIRMA);
    }

    @Test
    @DisplayName("proposta vencida não é aceitável nem para quem tem alçada")
    void vencidaNaoEhAceitavel() {
        var proposta = pendente();
        var depoisDoPrazo = AGORA.plus(CommandProposal.VALIDITY).plus(Duration.ofSeconds(1));

        assertThatExceptionOfType(ExpiredProposalException.class)
                .isThrownBy(() -> proposta.accept(QUEM_CONFIRMA, Set.of("costing.cost.close"), null,
                        depoisDoPrazo));
        assertThat(proposta.expiredAt(depoisDoPrazo)).isTrue();
        assertThat(proposta.expiredAt(AGORA.plusSeconds(60))).isFalse();
    }

    /**
     * Vencida ainda pode ser descartada.
     *
     * <p>Se não pudesse, a lista de pendências nunca se limparia — e uma tela cheia de propostas vencidas
     * treina quem a lê a ignorar a tela inteira, inclusive as que valem.
     */
    @Test
    @DisplayName("proposta vencida ainda pode ser descartada")
    void vencidaAindaPodeSerDescartada() {
        var proposta = pendente();
        var depoisDoPrazo = AGORA.plus(CommandProposal.VALIDITY).plus(Duration.ofDays(3));

        assertThatCode(() -> proposta.reject(QUEM_CONFIRMA, "Venceu.", depoisDoPrazo))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("decisão é definitiva: proposta decidida não volta a pendente")
    void decisaoEhDefinitiva() {
        var aceita = pendente().accept(QUEM_CONFIRMA, Set.of("costing.cost.close"), null, AGORA);

        assertThatExceptionOfType(ProposalNotPendingException.class)
                .isThrownBy(() -> aceita.accept(QUEM_CONFIRMA, Set.of("costing.cost.close"), null, AGORA));
        assertThatExceptionOfType(ProposalNotPendingException.class)
                .isThrownBy(() -> aceita.reject(QUEM_CONFIRMA, null, AGORA));
    }

    @Test
    @DisplayName("proposta sem justificativa não é proposta")
    void semJustificativaNaoEhProposta() {
        assertThatThrownBy(() -> CommandProposal.propose(BREWERY, ProposedAction.CLOSE_BATCH_COST,
                PARAMETROS, "   ", QUEM_PEDIU, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa");
    }

    @Test
    @DisplayName("parâmetro faltando impede a proposta de existir")
    void parametroFaltandoImpedeAProposta() {
        assertThatThrownBy(() -> CommandProposal.propose(BREWERY, ProposedAction.OPEN_NON_CONFORMITY,
                Map.of("batchId", UUID.randomUUID().toString(), "title", "Densidade fora da faixa"),
                "Medição fora da especificação sem NC aberta.", QUEM_PEDIU, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("severity");
    }

    /**
     * Parâmetro sobrando também recusa.
     *
     * <p>Aqui o modelo mandou a concentração do produto químico junto — que é o caso concreto que a regra
     * "não inventar concentração ou mistura" existe para bloquear. O procedimento dita o parâmetro químico; a
     * proposta só aponta o equipamento e o POP.
     */
    @Test
    @DisplayName("parâmetro inesperado recusa a proposta em vez de ser ignorado")
    void parametroInesperadoRecusa() {
        assertThatThrownBy(() -> CommandProposal.propose(BREWERY, ProposedAction.SCHEDULE_CLEANING_CYCLE,
                Map.of("equipmentId", UUID.randomUUID().toString(), "procedureCode", "POP-CIP-01",
                        "concentracao", "2%"),
                "Tanque sem ciclo registrado desde a última transferência.", QUEM_PEDIU, AGORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("concentracao");
    }

    @Test
    @DisplayName("cada ação carrega a permissão do comando de verdade")
    void cadaAcaoCarregaAPermissaoDoComando() {
        assertThat(ProposedAction.CLOSE_BATCH_COST.requiredPermission()).isEqualTo("costing.cost.close");
        assertThat(ProposedAction.OPEN_NON_CONFORMITY.requiredPermission()).isEqualTo("quality.nc.manage");
        assertThat(ProposedAction.SCHEDULE_CLEANING_CYCLE.requiredPermission())
                .isEqualTo("sanitation.cycle.execute");
        // Nenhuma ação pode ser confirmada com a permissão de propor.
        assertThat(ProposedAction.names()).doesNotContain("ai.command.propose");
        assertThat(java.util.Arrays.stream(ProposedAction.values())
                .map(ProposedAction::requiredPermission))
                .noneMatch(permission -> permission.startsWith("ai."));
    }
}
