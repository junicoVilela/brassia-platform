package br.com.brew.brassia.digitaltwin.domain;

/**
 * Quanto se pode apoiar numa estimativa (DTW-001).
 *
 * <p><strong>Existe porque uma faixa numérica não basta.</strong> Um intervalo estreito calculado sobre
 * três brassagens parece confiável e não é — a estreiteza vem de as três terem dado parecido, não de haver
 * evidência suficiente. O rótulo é o que impede a leitura errada, e é ele que a tela mostra ao lado do
 * número.
 */
public enum Confidence {

    /** Menos de duas observações. Não há estimativa — não é um número baixo, é a ausência dele. */
    INSUFFICIENT,

    /**
     * Duas a quatro observações.
     *
     * <p>A estimativa existe e <strong>não deve guiar decisão sozinha</strong>. É informação para olhar
     * junto com quem conhece o equipamento, não para alimentar um planejamento automático.
     */
    LOW,

    /** Cinco a nove. Já diz alguma coisa; ainda se mexe quando chega brassagem nova. */
    MODERATE,

    /** Dez ou mais. A média para de oscilar a cada observação. */
    HIGH
}
