package br.com.brew.brassia.crm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetentionPolicyTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final LocalDate ULTIMO_CONTATO = LocalDate.parse("2026-01-10");

    @Test
    void semPoliticaNadaExpira() {
        // Não anonimizar por falta de decisão é reversível; anonimizar cedo demais não é.
        var p = RetentionPolicy.none(CERVEJARIA);

        assertThat(p.anonymizeOn(ULTIMO_CONTATO)).isEmpty();
        assertThat(p.dueFor(ULTIMO_CONTATO, LocalDate.parse("2030-01-01"))).isFalse();
    }

    @Test
    void comPoliticaADataDerivaDoUltimoRelacionamento() {
        var p = RetentionPolicy.of(CERVEJARIA, 365);

        assertThat(p.anonymizeOn(ULTIMO_CONTATO)).contains(LocalDate.parse("2027-01-10"));
    }

    @Test
    void oDiaDoVencimentoAindaNaoVence() {
        // O prazo é "guardar por N dias"; cortar no próprio dia entregaria N-1, e a diferença aparece
        // em auditoria.
        var p = RetentionPolicy.of(CERVEJARIA, 365);

        assertThat(p.dueFor(ULTIMO_CONTATO, LocalDate.parse("2027-01-09"))).isFalse();
        assertThat(p.dueFor(ULTIMO_CONTATO, LocalDate.parse("2027-01-10"))).isFalse();
        assertThat(p.dueFor(ULTIMO_CONTATO, LocalDate.parse("2027-01-11"))).isTrue();
    }

    @Test
    void prazoNaoPositivoERecusado() {
        // Retenção zero anonimizaria no ato do cadastro, o que não é política e sim defeito.
        assertThatThrownBy(() -> RetentionPolicy.of(CERVEJARIA, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
        assertThatThrownBy(() -> RetentionPolicy.of(CERVEJARIA, -30))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
