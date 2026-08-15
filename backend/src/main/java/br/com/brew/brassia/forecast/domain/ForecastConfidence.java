package br.com.brew.brassia.forecast.domain;

/**
 * Quanto se pode apoiar numa previsão (FCST-001).
 *
 * <p>Mesmo espírito do {@code Confidence} do gêmeo digital, e pela mesma razão: uma faixa estreita
 * calculada sobre três meses parece confiável e não é — a estreiteza vem de os três terem dado parecido,
 * não de haver evidência suficiente.
 *
 * <p>Aqui há um agravante que a estimativa de brassagem não tem: <strong>demanda tem sazonalidade</strong>.
 * Três meses de verão não dizem nada sobre o inverno, e uma previsão que não avisa isso convida a
 * cervejaria a produzir para uma demanda que não vem.
 */
public enum ForecastConfidence {

    /**
     * Histórico curto demais. <strong>Não há previsão</strong> — não é um número incerto, é a ausência
     * dele.
     *
     * <p>Devolver um número aqui seria o pior resultado possível: ele viraria plano de produção, e a
     * cerveja que não vender vai vencer na prateleira.
     */
    INSUFFICIENT,

    /**
     * Histórico curto. A previsão existe e <strong>não deve virar ordem de produção sozinha</strong>.
     *
     * <p>É informação para olhar junto com quem conhece o mercado da casa, e não para alimentar
     * planejamento automático.
     */
    LOW,

    /** Histórico razoável. Já diz alguma coisa; ainda se mexe quando chega mês novo. */
    MODERATE,

    /**
     * Histórico de um ciclo anual ou mais.
     *
     * <p>Só a partir daqui a sazonalidade aparece no dado, em vez de ser adivinhada.
     */
    HIGH
}
