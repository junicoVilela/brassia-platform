package br.com.brew.brassia.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Estado de limpeza do equipamento (CLN-004-A).
 *
 * <p>O que estes testes fixam: não existe caminho para marcar limpo sem ciclo, e a data de sujeira não
 * se renova a cada uso — porque é ela que denuncia o tanque parado sujo há semanas.
 */
class CleanlinessTest {

    private static final Instant MANHA = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant TARDE = Instant.parse("2026-08-11T18:00:00Z");

    @Test
    @DisplayName("equipamento nunca usado é limpo, sem data e sem ciclo")
    void nuncaUsado() {
        // Exigir ciclo antes do primeiro uso obrigaria a registrar a limpeza de um tanque recém-chegado,
        // e a primeira coisa que se aprende com uma regra assim é burlá-la.
        var novo = Cleanliness.neverUsed();

        assertThat(novo.isClean()).isTrue();
        assertThat(novo.soiledSince()).isEmpty();
        assertThat(novo.cleanedByCycleId()).isNull();
    }

    @Test
    @DisplayName("receber cerveja suja, e a data fica registrada")
    void recebeuCerveja() {
        var sujo = Cleanliness.neverUsed().soil(MANHA);

        assertThat(sujo.isClean()).isFalse();
        assertThat(sujo.soiledSince()).contains(MANHA);
    }

    @Test
    @DisplayName("SUJAR DE NOVO NÃO RENOVA A DATA")
    void sujarDeNovoNaoRenova() {
        // Reiniciar a contagem esconderia o tanque parado há três semanas atrás de um uso recente — e é
        // justamente o tanque parado sujo que é o problema pior.
        var sujo = Cleanliness.neverUsed().soil(MANHA).soil(TARDE);

        assertThat(sujo.soiledSince()).contains(MANHA);
    }

    @Test
    @DisplayName("o ciclo liberado limpa, e fica ligado à evidência")
    void cicloLimpa() {
        var ciclo = UUID.randomUUID();

        var limpo = Cleanliness.neverUsed().soil(MANHA).cleanedBy(ciclo, TARDE);

        assertThat(limpo.isClean()).isTrue();
        assertThat(limpo.soiledSince()).isEmpty();
        assertThat(limpo.cleanedByCycleId()).isEqualTo(ciclo);
        assertThat(limpo.cleanedAt()).isEqualTo(TARDE);
    }

    @Test
    @DisplayName("NÃO EXISTE CAMINHO PARA MARCAR LIMPO SEM CICLO")
    void semCicloNaoLimpa() {
        // Se existisse, ele seria o caminho usado no dia de correria — e "limpo" deixaria de significar
        // "há evidência de sanitização" para significar "alguém clicou".
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Cleanliness(Cleanliness.State.CLEAN, null, TARDE, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Cleanliness(Cleanliness.State.CLEAN, null, null, UUID.randomUUID()));
    }

    @Test
    @DisplayName("sujo sem data de sujeira é recusado")
    void sujoPrecisaDeData() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Cleanliness(Cleanliness.State.DIRTY, null, null, null));
    }
}
