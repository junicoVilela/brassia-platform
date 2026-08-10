package br.com.brew.brassia.experiment.domain;

/**
 * O que esta conclusão <em>não</em> pode afirmar (EXP-001).
 *
 * <p><strong>São derivadas do desenho do experimento, não escritas por quem conclui.</strong> A diferença
 * importa: limitação que depende de alguém lembrar de escrevê-la é limitação que some justamente quando o
 * resultado agrada. Um lote dividido tem restrições que existem pela forma como foi feito — e o sistema as
 * conhece, porque conhece o plano.
 */
public enum Limitation {

    /**
     * Um par de lotes é n=1.
     *
     * <p>Sempre presente num lote dividido, e é a limitação mais esquecida: a diferença observada entre
     * dois lotes inclui a variação normal do processo, que existiria mesmo sem mudar nada. Sem repetição
     * não há como separar o efeito do acaso.
     */
    SINGLE_PAIR("Um único par controle/variante (n=1): a diferença observada inclui a variação normal do "
            + "processo, que existiria mesmo sem mudar nada."),

    /**
     * Sensorial não cego.
     *
     * <p>Quem sabe qual copo é a variante encontra o que espera encontrar. Não é má-fé: é como a
     * percepção funciona, e é o motivo de o resultado sensorial não cego não sustentar sozinho a decisão.
     */
    SENSORY_NOT_BLIND("Avaliação sensorial não cega: quem sabe qual amostra é a variante tende a "
            + "encontrar o efeito que espera."),

    /** Sem sensorial: o efeito percebido na bebida não foi avaliado, só o que os instrumentos medem. */
    NO_SENSORY("Sem avaliação sensorial: só o que os instrumentos medem foi comparado."),

    /**
     * Nenhuma grandeza planejada.
     *
     * <p>Sem medição definida antes, a comparação vira escolha do que olhar depois — e sempre há alguma
     * grandeza em que a variante ganha.
     */
    NO_PLANNED_MEASUREMENT("Nenhuma grandeza foi definida antes do experimento: a comparação fica sujeita "
            + "a escolher depois o número que favorece a conclusão."),

    /**
     * Um só ponto de medição.
     *
     * <p>Uma grandeza medida uma vez em cada lado não distingue efeito de instante da coleta.
     */
    SINGLE_METRIC("Uma única grandeza comparada: um efeito lateral em outra propriedade passaria "
            + "despercebido.");

    private final String description;

    Limitation(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
