package br.com.brew.brassia.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A vigência de um documento (RAG-001).
 *
 * <p>O que estes testes fixam é a capacidade de responder sobre o passado. Uma base que só sabe o que
 * vale hoje não serve para investigar um lote produzido em maio — e investigar lote é metade do motivo de
 * este sistema existir.
 */
class EffectivityTest {

    private static final LocalDate MARCO = LocalDate.of(2026, 3, 1);
    private static final LocalDate ABRIL = LocalDate.of(2026, 4, 1);
    private static final LocalDate MAIO = LocalDate.of(2026, 5, 1);

    @Test
    @DisplayName("vigência aberta cobre o início e tudo depois dele")
    void vigenciaAbertaCobreODepois() {
        var open = Effectivity.from(ABRIL);

        assertThat(open.coversDate(ABRIL)).isTrue();
        assertThat(open.coversDate(MAIO)).isTrue();
        assertThat(open.coversDate(ABRIL.plusYears(5))).isTrue();
        assertThat(open.open()).isTrue();
    }

    @Test
    @DisplayName("antes do início não vale: laudo assinado em março que vale a partir de abril")
    void antesDoInicioNaoVale() {
        // O caso é real e é a razão de a vigência não ser a data de upload: o documento existe em março e
        // só passa a valer em abril.
        var open = Effectivity.from(ABRIL);

        assertThat(open.coversDate(MARCO)).isFalse();
        assertThat(open.coversDate(ABRIL.minusDays(1))).isFalse();
    }

    @Test
    @DisplayName("vigência fechada inclui os dois extremos")
    void vigenciaFechadaEhInclusiva() {
        var closed = new Effectivity(MARCO, ABRIL);

        assertThat(closed.coversDate(MARCO)).isTrue();
        assertThat(closed.coversDate(ABRIL)).isTrue();
        assertThat(closed.coversDate(ABRIL.plusDays(1))).isFalse();
        assertThat(closed.open()).isFalse();
    }

    @Test
    @DisplayName("substituição encerra no dia anterior: nunca duas vigentes no mesmo dia")
    void substituicaoNaoDeixaSobreposicao() {
        // É a invariante que impede a recuperação de devolver duas concentrações diferentes para o mesmo
        // produto no mesmo dia, sem meio de escolher qual vale.
        var previous = Effectivity.from(MARCO).endedBefore(MAIO);

        assertThat(previous.to()).isEqualTo(MAIO.minusDays(1));
        assertThat(previous.coversDate(MAIO.minusDays(1))).isTrue();
        assertThat(previous.coversDate(MAIO)).isFalse();
    }

    @Test
    @DisplayName("o documento substituído continua respondendo sobre o período em que valeu")
    void substituidoAindaRespondeSobreOPassado() {
        var previous = Effectivity.from(MARCO).endedBefore(MAIO);

        // Esta é a pergunta que a história precisa saber responder: o que valia quando o lote foi feito.
        assertThat(previous.coversDate(MARCO)).isTrue();
        assertThat(previous.coversDate(ABRIL)).isTrue();
    }

    @Test
    @DisplayName("substituto que começa no mesmo dia reduz o anterior a um dia, sem intervalo impossível")
    void substitutoNoMesmoDia() {
        // Acontece quando se corrige uma indexação no próprio dia. Encolher para trás do início produziria
        // um intervalo invertido, e o construtor recusaria — o documento ficaria sem vigência nenhuma.
        var previous = Effectivity.from(MAIO).endedBefore(MAIO);

        assertThat(previous.from()).isEqualTo(MAIO);
        assertThat(previous.to()).isEqualTo(MAIO);
        assertThat(previous.coversDate(MAIO)).isTrue();
    }

    @Test
    @DisplayName("fim antes do início não existe")
    void fimAntesDoInicioEhRecusado() {
        assertThatThrownBy(() -> new Effectivity(MAIO, MARCO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem início não há vigência")
    void inicioEhObrigatorio() {
        assertThatThrownBy(() -> new Effectivity(null, MAIO)).isInstanceOf(NullPointerException.class);
    }
}
