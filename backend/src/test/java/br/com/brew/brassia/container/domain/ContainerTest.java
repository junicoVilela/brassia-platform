package br.com.brew.brassia.container.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContainerTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID INSPETOR = UUID.randomUUID();
    private static final Instant HOJE = Instant.parse("2026-08-16T10:00:00Z");
    private static final Instant DAQUI_A_UM_ANO = HOJE.plus(Duration.ofDays(365));

    private static Container keg() {
        return Container.register(UUID.randomUUID(), CERVEJARIA, "KEG-0001", ContainerKind.KEG,
                new BigDecimal("50"), Ownership.OWN);
    }

    private static Container kegInspecionado() {
        var c = keg();
        c.inspect(new ContainerInspection(HOJE, DAQUI_A_UM_ANO, INSPETOR, null));
        return c;
    }

    // --- inspeção ---

    @Test
    void oContêinerNasceSemInspecaoENaoSeEnche() {
        // "Nunca foi inspecionado" é pior que "venceu": tratar a ausência como aprovação deixaria toda a
        // frota nova fora de qualquer controle.
        var c = keg();

        assertThat(c.state()).isEqualTo(ContainerState.EMPTY);
        assertThat(c.inspection()).isEmpty();
        assertThatThrownBy(() -> c.requireFillableAt(HOJE))
                .isInstanceOf(ContainerNotFillableException.class)
                .hasMessageContaining("inspeção");
    }

    @Test
    void aInspecaoVencidaImpedeOEnchimento() {
        // Vaso de pressão sem inspeção em dia é risco físico, e não pendência de papel.
        var c = kegInspecionado();

        assertThat(c.fillableAt(HOJE)).isTrue();
        assertThat(c.fillableAt(DAQUI_A_UM_ANO.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    void aValidadeERegistradaENaoCalculada() {
        // Inventar aqui um intervalo padrão — "cinco anos" — faria o sistema afirmar conformidade que
        // ninguém verificou. Quem inspeciona informa até quando vale (DUV-CON-001).
        assertThatThrownBy(() -> new ContainerInspection(HOJE, HOJE, INSPETOR, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    void inspecionarNaoMudaEstadoNemCondicao() {
        var c = keg();
        c.markDamaged();
        c.inspect(new ContainerInspection(HOJE, DAQUI_A_UM_ANO, INSPETOR, "válvula trocada"));

        // Ele foi olhado; isso não conserta o amassado. Consertar é a manutenção.
        assertThat(c.condition()).isEqualTo(ContainerCondition.DAMAGED);
        assertThat(c.state()).isEqualTo(ContainerState.EMPTY);
    }

    // --- ciclo ---

    @Test
    void oQueVoltouDoClienteNaoEstaPronto() {
        // A decisão central da história. Derivar a limpeza da chegada — "voltou, logo está disponível" —
        // encheria com cerveja um vasilhame que ninguém lavou, e o problema apareceria na boca do
        // cliente.
        var c = kegInspecionado();
        c.fill(HOJE);
        c.dispatch();
        c.deliver();
        c.collect();

        assertThat(c.state()).isEqualTo(ContainerState.RETURNED);
        assertThatThrownBy(() -> c.requireFillableAt(HOJE))
                .isInstanceOf(ContainerNotFillableException.class)
                .hasMessageContaining("RETURNED");

        c.releaseToStock();
        assertThat(c.state()).isEqualTo(ContainerState.EMPTY);
        assertThat(c.fillableAt(HOJE)).isTrue();
    }

    @Test
    void oCicloNaoPulaEtapas() {
        // Entregar o que nunca saiu do depósito é engano de operação, e o registro dele viraria uma
        // entrega que ninguém fez.
        var c = kegInspecionado();

        assertThatThrownBy(c::deliver).isInstanceOf(IllegalContainerTransitionException.class);
        assertThatThrownBy(c::collect).isInstanceOf(IllegalContainerTransitionException.class);
        assertThatThrownBy(c::releaseToStock).isInstanceOf(IllegalContainerTransitionException.class);
    }

    @Test
    void oAvariadoNaoRecebeCerveja() {
        // Encher um vasilhame com vazamento perde a cerveja e o tempo.
        var c = kegInspecionado();
        c.markDamaged();

        assertThatThrownBy(() -> c.requireFillableAt(HOJE))
                .isInstanceOf(ContainerNotFillableException.class)
                .hasMessageContaining("avariado");
    }

    @Test
    void aManutencaoDevolveOVasilhameVazioEEmBoaCondicao() {
        var c = kegInspecionado();
        c.markDamaged();
        c.sendToMaintenance();

        assertThat(c.fillableAt(HOJE)).isFalse();

        c.returnFromMaintenance();
        assertThat(c.state()).isEqualTo(ContainerState.EMPTY);
        assertThat(c.condition()).isEqualTo(ContainerCondition.GOOD);
        assertThat(c.fillableAt(HOJE)).isTrue();
    }

    @Test
    void naoSeMandaParaAOficinaOQueEstaNaRua() {
        // Primeiro ele volta. O contrário registraria conserto de um keg que está na câmara fria do bar.
        var c = kegInspecionado();
        c.fill(HOJE);
        c.dispatch();

        assertThatThrownBy(c::sendToMaintenance)
                .isInstanceOf(IllegalContainerTransitionException.class);
    }

    // --- fim de vida ---

    @Test
    void aBaixaEXigeMotivoEEterminal() {
        // Sem motivo, o inventário perde um ativo e ninguém sabe por quê seis meses depois.
        var c = kegInspecionado();

        assertThatThrownBy(() -> c.retire("  ", HOJE)).isInstanceOf(IllegalArgumentException.class);

        c.retire("furo na costura, sem recuperação", HOJE);

        assertThat(c.isRetired()).isTrue();
        assertThat(c.retirementReason()).contains("furo na costura, sem recuperação");
        assertThat(c.retiredAt()).contains(HOJE);
        // Baixado é histórico, e não estoque: nada mais acontece com ele.
        assertThatThrownBy(() -> c.fill(HOJE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(c::markDamaged).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.retire("de novo", HOJE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void naoSeDaBaixaNoQueEstaComOCliente() {
        // O vasilhame que não voltou é PERDA, que é outro fato e tem outro dono (CON-003). O mesmo botão
        // faria "sumiu" e "descartei" virarem a mesma linha no inventário.
        var c = kegInspecionado();
        c.fill(HOJE);
        c.dispatch();
        c.deliver();

        assertThatThrownBy(() -> c.retire("sumiu", HOJE))
                .isInstanceOf(IllegalContainerTransitionException.class);
    }

    // --- propriedade ---

    @Test
    void oVasilhameDeTerceiroEEstadoLegitimo() {
        // Tratar os três como iguais faria o inventário contar como patrimônio o que é do cliente.
        var c = Container.register(UUID.randomUUID(), CERVEJARIA, "KEG-CLI-01", ContainerKind.KEG,
                new BigDecimal("30"), Ownership.CUSTOMER);

        assertThat(c.ownership()).isEqualTo(Ownership.CUSTOMER);
        c.changeOwnership(Ownership.POOL);
        assertThat(c.ownership()).isEqualTo(Ownership.POOL);
    }

    @Test
    void oCodigoEACapacidadeSaoObrigatorios() {
        assertThatThrownBy(() -> Container.register(UUID.randomUUID(), CERVEJARIA, " ",
                ContainerKind.KEG, new BigDecimal("50"), Ownership.OWN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("código");
        assertThatThrownBy(() -> Container.register(UUID.randomUUID(), CERVEJARIA, "K1",
                ContainerKind.KEG, BigDecimal.ZERO, Ownership.OWN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("capacidade");
    }

    // --- a volta do perdido (DUV-CON-002) ---

    @Test
    void oPerdidoVoltaSujoENaoDisponivel() {
        // Ele passou meses fora de vista: tratá-lo como pronto para encher seria confiar num vasilhame
        // que ninguém olhou.
        var c = kegInspecionado();
        c.declareLost("o bar fechou", HOJE);

        c.recover("o bar reabriu e devolveu", HOJE);

        assertThat(c.state()).isEqualTo(ContainerState.RETURNED);
        assertThat(c.isRetired()).isFalse();
        assertThat(c.fillableAt(HOJE)).isFalse();
        // E o motivo da baixa sai: um contêiner ativo com motivo de baixa mente sobre o próprio estado.
        assertThat(c.retirementReason()).isEmpty();
    }

    @Test
    void oPerdidoAvariadoVoltaAVARIADO() {
        // DEB-CON-003 #3. A volta zerava a condição para GOOD, e um keg que sumiu com uma avaria
        // reaparecia como se alguém o tivesse consertado. Ninguém consertou nada: ele estava no depósito
        // de um cliente esse tempo todo. Quem conserta é a oficina, e `returnFromMaintenance` é que
        // registra isso.
        var c = kegInspecionado();
        c.markDamaged();
        c.declareLost("sumiu com o bar", HOJE);

        c.recover("apareceu no inventário do cliente", HOJE);

        assertThat(c.condition()).isEqualTo(ContainerCondition.DAMAGED);
        // E a consequência que importa: avariado não recebe cerveja, tenha ele sumido ou não.
        assertThat(c.fillableAt(HOJE)).isFalse();
    }

    @Test
    void oDescartadoNaoReaparece() {
        // "Descartei" não pode virar reversível — é justamente a distinção que a CON-003 construiu entre
        // o keg que sumiu e o que foi para o ferro-velho.
        var c = kegInspecionado();
        c.retire("furo na costura, sem recuperação", HOJE);

        assertThatThrownBy(() -> c.recover("achei", HOJE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("saiu por perda");
    }

    @Test
    void aVoltaPrecisaDeMotivo() {
        var c = kegInspecionado();
        c.declareLost("sumiu", HOJE);

        assertThatThrownBy(() -> c.recover("   ", HOJE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivo");
    }
}
